package cn.gemeopen.protocol.util;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 下行 JSON 手写序列化，保证整型字段输出 {@code 1} 而非 {@code 1.0}（对齐 transparent-codec.js）。
 */
public final class JsonWriters {

    private JsonWriters() {
    }

    public static byte[] toDevicePayload(Map<String, Object> obj, Set<String> intKeys) {
        return toDevicePayloadString(obj, intKeys).getBytes(StandardCharsets.UTF_8);
    }

    public static String toDevicePayloadString(Map<String, Object> obj, Set<String> intKeys) {
        StringBuilder sb = new StringBuilder();
        appendObject(sb, obj, intKeys);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendObject(StringBuilder sb, Map<String, Object> obj, Set<String> intKeys) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\":");
            appendValue(sb, e.getKey(), v, intKeys);
        }
        sb.append('}');
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, String key, Object v, Set<String> intKeys) {
        if (v instanceof Map<?, ?> map) {
            appendObject(sb, (Map<String, Object>) map, intKeys);
        } else if (v instanceof Collection<?> collection) {
            sb.append('[');
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, key, item, intKeys);
            }
            sb.append(']');
        } else if (intKeys.contains(key)) {
            sb.append(toInt(v));
        } else if (v instanceof Number number) {
            sb.append(number);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else {
            sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
        }
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
