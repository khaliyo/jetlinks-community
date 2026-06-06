package cn.gemeopen.protocol.util;

import java.nio.charset.StandardCharsets;
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
        StringBuilder sb = new StringBuilder("{");
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
            String k = e.getKey();
            sb.append('"').append(escapeJson(k)).append("\":");
            if (intKeys.contains(k)) {
                sb.append(toInt(v));
            } else if (v instanceof Number number) {
                sb.append(number);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
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
