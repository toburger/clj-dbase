# clj-dbase

A small Clojure library designed to parse [dBASE](https://de.wikipedia.org/wiki/DBASE)
files. At the moment only Version 3 is supported. 


dBASE file are some sort of legacy table format used by some telecommunication
providers to provide billing informations. It consists out of a binary
header which starts with some metadata and afterwards the field descriptions.
The main part are records in almost plain text. 
More informations can be found [here](http://www.independent-software.com/dbase-dbf-dbt-file-format.html) and [here](https://github.com/henck/dBASE.NET/tree/master/dBASE.NET).

## Usage




## License

A small clojure library for parsing dBASE files.
Copyright © 2020 Henrik Jürges <juerges.henrik@gmail.com>

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