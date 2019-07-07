package de.skyrising.util;

import java.util.ArrayList;

public final class StringView implements CharSequence {
    private final String value;
    private String trimmedValue;
    private final int offset;
    private final int length;
    private int hash;

    public StringView(String value) {
        this(value, 0, value.length());
    }

    public StringView(String value, int off, int len) {
        this.value = value;
        this.offset = off;
        this.length = len;
        if (len == value.length()) trimmedValue = value;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(int index) {
        return value.charAt(this.offset + index);
    }

    @Override
    public StringView subSequence(int start, int end) {
        if (start < 0) throw new StringIndexOutOfBoundsException("start=" + start + " < 0");
        if (end < 0) throw new StringIndexOutOfBoundsException("end=" + end + " < 0");
        if (start >= length)  throw new StringIndexOutOfBoundsException("start=" + start + " >= length=" + length);
        if (end < start) throw new StringIndexOutOfBoundsException("end=" + end + " > start=" + start);
        int len = end - start;
        return new StringView(this.value, this.offset + start, len);
    }

    @Override
    public int hashCode() {
        if (hash != 0) return hash;
        if (trimmedValue != null) return hash = trimmedValue.hashCode();
        int end = offset + length;
        int h = 0;
        for (int i = offset; i < end; i++) {
            h = h * 31 + value.charAt(i);
        }
        return hash = h;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof StringView)) return false;
        StringView s = (StringView) obj;
        if (s.length != length) return false;
        if (s.value == value) return s.offset == offset;
        if (hash != 0 && s.hash != 0 && hash != s.hash) return false;
        if (trimmedValue != null && s.trimmedValue != null) return trimmedValue.equals(s.trimmedValue);
        int offsetDiff = s.offset - offset;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            if (value.charAt(i) != s.value.charAt(i + offsetDiff)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (trimmedValue != null) return trimmedValue;
        return trimmedValue = value.substring(offset, offset + length);
    }

    public static StringView[] split(String s, char c) {
        ArrayList<StringView> segments = new ArrayList<>();
        char[] chars = s.toCharArray();
        int len = chars.length;
        int prevEnd = 0;
        for (int i = 0; i < len; i++) {
            if (chars[i] != c) continue;
            segments.add(new StringView(s, prevEnd, i - prevEnd));
            prevEnd = i + 1;
        }
        if (prevEnd != len) segments.add(new StringView(s, prevEnd, len - prevEnd));
        return segments.toArray(new StringView[0]);
    }
}
