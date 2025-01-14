(ns clj-dbase.core
  "A small library to parse dbase files.
   Copyright (C) 2020  Henrik Jürges <juerges.henrik@gmail.com>

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <https://www.gnu.org/licenses/>.

   This module provides the utility to parse the dbase dbf table format into

   The expected file format version is dBase v3.
   A dbf file starts with a variable header defining some metainformations
   and the field types and names. The remaining file is the record content.

   ### Usage

   The main entry point is the `(parse-dbase-file input-file)` function.

   ```
   (def dbase-data (parse-dbase-file (clojure.java.io/file input-file)))
   ```

   The returned data is a nested map of
   ```
   {:header <meta informations>
    :fields <record field definitions>
    :records <the actual table data>}
   ```

   Additionally, the field definition can be attatched to the record data.

   ```
   (-> (java.io.file. input-file)
       (parse-dbase-file)
       (field-definition->records))
   ```
   This results in the field name is also attached to the individual record
   for easier handling and post-processing."
  (:refer-clojure :exclude [abs])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(defn abs [n] (max n (- n)))

(defn read-bytes
  "The main byte reading function.

  Read from `stream` n bytes and optional skip `m` bytes."
  ([stream n]
   (read-bytes stream n 0))
  ([stream n m]
   (let [bytes (byte-array n)]
     (.read stream bytes 0 n)
     (.skip stream m)
     bytes)))

(defn read-byte
  "Read only one byte and optionaly skip `n` bytes"
  ([stream] (read-byte stream 0))
  ([stream n] (first (read-bytes stream 1 n))))

(defn bytes->int
  "Convert some bytes to an integer or uint32."
  [bytes]
  (assert (= 4 (count bytes)))
  (let [wrapper (java.nio.ByteBuffer/wrap bytes)
        wrapper (.order wrapper java.nio.ByteOrder/LITTLE_ENDIAN)]
    (.getInt wrapper)))

(defn bytes->short
  "Converts some bytes to a short or uint16."
  [bytes]
  (assert (= 2 (count bytes)))
  (let [wrapper (java.nio.ByteBuffer/wrap bytes)
        wrapper (.order wrapper java.nio.ByteOrder/LITTLE_ENDIAN)]
    (.getShort wrapper)))

(defn next-byte?
  "Test the next byte from the input `stream` with `pred`.

  The stream is resetted afterwards."
  [stream pred]
  (.mark stream 33)
  (let [b (.read stream)]
    (.reset stream)
    (pred b)))

;;;; Header
;;; Metadata
(defn parse-last-update-time
  "Read the date from the input `stream`."
  [stream]
  (java.util.Date.
   (read-byte stream)   ; year
   (read-byte stream)   ; month
   (read-byte stream))) ; day

(defn parse-dbase-metadata
  "Parse the dbase header file from the input `stream`."
  [stream]
  {:dbf-version (read-byte stream)
   :last-update (parse-last-update-time stream)
   :num-records (bytes->int (read-bytes stream 4))
   :header-length (bytes->short (read-bytes stream 2))
                                        ; the last 20 header bytes are reserved
   :record-length (bytes->short (read-bytes stream 2 20))})

;;; Fieldheader
(defn parse-field-name
  "Parse the field name from the input `stream`."
  [stream]
  (let [bytes (read-bytes stream 11)]
    (clojure.string/join (filter (complement #{\ }) (map char bytes)))))

(defn parse-field
  "Parse a record field definition from the input `stream`.

  The skipped bytes are reserved for internal field adress in memory
  or other file format version then 3."
  [stream]
  {:name (parse-field-name stream)
   :type (char (read-byte stream 4))
   :length (read-byte stream)
   :precision (read-byte stream 2)
   :work-area-id (read-byte stream 2)
   :flags (read-byte stream 8)})

(defn parse-dbase-fields
  "Parse all fields in the header.

  The parsing stops at the first carriage return, ascii code 13."
  [stream]
  (loop [in stream
         out []]
    (if (next-byte? stream (partial = 13))
      out
      (recur stream (conj out (parse-field stream))))))

;;;; Record
;;; Single record field
(defn type-field
  "Convert the bytes to the `field-type` mentioned in the field header."
  [field-type bytes]
  (condp = field-type
    \C (s/join (map char (map abs bytes)))
    \N (apply str (map char (map abs bytes)))
    \M (apply str (map char (map abs bytes)))
    bytes))

(defn parse-record-field
  "Converts the field into some appropriate clojure type.

  The raw `bytes` of the record field.
  The header description of the `field`."
  [bytes field]
  (let [sub (take (:length field) (drop (:offset field) bytes))]
    (s/trim (type-field (:type field) sub))))

(defn parse-dbase-records
  "Parse the num of records defined in `header` and with the defined `fields` order."
  [file fields header]
  (with-open [stream (io/input-stream file)]
    (.skip stream (:header-length header))
    (loop [stream stream
           n (:num-records header)
           records []]
      ;;(println n)
      (if (< n 1)
        records
        (let [bytes (read-bytes stream (:record-length header))]
          (recur stream (- n 1)
                 (conj records
                       (map #(parse-record-field (rest bytes) %) fields))))))))

(defn calc-offset
  "Calculate the `offset` after which the first record starts."
  [fields offset fs]
  (if (empty? fields)
    fs
    (let [f (assoc (first fields) :offset offset)]
      (recur (rest fields) (+ offset (:length f)) (conj fs f)))))

(defn parse-dbase-header
  "Take a dbase `file` of version 3 and parse the metadata and field information."
  [file]
  (with-open [stream (io/input-stream file)]
    {:header (parse-dbase-metadata stream)
     :fields (parse-dbase-fields stream)}))

(defn parse-dbase-file
  "Take a dbase `file` and parse the file returnin a clojure map.

  The consists of a `:header` part, the list of `:fields` definitions
  and the parsed `:records` data.
  At the moment only text and numbers are recognized as types."
  [^java.io.File file]
  (let [{:keys [header fields]} (parse-dbase-header file)
        fields-length (calc-offset fields 0 [])
        records (parse-dbase-records file fields-length header)]
    {:header header
     :fields fields
     :records records}))


(defn enrich-record
  "Add the field definitions as keywords to some record."
  [fields record]
  (loop [in record
         f fields
         out {}]
    (if (empty? in)
      out
      (recur (rest in) (rest f)
             (merge out {(keyword (:name (first f))) (first in)})))))

(defn field-definition->records
  "Enrich the records with the field names as keywords."
  [dbase-data]
  (let [{:keys [fields records]} dbase-data
        records (map (partial enrich-record fields) records)]
    (assoc dbase-data :records records)))
