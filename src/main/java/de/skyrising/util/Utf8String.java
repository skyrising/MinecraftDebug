package de.skyrising.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Utf8String implements CharSequence {
    private final byte[] value;
    private final char[] charValue;
    private final int length;
    private int hash;

    public Utf8String(byte[] value) {
        this.value = value;
        for (int i = 0; i < value.length; i++) {
            if (value[i] < 0) {
                try {
                    CharBuffer buf = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value));
                    length = buf.limit();
                    charValue = new char[length];
                    buf.get(charValue);
                } catch (CharacterCodingException e) {
                    throw new RuntimeException(e);
                }
                return;
            }
        }
        charValue = null;
        length = value.length;
    }

    Utf8String(char[] charValue) {
        this.value = null;
        this.charValue = charValue;
        this.length = charValue.length;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(int index) {
        if (charValue != null) return charValue[index];
        return (char) value[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (charValue != null) {
            return new Utf8String(Arrays.copyOfRange(charValue, start, end));
        }
        return new Utf8String(Arrays.copyOfRange(value, start, end));
    }

    @Override
    public String toString() {
        if (charValue != null) return new String(charValue);
        return new String(value, StandardCharsets.US_ASCII);
    }

    @Override
    public int hashCode() {
        if (hash != 0) return hash;
        int h = 0;
        for (int i = 0; i < length; i++) {
            h = h * 31 + charAt(i);
        }
        return hash = h;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Utf8String)) return false;
        Utf8String s = (Utf8String) obj;
        if (s.length != length) return false;
        if (charValue != null) {
            if (s.charValue != null) return Arrays.equals(charValue, s.charValue);
            for (int i = 0; i < length; i++) {
                if (charAt(i) != s.charAt(i)) return false;
            }
            return true;
        }
        return Arrays.equals(value, s.value);
    }
}
