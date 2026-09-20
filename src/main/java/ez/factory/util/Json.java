package ez.factory.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON parser / writer (RFC 8259 subset: objects, arrays,
 * strings, numbers->Double, booleans, null). Keeps the factory buildable with plain
 * javac on any machine (no Maven repository access required).
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.val();
        p.ws();
        if (p.i < p.s.length()) throw new RuntimeException("Trailing garbage in JSON at offset " + p.i);
        return v;
    }

    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) throw new RuntimeException("Expected JSON object at top level");
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        return m;
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object val() {
        if (i >= s.length()) throw new RuntimeException("Unexpected end of JSON");
        char c = s.charAt(i);
        switch (c) {
            case '{': return obj();
            case '[': return arr();
            case '"': return str();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return num();
        }
    }

    private void expect(String lit) {
        if (!s.startsWith(lit, i)) throw new RuntimeException("Bad literal at " + i);
        i += lit.length();
    }

    private Map<String, Object> obj() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = str();
            ws();
            if (s.charAt(i) != ':') throw new RuntimeException("Expected ':' at " + i);
            i++;
            ws();
            m.put(k, val());
            ws();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; return m; }
            throw new RuntimeException("Expected ',' or '}' at " + i);
        }
    }

    private List<Object> arr() {
        List<Object> l = new ArrayList<>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(val());
            ws();
            char c = s.charAt(i);
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; return l; }
            throw new RuntimeException("Expected ',' or ']' at " + i);
        }
    }

    private String str() {
        if (s.charAt(i) != '"') throw new RuntimeException("Expected string at " + i);
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new RuntimeException("Bad escape \\" + e + " at " + i);
                }
            } else b.append(c);
        }
    }

    private Double num() {
        int start = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        return Double.parseDouble(s.substring(start, i));
    }

    // ---------- writing ----------

    public static String stringify(Object o) { return w(o, 0, -1); }

    public static String pretty(Object o) { return w(o, 0, 2); }

    private static String w(Object o, int depth, int indent) {
        StringBuilder b = new StringBuilder();
        write(b, o, depth, indent);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object o, int depth, int indent) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                nl(b, depth + 1, indent);
                writeString(b, e.getKey());
                b.append(indent >= 0 ? ": " : ":");
                write(b, e.getValue(), depth + 1, indent);
            }
            if (!first) nl(b, depth, indent);
            b.append('}');
            return;
        }
        if (o instanceof List) {
            List<Object> l = (List<Object>) o;
            b.append('[');
            boolean first = true;
            for (Object e : l) {
                if (!first) b.append(',');
                first = false;
                nl(b, depth + 1, indent);
                write(b, e, depth + 1, indent);
            }
            if (!first) nl(b, depth, indent);
            b.append(']');
            return;
        }
        if (o instanceof Boolean) { b.append(o.toString()); return; }
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.floor(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
            return;
        }
        writeString(b, String.valueOf(o));
    }

    private static void nl(StringBuilder b, int depth, int indent) {
        if (indent < 0) return;
        b.append('\n');
        for (int k = 0; k < depth * indent; k++) b.append(' ');
    }

    private static void writeString(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ---------- tolerant accessors ----------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static Map<String, Object> sub(Object o, String k) { return obj(obj(o).get(k)); }

    public static List<Object> list(Object o, String k) { return arr(obj(o).get(k)); }

    public static String str(Object o, String k, String def) {
        Object v = obj(o).get(k);
        return v == null ? def : String.valueOf(v);
    }

    public static List<String> strList(Object o, String k) {
        List<String> out = new ArrayList<>();
        for (Object e : list(o, k)) if (e != null) out.add(String.valueOf(e));
        return out;
    }

    public static boolean bool(Object o, String k, boolean def) {
        Object v = obj(o).get(k);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    public static int int_(Object o, String k, int def) {
        Object v = obj(o).get(k);
        if (v == null) return def;
        try { return (int) Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    public static double dbl(Object o, String k, double def) {
        Object v = obj(o).get(k);
        if (v == null) return def;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return def; }
    }
}
