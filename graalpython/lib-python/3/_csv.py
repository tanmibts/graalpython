# coding=utf-8
# Copyright (c) 2017, Oracle and/or its affiliates.
# Copyright (c) 2017, The PyPy Project
#
#     The MIT License
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included
# in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
# OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.

"""CSV parsing and writing.

This module provides classes that assist in the reading and writing
of Comma Separated Value (CSV) files, and implements the interface
described by PEP 305.  Although many CSV files are simple to parse,
the format is not formally defined by a stable specification and
is subtle enough that parsing lines of a CSV file with something
like line.split(\",\") is bound to fail.  The module supports three
basic APIs: reading, writing, and registration of dialects.


DIALECT REGISTRATION:

Readers and writers support a dialect argument, which is a convenient
handle on a group of settings.  When the dialect argument is a string,
it identifies one of the dialects previously registered with the module.
If it is a class or instance, the attributes of the argument are used as
the settings for the reader or writer:

    class excel:
        delimiter = ','
        quotechar = '\"'
        escapechar = None
        doublequote = True
        skipinitialspace = False
        lineterminator = '\\r\\n'
        quoting = QUOTE_MINIMAL

SETTINGS:

    * quotechar - specifies a one-character string to use as the 
        quoting character.  It defaults to '\"'.
    * delimiter - specifies a one-character string to use as the 
        field separator.  It defaults to ','.
    * skipinitialspace - specifies how to interpret whitespace which
        immediately follows a delimiter.  It defaults to False, which
        means that whitespace immediately following a delimiter is part
        of the following field.
    * lineterminator -  specifies the character sequence which should 
        terminate rows.
    * quoting - controls when quotes should be generated by the writer.
        It can take on any of the following module constants:

        csv.QUOTE_MINIMAL means only when required, for example, when a
            field contains either the quotechar or the delimiter
        csv.QUOTE_ALL means that quotes are always placed around fields.
        csv.QUOTE_NONNUMERIC means that quotes are always placed around
            fields which do not parse as integers or floating point
            numbers.
        csv.QUOTE_NONE means that quotes are never placed around fields.
    * escapechar - specifies a one-character string used to escape 
        the delimiter when quoting is set to QUOTE_NONE.
    * doublequote - controls the handling of quotes inside fields.  When
        True, two consecutive quotes are interpreted as one during read,
        and when writing, each quote character embedded in the data is
        written as two quotes.
"""

__version__ = "1.0"

QUOTE_MINIMAL, QUOTE_ALL, QUOTE_NONNUMERIC, QUOTE_NONE = range(4)
_dialects = {}
_field_limit = 128 * 1024 # max parsed field size

class Error(Exception):
    pass

class Dialect(object):
    """CSV dialect

    The Dialect type records CSV parsing and generation options."""

    __slots__ = ["_delimiter", "_doublequote", "_escapechar",
                 "_lineterminator", "_quotechar", "_quoting",
                 "_skipinitialspace", "_strict"]

    def __new__(cls, dialect = None, **kwargs):

        for name in kwargs:
            if '_' + name not in Dialect.__slots__:
                raise TypeError("unexpected keyword argument '%s'" %
                                (name,))

        if dialect is not None:
            if isinstance(dialect, str):
                dialect = get_dialect(dialect)
        
            # Can we reuse this instance?
            if (isinstance(dialect, Dialect)
                and all(value is None for value in kwargs.values())):
                return dialect

        self = object.__new__(cls)


        # equivalent of '_csv.c: _set_char'
        def set_char(x):
            if x is not None:
                if not isinstance(x, str):
                    raise TypeError('"%s" must be string, not %s' % (name, type(x).__name__))
                n = len(x)
                if n > 1:
                    raise TypeError('"%s" must be a 1-character string' % (name,))
                if n > 0:
                    return x
            return ""
        def set_str(x):
            if isinstance(x, str):
                return x
            raise TypeError('"%s" must be a string' % (name))
        def set_quoting(x):
            if x in range(4):
                return x
            raise TypeError("bad 'quoting' value")
        
        attributes = {"delimiter": (',', set_char),
                      "doublequote": (True, bool),
                      "escapechar": (None, set_char),
                      "lineterminator": ("\r\n", set_str),
                      "quotechar": ('"', set_char),
                      "quoting": (QUOTE_MINIMAL, set_quoting),
                      "skipinitialspace": (False, bool),
                      "strict": (False, bool),
                      }

        # Copy attributes
        notset = object()
        for name in Dialect.__slots__:
            name = name[1:]
            value = notset
            if name in kwargs:
                value = kwargs[name]
            elif dialect is not None:
                value = getattr(dialect, name, notset)

            # mapping by name: (default, converter)
            if value is notset:
                value = attributes[name][0]
                if name == 'quoting' and not self.quotechar:
                    value = QUOTE_NONE
            else:
                converter = attributes[name][1]
                if converter:
                    value = converter(value)

            setattr(self, '_' + name, value)

        if not self.delimiter:
            raise TypeError('"delimiter" must be a 1-character string')

        if self.quoting != QUOTE_NONE and not self.quotechar:
            raise TypeError("quotechar must be set if quoting enabled")

        if not self.lineterminator:
            raise TypeError("lineterminator must be set")

        return self

    delimiter        = property(lambda self: self._delimiter)
    doublequote      = property(lambda self: self._doublequote)
    escapechar       = property(lambda self: self._escapechar)
    lineterminator   = property(lambda self: self._lineterminator)
    quotechar        = property(lambda self: self._quotechar)
    quoting          = property(lambda self: self._quoting)
    skipinitialspace = property(lambda self: self._skipinitialspace)
    strict           = property(lambda self: self._strict)


def _call_dialect(dialect_inst, kwargs):
    return Dialect(dialect_inst, **kwargs)

def register_dialect(name, dialect=None, **kwargs):
    """Create a mapping from a string name to a dialect class.
    dialect = csv.register_dialect(name, dialect)"""
    if not isinstance(name, str):
        raise TypeError("dialect name must be a string or unicode")

    dialect = _call_dialect(dialect, kwargs)
    _dialects[name] = dialect

def unregister_dialect(name):
    """Delete the name/dialect mapping associated with a string name.\n
    csv.unregister_dialect(name)"""
    try:
        del _dialects[name]
    except KeyError:
        raise Error("unknown dialect")

def get_dialect(name):
    """Return the dialect instance associated with name.
    dialect = csv.get_dialect(name)"""
    try:
        return _dialects[name]
    except KeyError:
        raise Error("unknown dialect")

def list_dialects():
    """Return a list of all know dialect names
    names = csv.list_dialects()"""
    return list(_dialects)

class Reader(object):
    """CSV reader

    Reader objects are responsible for reading and parsing tabular data
    in CSV format."""
    

    (START_RECORD, START_FIELD, ESCAPED_CHAR, IN_FIELD,
     IN_QUOTED_FIELD, ESCAPE_IN_QUOTED_FIELD, QUOTE_IN_QUOTED_FIELD,
     EAT_CRNL) = range(8)
    
    def __init__(self, iterator, dialect=None, **kwargs):
        self.dialect = _call_dialect(dialect, kwargs)
        self.input_iter = iter(iterator)
        self.line_num = 0

        self._parse_reset()

    def _parse_reset(self):
        self.field = ''
        self.fields = []
        self.state = self.START_RECORD
        self.numeric_field = False

    def __iter__(self):
        return self

    def __next__(self):
        self._parse_reset()
        while True:
            try:
                line = next(self.input_iter)
            except StopIteration:
                # End of input OR exception
                if len(self.field) > 0:
                    raise Error("newline inside string")
                raise

            self.line_num += 1
            if isinstance(line, str) and '\0' in line or isinstance(line, bytes) and line.index(0) >=0:
                raise Error("line contains NULL byte")
            pos = 0
            while pos < len(line):
                pos = self._parse_process_char(line, pos)
            self._parse_eol()

            if self.state == self.START_RECORD:
                break

        fields = self.fields
        self.fields = []
        return fields
            
    def _parse_process_char(self, line, pos):
        c = line[pos]
        if self.state == self.IN_FIELD:
            # in unquoted field
            pos2 = pos
            while True:
                if c in '\n\r':
                    # end of line - return [fields]
                    if pos2 > pos:
                        self._parse_add_char(line[pos:pos2])
                        pos = pos2
                    self._parse_save_field()
                    self.state = self.EAT_CRNL
                elif c == self.dialect.escapechar:
                    # possible escaped character
                    pos2 -= 1
                    self.state = self.ESCAPED_CHAR
                elif c == self.dialect.delimiter:
                    # save field - wait for new field
                    if pos2 > pos:
                        self._parse_add_char(line[pos:pos2])
                        pos = pos2
                    self._parse_save_field()
                    self.state = self.START_FIELD
                else:
                    # normal character - save in field
                    pos2 += 1
                    if pos2 < len(line):
                        c = line[pos2]
                        continue
                break
            if pos2 > pos:
                self._parse_add_char(line[pos:pos2])
                pos = pos2 - 1

        elif self.state == self.START_RECORD:
            if c in '\n\r':
                self.state = self.EAT_CRNL
            else:
                self.state = self.START_FIELD
                # restart process
                self._parse_process_char(line, pos)

        elif self.state == self.START_FIELD:
            if c in '\n\r':
                # save empty field - return [fields]
                self._parse_save_field()
                self.state = self.EAT_CRNL
            elif (c == self.dialect.quotechar
                  and self.dialect.quoting != QUOTE_NONE):
                # start quoted field
                self.state = self.IN_QUOTED_FIELD
            elif c == self.dialect.escapechar:
                # possible escaped character
                self.state = self.ESCAPED_CHAR
            elif c == ' ' and self.dialect.skipinitialspace:
                # ignore space at start of field
                pass
            elif c == self.dialect.delimiter:
                # save empty field
                self._parse_save_field()
            else:
                # begin new unquoted field
                if self.dialect.quoting == QUOTE_NONNUMERIC:
                    self.numeric_field = True
                self._parse_add_char(c)
                self.state = self.IN_FIELD
        
        elif self.state == self.ESCAPED_CHAR:
            self._parse_add_char(c)
            self.state = self.IN_FIELD
        
        elif self.state == self.IN_QUOTED_FIELD:
            if c == self.dialect.escapechar:
                # possible escape character
                self.state = self.ESCAPE_IN_QUOTED_FIELD
            elif (c == self.dialect.quotechar
                  and self.dialect.quoting != QUOTE_NONE):
                if self.dialect.doublequote:
                    # doublequote; " represented by ""
                    self.state = self.QUOTE_IN_QUOTED_FIELD
                else:
                    #end of quote part of field
                    self.state = self.IN_FIELD
            else:
                # normal character - save in field
                self._parse_add_char(c)
                
        elif self.state == self.ESCAPE_IN_QUOTED_FIELD:
            self._parse_add_char(c)
            self.state = self.IN_QUOTED_FIELD
                
        elif self.state == self.QUOTE_IN_QUOTED_FIELD:
            # doublequote - seen a quote in a quoted field
            if (c == self.dialect.quotechar
                and self.dialect.quoting != QUOTE_NONE):
                # save "" as "
                self._parse_add_char(c)
                self.state = self.IN_QUOTED_FIELD
            elif c == self.dialect.delimiter:
                # save field - wait for new field
                self._parse_save_field()
                self.state = self.START_FIELD
            elif c in '\r\n':
                # end of line - return [fields]
                self._parse_save_field()
                self.state = self.EAT_CRNL
            elif not self.dialect.strict:
                self._parse_add_char(c)
                self.state = self.IN_FIELD
            else:
                raise Error("'%c' expected after '%c'" %
                            (self.dialect.delimiter, self.dialect.quotechar))

        elif self.state == self.EAT_CRNL:
            if c not in '\r\n':
                raise Error("new-line character seen in unquoted field - "
                            "do you need to open the file "
                            "in universal-newline mode?")

        else:
            raise RuntimeError("unknown state: %r" % (self.state,))

        return pos + 1

    def _parse_eol(self):
        if self.state == self.EAT_CRNL:
            self.state = self.START_RECORD
        elif self.state == self.START_RECORD:
            # empty line - return []
            pass
        elif self.state == self.IN_FIELD:
            # in unquoted field
            # end of line - return [fields]
            self._parse_save_field()
            self.state = self.START_RECORD
        elif self.state == self.START_FIELD:
            # save empty field - return [fields]
            self._parse_save_field()
            self.state = self.START_RECORD
        elif self.state == self.ESCAPED_CHAR:
            self._parse_add_char('\n')
            self.state = self.IN_FIELD
        elif self.state == self.IN_QUOTED_FIELD:
            pass
        elif self.state == self.ESCAPE_IN_QUOTED_FIELD:
            self._parse_add_char('\n')
            self.state = self.IN_QUOTED_FIELD
        elif self.state == self.QUOTE_IN_QUOTED_FIELD:
            # end of line - return [fields]
            self._parse_save_field()
            self.state = self.START_RECORD
        else:
            raise RuntimeError("unknown state: %r" % (self.state,))

    def _parse_save_field(self):
        field, self.field = self.field, ''
        if self.numeric_field:
            self.numeric_field = False
            field = float(field)
        self.fields.append(field)

    def _parse_add_char(self, c):
        if len(self.field) + len(c) > _field_limit:
            raise Error("field larger than field limit (%d)" % (_field_limit))
        self.field += c
        

class Writer(object):
    """CSV writer

    Writer objects are responsible for generating tabular data
    in CSV format from sequence input."""

    def __init__(self, file, dialect=None, **kwargs):
        if not (hasattr(file, 'write') and callable(file.write)):
            raise TypeError("argument 1 must have a 'write' method")
        self.writeline = file.write
        self.dialect = _call_dialect(dialect, kwargs)

    def _join_reset(self):
        self.rec = []
        self.num_fields = 0

    def _join_append(self, field, quoted, quote_empty):
        dialect = self.dialect
        # If this is not the first field we need a field separator
        if self.num_fields > 0:
            self.rec.append(dialect.delimiter)

        if dialect.quoting == QUOTE_NONE:
            need_escape = tuple(dialect.lineterminator) + (
                dialect.escapechar,  # escapechar always first
                dialect.delimiter, dialect.quotechar)
                
        else:
            for c in tuple(dialect.lineterminator) + (
                dialect.delimiter, dialect.escapechar):
                if c and c in field:
                    quoted = True

            need_escape = ()
            if dialect.quotechar in field:
                if dialect.doublequote:
                    field = field.replace(dialect.quotechar,
                                          dialect.quotechar * 2)
                    quoted = True
                else:
                    need_escape = (dialect.quotechar,)


        for c in need_escape:
            if c and c in field:
                if not dialect.escapechar:
                    raise Error("need to escape, but no escapechar set")
                field = field.replace(c, dialect.escapechar + c)

        # If field is empty check if it needs to be quoted
        if field == '' and quote_empty:
            if dialect.quoting == QUOTE_NONE:
                raise Error("single empty field record must be quoted")
            quoted = 1

        if quoted:
            field = dialect.quotechar + field + dialect.quotechar

        self.rec.append(field)
        self.num_fields += 1



    def writerow(self, row):
        if row is None:
            raise Error
        
        dialect = self.dialect

        # join all fields in internal buffer
        self._join_reset()
        
        fields = []
        for field in row:
            fields.append(field)
            
        rowlen = len(fields)    
        for field in fields:
            quoted = False
            if dialect.quoting == QUOTE_NONNUMERIC:
                try:
                    float(field)
                except:
                    quoted = True
                # This changed since 2.5:
                # quoted = not isinstance(field, (int, long, float))
            elif dialect.quoting == QUOTE_ALL:
                quoted = True

            if field is None:
                if dialect.quoting == QUOTE_NONE:
                    raise Error
                value = ""
            elif isinstance(field, float):
                value = repr(field)
            else:
                value = str(field)
            self._join_append(value, quoted, rowlen == 1)

        # add line terminator
        self.rec.append(dialect.lineterminator)

        self.writeline(''.join(self.rec))

    def writerows(self, rows):
        for row in rows:
            self.writerow(row)

def reader(*args, **kwargs):
    """
    csv_reader = reader(iterable [, dialect='excel']
                       [optional keyword args])
    for row in csv_reader:
        process(row)

    The "iterable" argument can be any object that returns a line
    of input for each iteration, such as a file object or a list.  The
    optional \"dialect\" parameter is discussed below.  The function
    also accepts optional keyword arguments which override settings
    provided by the dialect.

    The returned object is an iterator.  Each iteration returns a row
    of the CSV file (which can span multiple input lines)"""
    
    return Reader(*args, **kwargs)

def writer(*args, **kwargs):
    """
    csv_writer = csv.writer(fileobj [, dialect='excel']
                            [optional keyword args])
    for row in sequence:
        csv_writer.writerow(row)

    [or]

    csv_writer = csv.writer(fileobj [, dialect='excel']
                            [optional keyword args])
    csv_writer.writerows(rows)

    The \"fileobj\" argument can be any object that supports the file API."""
    return Writer(*args, **kwargs)


undefined = object()
def field_size_limit(limit=undefined):
    """Sets an upper limit on parsed fields.
    csv.field_size_limit([limit])

    Returns old limit. If limit is not given, no new limit is set and
    the old limit is returned"""

    global _field_limit
    old_limit = _field_limit
    
    if limit is not undefined:
        if not isinstance(limit, int):
            raise TypeError("int expected, got %s" %
                            (limit.__class__.__name__,))
        _field_limit = limit

    return old_limit
