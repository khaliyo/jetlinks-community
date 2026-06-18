package cn.gemeopen.protocol.codec;

import cn.gemeopen.protocol.profile.ProductProfile;
import cn.gemeopen.protocol.util.JsonWriters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DownstreamEncoder {

    private static final Set<String> INT_KEYS = Set.of(
        "key", "key1", "key2", "no", "number", "hour", "minute", "port", "countdownSecond", "timerEnable", "timerInterval", "keyLock", "onState", "onState1", "onState2", "wifiLock", "value"
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

    public EncodedDownstream encodeReadProperty(List<String> properties, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("missing platform messageId on downstream encode");
        }
        String type = "info";
        if (properties != null) {
            for (String prop : properties) {
                if (profile.getFloatProperties().contains(prop)) {
                    type = "statistic";
                    break;
                }
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("messageId", messageId);
        body.put("type", type);
        byte[] payload = JsonWriters.toDevicePayload(body, Set.of());
        return new EncodedDownstream(payload);
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
        for (Map.Entry<String, Object> field : template.getFields().entrySet()) {
            Object resolved = resolvePlaceholder(field.getValue(), inputs, writeCtx, messageId);
            if (resolved != null) {
                body.put(field.getKey(), resolved);
            }
        }
        if (!body.containsKey("messageId")) {
            body.put("messageId", messageId);
        }
        Set<String> intKeys = new HashSet<>(INT_KEYS);
        if ("setting".equals(template.getType()) && body.containsKey("system")) {
            // system:restart 等非整型字段
        }
        byte[] payload = JsonWriters.toDevicePayload(body, intKeys);
        return new EncodedDownstream(payload);
    }

    @SuppressWarnings("unchecked")
    private Object resolvePlaceholder(Object expr, Map<String, Object> inputs, Map<String, Object> writeCtx, String messageId) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof Map<?, ?> map) {
            Map<String, Object> nested = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object resolved = resolvePlaceholder(entry.getValue(), inputs, writeCtx, messageId);
                if (resolved != null) {
                    nested.put(String.valueOf(entry.getKey()), resolved);
                }
            }
            return nested.isEmpty() ? null : nested;
        }
        if (!(expr instanceof String s)) {
            return expr;
        }
        if ("${messageId}".equals(s)) {
            return messageId;
        }
        if ("${value}".equals(s) && writeCtx != null) {
            return toIntOrRaw(writeCtx.get("value"));
        }
        if (s.startsWith("${inputs.") && s.endsWith("}")) {
            String key = s.substring("${inputs.".length(), s.length() - 1);
            return toIntOrRaw(inputs.get(key));
        }
        return s;
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
