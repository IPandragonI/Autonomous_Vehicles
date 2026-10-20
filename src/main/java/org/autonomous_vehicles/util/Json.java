package org.autonomous_vehicles.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer, so saved files need no library (the project has no runtime dependency
 * besides JavaFX).
 * <p>
 * Values map to: object → {@code Map<String, Object>} (key order kept), array → {@code List<Object>},
 * integer → {@link Long}, other number → {@link Double}, string, {@link Boolean}, {@code null}.
 * Doubles are written with {@link Double#toString}, the shortest text that parses back to the same bits:
 * a reloaded genome is bit-identical, so a reloaded network drives exactly the same trajectory.
 */
public final class Json {

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Pretty-printed JSON with two-space indentation; arrays of numbers stay on one line (weight rows).
     *
     * @throws IllegalArgumentException for NaN or infinite numbers (not representable in JSON) and unknown types
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.append('\n').toString();
    }

    private static void write(Object value, StringBuilder out, int indent) {
        switch (value) {
            case null -> out.append("null");
            case Boolean bool -> out.append(bool);
            case Double number -> out.append(number(number));
            case Float number -> out.append(number(number.doubleValue()));
            case Number number -> out.append(number.longValue());
            case String string -> writeString(string, out);
            case Map<?, ?> map -> writeObject(map, out, indent);
            case List<?> list -> writeArray(list, out, indent);
            default -> throw new IllegalArgumentException("Cannot write " + value.getClass().getSimpleName() + " as JSON.");
        }
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON cannot represent " + value + ".");
        }

        return Double.toString(value);
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int indent) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }

        out.append("{\n");
        int i = 0;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object keys must be strings.");
            }

            out.append("  ".repeat(indent + 1));
            writeString(key, out);
            out.append(": ");
            write(entry.getValue(), out, indent + 1);
            out.append(++i < map.size() ? ",\n" : "\n");
        }

        out.append("  ".repeat(indent)).append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out, int indent) {
        boolean inline = list.stream().allMatch(element -> element instanceof Number || element instanceof Boolean
                || element instanceof String);

        if (list.isEmpty() || inline) {
            out.append('[');

            for (int i = 0; i < list.size(); i++) {
                write(list.get(i), out, indent);
                out.append(i < list.size() - 1 ? ", " : "");
            }

            out.append(']');
            return;
        }

        out.append("[\n");

        for (int i = 0; i < list.size(); i++) {
            out.append("  ".repeat(indent + 1));
            write(list.get(i), out, indent + 1);
            out.append(i < list.size() - 1 ? ",\n" : "\n");
        }

        out.append("  ".repeat(indent)).append(']');
    }

    private static void writeString(String string, StringBuilder out) {
        out.append('"');

        for (char c : string.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        out.append('"');
    }

    /**
     * Parses one JSON value (surrounding whitespace allowed).
     *
     * @throws IllegalArgumentException with the character offset when the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text must not be null.");
        }

        Json parser = new Json(text);
        Object value = parser.value();
        parser.skipWhitespace();

        if (parser.position != text.length()) {
            throw parser.error("Unexpected text after the JSON value");
        }

        return value;
    }

    private Object value() {
        skipWhitespace();

        if (position >= text.length()) {
            throw error("Unexpected end of JSON");
        }

        char c = text.charAt(position);

        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }

                throw error("Unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        position++;
        skipWhitespace();

        if (peek('}')) {
            position++;
            return map;
        }

        while (true) {
            skipWhitespace();

            if (!peek('"')) {
                throw error("Expected a string key");
            }

            String key = string();
            skipWhitespace();
            expect(':');

            if (map.containsKey(key)) {
                throw error("Duplicate key '" + key + "'");
            }

            map.put(key, value());
            skipWhitespace();

            if (peek(',')) {
                position++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        position++;
        skipWhitespace();

        if (peek(']')) {
            position++;
            return list;
        }

        while (true) {
            list.add(value());
            skipWhitespace();

            if (peek(',')) {
                position++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        position++;

        while (position < text.length()) {
            char c = text.charAt(position++);

            if (c == '"') {
                return out.toString();
            }

            if (c != '\\') {
                out.append(c);
                continue;
            }

            if (position >= text.length()) {
                break;
            }

            char escaped = text.charAt(position++);

            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw error("Truncated \\u escape");
                    }

                    try {
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("Invalid \\u escape");
                    }

                    position += 4;
                }
                default -> throw error("Invalid escape '\\" + escaped + "'");
            }
        }

        throw error("Unterminated string");
    }

    private Object number() {
        int start = position;

        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }

        String token = text.substring(start, position);

        if (token.matches("-?\\d+")) {
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException tooLarge) {
                return Double.parseDouble(token);
            }
        }

        if (token.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            return Double.parseDouble(token);
        }

        position = start;
        throw error("Invalid number '" + token + "'");
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) {
            throw error("Unexpected character '" + text.charAt(position) + "'");
        }

        position += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }

        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position + ".");
    }
}
