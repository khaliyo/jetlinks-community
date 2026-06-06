package cn.gemeopen.protocol.codec;

import cn.gemeopen.protocol.profile.ProductProfile;
import cn.gemeopen.protocol.util.JsonWriters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DownstreamEncoder {

    private static final Set<String> INT_KEYS = Set.of(
        "key", "timerEnable", "timerInterval", "keyLock", "onState", "wifiLock"
    );

    private final ProductProfile profile;

    public DownstreamEncoder(ProductProfile profile) {
        this.profile = profile;
    }

    public EncodedDownstream encodeFunction(String functionId, Map<String, Object> inputs, String messageId) {
        ProductProfile.FunctionRule rule = profile.getFunctions().get(functionId);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown function: " + functionId);
        }
        return encodeTemplate(rule.getDownstream(), inputs, null, messageId);
    }

    public EncodedDownstream encodeWriteProperty(String propertyId, Object value, String messageId) {
        ProductProfile.WritePropertyRule rule = profile.getWriteProperties().get(propertyId);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown write property: " + propertyId);
        }
        Map<String, Object> ctx = Map.of("value", value);
        return encodeTemplate(rule.getDownstream(), Map.of(), ctx, messageId);
    }

    private EncodedDownstream encodeTemplate(
        ProductProfile.DownstreamTemplate template,
        Map<String, Object> inputs,
        Map<String, Object> writeCtx,
        String messageId
    ) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("missing platform messageId on downstream encode");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("type", template.getType());
        for (Map.Entry<String, String> field : template.getFields().entrySet()) {
            Object resolved = resolvePlaceholder(field.getValue(), inputs, writeCtx);
            if (resolved != null) {
                body.put(field.getKey(), resolved);
            }
        }
        body.put("messageId", messageId);
        Set<String> intKeys = new HashSet<>(INT_KEYS);
        if ("setting".equals(template.getType()) && body.containsKey("system")) {
            // system:restart 等非整型字段
        }
        byte[] payload = JsonWriters.toDevicePayload(body, intKeys);
        return new EncodedDownstream(payload);
    }

    private Object resolvePlaceholder(String expr, Map<String, Object> inputs, Map<String, Object> writeCtx) {
        if (expr == null) {
            return null;
        }
        if ("${value}".equals(expr) && writeCtx != null) {
            return toIntOrRaw(writeCtx.get("value"));
        }
        if (expr.startsWith("${inputs.") && expr.endsWith("}")) {
            String key = expr.substring("${inputs.".length(), expr.length() - 1);
            return toIntOrRaw(inputs.get(key));
        }
        return expr;
    }

    private Object toIntOrRaw(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public record EncodedDownstream(byte[] payload) {
    }
}
