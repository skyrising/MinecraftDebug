package de.skyrising.minecraft.debug;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StackTraceUtil {
    private static final Pattern STE_PATTERN = Pattern.compile("^\\s*at\\s+(?<class>(?:[\\w$\\/]+\\.)*[\\w$\\/]+)\\.(?<method>\\w+)\\((?<file>.+?)(?::(?<line>\\d+))?\\)$");

    private StackTraceUtil() {}

    public static StackTraceElement parse(String line) {
        Matcher match = STE_PATTERN.matcher(line);
        if (!match.find()) return null;
        String lineNumber = match.group("line");
        if (lineNumber == null) {
            return new StackTraceElement(
                    match.group("class"),
                    match.group("method"),
                    null,
                    -1
            );
        }
        return new StackTraceElement(
                match.group("class"),
                match.group("method"),
                match.group("file"),
                Integer.parseInt(match.group("line"))
        );
    }
}
