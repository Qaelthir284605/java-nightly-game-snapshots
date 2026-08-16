package dev.infrai.gamesnapshot;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.index != text.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return '"' + escape(s) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(write(key.toString()) + ":" + write(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> values = new ArrayList<>();
            items.forEach(item -> values.add(write(item)));
            return "[" + String.join(",", values) + "]";
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            try {
                for (RecordComponent component : value.getClass().getRecordComponents()) {
                    fields.put(component.getName(), component.getAccessor().invoke(value));
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Cannot encode record", exception);
            }
            return write(fields);
        }
        return write(value.toString());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class Parser {
        private final String text;
        private int index;
        private Parser(String text) { this.text = text; }

        private Object value() {
            space();
            if (index >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return switch (text.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                expect(':');
                result.put(key, value());
                space();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            index++;
            space();
            if (take(']')) return result;
            do { result.add(value()); space(); } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') return result.toString();
                if (current != '\\') { result.append(current); continue; }
                char escaped = text.charAt(index++);
                if (escaped == 'u') {
                    result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                    index += 4;
                } else {
                    result.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n';
                        case 'r' -> '\r'; case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Bad JSON escape");
                    });
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object number() {
            int start = index;
            while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) index++;
            String token = text.substring(start, index);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.valueOf(token) : Long.valueOf(token);
        }

        private Object literal(String token, Object value) {
            if (!text.startsWith(token, index)) throw new IllegalArgumentException("Bad JSON literal");
            index += token.length();
            return value;
        }

        private void space() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        private boolean take(char expected) {
            if (index < text.length() && text.charAt(index) == expected) { index++; return true; }
            return false;
        }
        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected " + expected);
        }
    }
}
