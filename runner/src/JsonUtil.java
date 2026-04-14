import java.util.*;

/**
 * Minimal JSON parser and serializer. No external dependencies.
 * Supports: String, Number (long/double), Boolean, null, Object, Array.
 */
public class JsonUtil {

    // ======================== SERIALIZER ========================

    public static String toJson(Map<String, Object> map) {
        if (map == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append(valueToJson(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String toJsonArray(List<?> list) {
        if (list == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append(valueToJson(item));
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object val) {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + escape((String) val) + "\"";
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        if (val instanceof Map) return toJson((Map<String, Object>) val);
        if (val instanceof List) return toJsonArray((List<?>) val);
        return "\"" + escape(val.toString()) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ======================== PARSER ========================

    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Parser p = new Parser(json.trim());
        Object result = p.parseValue();
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            return map;
        }
        throw new RuntimeException("Expected JSON object, got: " + (result == null ? "null" : result.getClass().getSimpleName()));
    }

    public static List<Object> parseArray(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        Parser p = new Parser(json.trim());
        Object result = p.parseValue();
        if (result instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) result;
            return list;
        }
        throw new RuntimeException("Expected JSON array");
    }

    // ======================== HELPERS ========================

    public static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        // Avoid "8083.0" for integer-valued doubles
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        if (v instanceof Long) return String.valueOf((long)(Long) v);
        return v.toString();
    }

    public static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    public static boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getArrayOfObjects(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : (List<?>) v) {
                if (item instanceof Map) result.add((Map<String, Object>) item);
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getStringArray(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) v) {
                result.add(item != null ? item.toString() : "");
            }
            return result;
        }
        return new ArrayList<>();
    }

    // ======================== RECURSIVE DESCENT PARSER ========================

    private static class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) return null;
            char c = input.charAt(pos);
            if (c == '{') return parseObj();
            if (c == '[') return parseArr();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        private Map<String, Object> parseObj() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos >= input.length()) break;
                if (input.charAt(pos) == ',') { pos++; continue; }
                if (input.charAt(pos) == '}') { pos++; break; }
                throw error("Expected ',' or '}'");
            }
            return map;
        }

        private List<Object> parseArr() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (pos >= input.length()) break;
                if (input.charAt(pos) == ',') { pos++; continue; }
                if (input.charAt(pos) == ']') { pos++; break; }
                throw error("Expected ',' or ']'");
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= input.length()) throw error("Unexpected end in string escape");
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"': case '\\': case '/': sb.append(esc); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > input.length()) throw error("Unexpected end in unicode escape");
                            sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < input.length() && input.charAt(pos) == '.') { isDouble = true; pos++; while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++; }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) { isDouble = true; pos++; if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++; while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++; }
            String num = input.substring(start, pos);
            return isDouble ? Double.parseDouble(num) : Long.parseLong(num);
        }

        private Boolean parseBool() {
            if (input.startsWith("true", pos)) { pos += 4; return true; }
            if (input.startsWith("false", pos)) { pos += 5; return false; }
            throw error("Expected boolean");
        }

        private Object parseNull() {
            if (input.startsWith("null", pos)) { pos += 4; return null; }
            throw error("Expected null");
        }

        private void expect(char c) {
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != c)
                throw error("Expected '" + c + "'");
            pos++;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private RuntimeException error(String msg) {
            return new RuntimeException("JSON parse error at pos " + pos + ": " + msg);
        }
    }
}
