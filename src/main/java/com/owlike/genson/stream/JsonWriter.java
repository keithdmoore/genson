package com.owlike.genson.stream;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class JsonWriter implements ObjectWriter {
    /*
     * TODO try to do something different and faster, optimize writeValue(String)
     */
    private final static char[][] REPLACEMENT_CHARS;
    private final static char[][] HTML_SAFE_REPLACEMENT_CHARS;
    static {
        REPLACEMENT_CHARS = new char[128][];
        for (int i = 0; i <= 0x1f; i++) {
            REPLACEMENT_CHARS[i] = String.format("\\u%04x", (int) i).toCharArray();
        }
        REPLACEMENT_CHARS['"'] = "\\\"".toCharArray();
        REPLACEMENT_CHARS['\\'] = "\\\\".toCharArray();
        REPLACEMENT_CHARS['\t'] = "\\t".toCharArray();
        REPLACEMENT_CHARS['\b'] = "\\b".toCharArray();
        REPLACEMENT_CHARS['\n'] = "\\n".toCharArray();
        REPLACEMENT_CHARS['\r'] = "\\r".toCharArray();
        REPLACEMENT_CHARS['\f'] = "\\f".toCharArray();
        HTML_SAFE_REPLACEMENT_CHARS = REPLACEMENT_CHARS.clone();
        HTML_SAFE_REPLACEMENT_CHARS['\''] = "\\u0027".toCharArray();
        HTML_SAFE_REPLACEMENT_CHARS['<'] = "\\u003c".toCharArray();
        HTML_SAFE_REPLACEMENT_CHARS['>'] = "\\u003e".toCharArray();
        HTML_SAFE_REPLACEMENT_CHARS['&'] = "\\u0026".toCharArray();
        HTML_SAFE_REPLACEMENT_CHARS['='] = "\\u003d".toCharArray();
    }

    private final static char[] _INT_TO_CHARARRAY = new char[10];
    static {
        for (int i = 0; i < 10; i++) {
            _INT_TO_CHARARRAY[i] = (char) (i + 48);
        }
    }
    private final static char[] NULL_VALUE = { 'n', 'u', 'l', 'l' };
    private final static char[] TRUE_VALUE = { 't', 'r', 'u', 'e' };
    private final static char[] FALSE_VALUE = { 'f', 'a', 'l', 's', 'e' };
    // seems to work well, but maybe a smaller value would be better?
    private final static int _LIMIT_WRITE_TO_BUFFER = 64;

    private final boolean htmlSafe;
    private final boolean skipNull;

    private final Writer writer;
    final Deque<JsonType> _ctx = new ArrayDeque<JsonType>(10);
    private boolean _hasPrevious;
    private String _name;
    private final boolean indentation;
    private final static char[] _indentation = new char[] { ' ', ' ' };

    private final char[] _buffer = new char[1024];
    private final int _bufferSize = _buffer.length;
    private int _len = 0;

    List<MetadataPair> _metadata = new ArrayList<MetadataPair>();

    private class MetadataPair {
        final String name;
        final String value;

        public MetadataPair(String name, String value) {
            super();
            this.name = name;
            this.value = value;
        }
    }

    public JsonWriter(Writer writer) {
        this(writer, false, false, false);
    }

    public JsonWriter(Writer writer, final boolean skipNull, final boolean htmlSafe,
            boolean indentation) {
        this.writer = writer;
        this.skipNull = skipNull;
        this.htmlSafe = htmlSafe;
        this.indentation = indentation;
        _ctx.push(JsonType.EMPTY);
    }

    public void close() {
        flush();
        try {
            writer.close();
        } catch (IOException e) {
            throw new JsonStreamException(e);
        }
    }

    public void flush() {
        flushBuffer();
        try {
            writer.flush();
        } catch (IOException e) {
            throw new JsonStreamException(e);
        }
    }

    public JsonWriter beginArray() {
        clearMetadata();
        if (_ctx.peek() == JsonType.OBJECT && _name == null)
            throw new JsonStreamException(
                    "Englobing scope is OBJECT before begining a new value call writeName.");
        return begin(JsonType.ARRAY, '[');
    }

    public JsonWriter beginObject() {
        if (_ctx.peek() == JsonType.METADATA) {
            _ctx.pop();
            begin(JsonType.OBJECT, '{');
            for (MetadataPair pair : _metadata) {
                writeName('@' + pair.name).writeInternalString(pair.value);
            }
        } else begin(JsonType.OBJECT, '{');
        return this;
    }

    protected final JsonWriter begin(final JsonType jsonType, final char token) {
        beforeValue();
        _ctx.push(jsonType);
        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = token;
        _hasPrevious = false;
        return this;
    }

    public JsonWriter endArray() {
        return end(JsonType.ARRAY, ']');
    }

    public JsonWriter endObject() {
        return end(JsonType.OBJECT, '}');
    }

    private final JsonWriter end(final JsonType jsonType, final char token) {
        JsonType jt = _ctx.pop();
        if (jt != jsonType)
            throw new JsonStreamException("Expect type " + jsonType.name() + " but was written "
                    + jt.name() + ", you must call the adequate beginXXX method before endXXX.");

        if (indentation) {
            _buffer[_len++] = '\n';
            for (int i = 0; i < _ctx.size() - 1; i++)
                writeToBuffer(_indentation, 0, 2);
        }

        if ((_len + 1) >= _bufferSize) flushBuffer();

        _buffer[_len++] = token;
        _hasPrevious = true;
        return this;
    }

    private final JsonWriter beforeValue() {
        /*
         * for the moment this does not work, however I should add some "state verifications" for
         * metadata feature... otherwise it may be difficult to use and debug it...
         */
        // if (_ctx.peek() == JsonType.METADATA)
        // throw new IllegalStateException(
        // "You have written metadata to the writer but did not call beginObject after it."
        // + "Metadata is not allowed in array or for literal values.");

        final JsonType enclosingType = _ctx.peek();
        if (enclosingType == JsonType.ARRAY) {
            if (_name != null) throw newIllegalKeyValuePairInJsonArray(_name);
            if (_hasPrevious) {
                if ((_len + 1) >= _bufferSize) flushBuffer();
                _buffer[_len++] = ',';
            }
            indent();
        } else if (_name != null) {
            final int l = _name.length();
            // hum I dont think there may be names with a length near to 1024... we flush only once
            if ((_len + 4 + l) >= _bufferSize) flushBuffer();
            if (_hasPrevious) _buffer[_len++] = ',';
            indent();
            if ((_len + 3 + l) >= _bufferSize) flushBuffer();
            _buffer[_len++] = '"';
            writeToBuffer(_name, 0, l);
            _buffer[_len++] = '"';
            _buffer[_len++] = ':';
            _name = null;
        } else if (enclosingType == JsonType.OBJECT) throw newIllegalSingleValueInJsonObject();

        return this;
    }

    private JsonStreamException newIllegalKeyValuePairInJsonArray(String name) {
        return JsonStreamException
                .niceTrace(new JsonStreamException(
                        "Tried to write key/value pair with key="
                                + name
                                + ", Json format does not allow key/value pairs inside arrays, only allowed for Json Objects."));
    }

    private JsonStreamException newIllegalSingleValueInJsonObject() {
        return JsonStreamException.niceTrace(new JsonStreamException(
                "Tried to write value with no key in a JsonObject, Json format does not allow "
                        + "values without keys in JsonObjects, authorized only for arrays."));
    }

    private final void clearMetadata() {
        if (_ctx.peek() == JsonType.METADATA) {
            _metadata.clear();
            _ctx.pop();
        }
    }

    protected void indent() {
        if (indentation) {
            if ((_len + 1) >= _bufferSize) flushBuffer();
            if (_ctx.peek() != JsonType.EMPTY) _buffer[_len++] = '\n';
            int len = _ctx.peek() == JsonType.METADATA ? _ctx.size() - 2 : _ctx.size() - 1;
            for (int i = 0; i < len; i++)
                writeToBuffer(_indentation, 0, 2);
        }
    }

    public JsonWriter writeName(final String name) {
        _name = name;
        return this;
    }

    public JsonWriter writeValue(int value) {
        clearMetadata();
        beforeValue();
        // ok so the buffer must always be bigger than the max length of a long
        if ((_len + 11) >= _bufferSize) flushBuffer();
        if (value < 0) {
            _buffer[_len++] = '-';
            writeInt(-((long) value));
        } else writeInt(value);
        _hasPrevious = true;
        return this;
    }

    public JsonWriter writeValue(final double value) {
        checkValidJsonDouble(value);
        clearMetadata();
        beforeValue();
        writeToBuffer(Double.toString(value), 0);
        _hasPrevious = true;
        return this;
    }

    public JsonWriter writeValue(long value) {
        clearMetadata();
        beforeValue();
        // ok so the buffer must always be bigger than the max length of a long
        if ((_len + 21) >= _bufferSize) flushBuffer();


        if (value < 0) {
            if (value != Long.MIN_VALUE) {
                _buffer[_len++] = '-';
                writeInt(-1 * value);
            } else writeToBuffer(Long.toString(value), 0);
        } else writeInt(value);

        _hasPrevious = true;
        return this;
    }

    public ObjectWriter writeValue(short value) {
        clearMetadata();
        beforeValue();
        // ok so the buffer must always be bigger than the max length of a short
        if ((_len + 5) >= _bufferSize) flushBuffer();
        if (value < 0) {
            _buffer[_len++] = '-';
            value *= -1;
        }
        writeInt(value);
        _hasPrevious = true;
        return this;
    }

    public ObjectWriter writeValue(float value) {
        checkValidJsonFloat(value);
        clearMetadata();
        beforeValue();
        writeToBuffer(Float.toString(value), 0);
        _hasPrevious = true;
        return this;
    }

    public JsonWriter writeValue(final boolean value) {
        clearMetadata();
        beforeValue();
        if (value) writeToBuffer(TRUE_VALUE, 0, 4);
        else writeToBuffer(FALSE_VALUE, 0, 5);
        _hasPrevious = true;
        return this;
    }

    protected final int writeInt(long value) {
        final int len = (int) Math.log10(value) + 1;
        if (value == 0) {
            _buffer[_len++] = '0';
            return 1;
        }

        int pos = _len + len - 1;
        long intPart;
        for (; value > 0;) {
            intPart = value / 10;
            _buffer[pos--] = _INT_TO_CHARARRAY[(int) (value - (intPart * 10))];
            value = intPart;
        }

        _len += len;
        return len;
    }

    public JsonWriter writeValue(final Number value) {
        checkValidJsonDouble(value);
        checkValidJsonFloat(value);
        clearMetadata();
        beforeValue();
        writeToBuffer(value.toString(), 0);
        _hasPrevious = true;
        return this;
    }

    public ObjectWriter writeBoolean(final Boolean value) {
        if (value == null) return writeNull();
        else return writeValue(value);
    }

    public ObjectWriter writeNumber(final Number value) {
        if (value == null) return writeNull();
        else return writeValue(value);
    }

    public ObjectWriter writeString(String value) {
        if (value == null) return writeNull();
        else return writeValue(value);
    }

    public ObjectWriter writeBytes(byte[] value) {
        if (value == null) return writeNull();
        else return writeValue(value);
    }

    private void checkValidJsonDouble(Number num) {
        if (num.equals(Double.NaN))
            throw new NumberFormatException("NaN is not a valid json number.");
        if (num.equals(Double.NEGATIVE_INFINITY) || num.equals(Double.POSITIVE_INFINITY))
            throw new NumberFormatException("Infinity is not a valid json number.");
    }

    private void checkValidJsonFloat(Number num) {
        if (num.equals(Float.NaN))
            throw new NumberFormatException("NaN is not a valid json number.");
        if (num.equals(Float.NEGATIVE_INFINITY) || num.equals(Float.POSITIVE_INFINITY))
            throw new NumberFormatException("Infinity is not a valid json number.");
    }

    public ObjectWriter writeValue(byte[] value) {
        clearMetadata();
        beforeValue();

        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = '"';
        final char[] charArray = Base64.encodeToChar(value, false);

        writeToBuffer(charArray, 0, charArray.length);

        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = '"';
        _hasPrevious = true;

        flush();
        return this;
    }

    public JsonWriter writeUnsafeValue(final String value) {
        clearMetadata();
        beforeValue();
        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = '"';
        writeToBuffer(value.toCharArray(), 0, value.length());
        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = '"';
        _hasPrevious = true;
        return this;
    }

    public JsonWriter writeValue(final String value) {
        clearMetadata();
        writeInternalString(value);
        return this;
    }

    private final void writeInternalString(final String value) {
        beforeValue();
        final char[][] replacements = htmlSafe ? HTML_SAFE_REPLACEMENT_CHARS : REPLACEMENT_CHARS;
        if ((_len + 1) >= _bufferSize) flushBuffer();
        _buffer[_len++] = '"';
        int last = 0;
        final int length = value.length();
        final char[] carray = value.toCharArray();
        for (int i = 0; i < length; i++) {
            char c = carray[i];
            char[] replacement;
            if (c < 128) {
                replacement = replacements[c];
                if (replacement == null) {
                    continue;
                }
            } else if (c == '\u2028') {
                replacement = "\\u2028".toCharArray();
            } else if (c == '\u2029') {
                replacement = "\\u2029".toCharArray();
            } else {
                continue;
            }
            if (last < i) {
                writeToBuffer(carray, last, i - last);
            }

            writeToBuffer(replacement, 0, replacement.length);
            last = i + 1;
        }
        if (last < length) {
            writeToBuffer(carray, last, length - last);
        }
        if ((_len + 1) >= _bufferSize) flushBuffer();

        _buffer[_len++] = '"';

        _hasPrevious = true;
    }

    public ObjectWriter writeNull() {
        if (skipNull) {
            _name = null;
        } else {
            beforeValue();
            writeToBuffer(NULL_VALUE, 0, 4);
            _hasPrevious = true;
        }
        return this;
    }

    public ObjectWriter beginNextObjectMetadata() {
        // this way we can use this method multiple times in different converters before calling beginObject
        if (_ctx.peek() != JsonType.METADATA) {
            _ctx.push(JsonType.METADATA);
            _metadata.clear();
        }
        return this;
    }

    public ObjectWriter writeMetadata(String name, String value) {
        if (_ctx.peek() == JsonType.METADATA) _metadata.add(new MetadataPair(name, value));
        else if (_ctx.peek() == JsonType.OBJECT) {
            writeName('@' + name);
            writeValue(value);
        }
        // else do nothing so we silently don't write metadata for literals and arrays
        return this;
    }

    public ObjectWriter writeBoolean(String name, Boolean value) {
        writeName(name);
        return writeBoolean(value);
    }

    public ObjectWriter writeNumber(String name, Number value) {
        writeName(name);
        return writeNumber(value);
    }

    public ObjectWriter writeString(String name, String value) {
        writeName(name);
        return writeString(value);
    }

    public ObjectWriter writeBytes(String name, byte[] value) {
        writeName(name);
        return writeBytes(value);
    }

    private final void writeToBuffer(final char[] data, final int offset, final int length) {
        if (length < _LIMIT_WRITE_TO_BUFFER && length < (_bufferSize - _len)) {
            System.arraycopy(data, offset, _buffer, _len, length);
            _len += length;
        } else {
            flushBuffer();
            try {
                writer.write(data, offset, length);
            } catch (IOException e) {
                throw new JsonStreamException(e);
            }
        }
    }

    private final void writeToBuffer(final String data, final int offset) {
        writeToBuffer(data, offset, data.length());
    }

    private final void writeToBuffer(final String data, final int offset, final int length)
            {
        if (length < _LIMIT_WRITE_TO_BUFFER && length < (_bufferSize - _len)) {
            data.getChars(offset, offset+length, _buffer, _len);
            _len += length;
        } else {
            flushBuffer();
            try {
                writer.write(data, offset, length);
            } catch (IOException e) {
                throw new JsonStreamException(e);
            }
        }
    }

    private final void flushBuffer() {
        try {
            if (_len > 0) {
                writer.write(_buffer, 0, _len);
                _len = 0;
            }
        } catch (IOException ioe) {
            throw new JsonStreamException(ioe);
        }
    }

    public Writer unwrap() {
        return writer;
    }
}
