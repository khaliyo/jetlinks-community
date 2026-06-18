package cn.gemeopen.protocol.codec;

import cn.gemeopen.protocol.profile.ProductProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 上行 JSON → 平台语义片段，行为对齐 specs/gspm1b/transparent-codec.js */
public class UpstreamDecoder {

    private final ProductProfile profile;
    private final Set<String> enumProps;
    private final Set<String> stringEnumProps;
    private final Set<String> intProps;
    private final Set<String> floatProps;

    public UpstreamDecoder(ProductProfile profile) {
        this.profile = profile;
        this.enumProps = new HashSet<>(profile.getEnumProperties());
        this.stringEnumProps = new HashSet<>(profile.getStringEnumProperties());
        this.intProps = new HashSet<>(profile.getIntProperties());
        this.floatProps = new HashSet<>(profile.getFloatProperties());
    }

    public List<UpstreamPart> decode(Map<String, ?> data, String fallbackDeviceId) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        if (resolveDeviceId(data, fallbackDeviceId) == null) {
            return List.of();
        }

        UpstreamPart.FunctionReply functionReply = buildFunctionReply(data);
        if (functionReply != null) {
            Map<String, Object> props = mapProperties(data);
            List<UpstreamPart> out = new ArrayList<>(2);
            Object output = functionReply.output();
            if (Boolean.TRUE.equals(output) && !props.isEmpty()) {
                out.add(new UpstreamPart.FunctionReply(functionReply.messageId(), true, props));
            } else {
                out.add(functionReply);
            }
            if (!props.isEmpty()) {
                out.add(new UpstreamPart.PropertyReport(props));
            }
            return out;
        }

        Map<String, Object> props = mapProperties(data);
        UpstreamPart.Event event = buildEvent(data);
        if (event != null && !props.isEmpty()) {
            return List.of(event, new UpstreamPart.PropertyReport(props));
        }
        if (event != null) {
            return List.of(event);
        }
        if (props.isEmpty()) {
            return List.of();
        }
        return List.of(new UpstreamPart.PropertyReport(props));
    }

    private String resolveDeviceId(Map<String, ?> data, String fallbackDeviceId) {
        Object mac = data.get("mac");
        if (mac != null && !String.valueOf(mac).isEmpty()) {
            return String.valueOf(mac);
        }
        return fallbackDeviceId;
    }

    private Map<String, Object> mapProperties(Map<String, ?> data) {
        Map<String, Object> props = new HashMap<>();
        for (Map.Entry<String, String> e : profile.getPropertyMapping().entrySet()) {
            String src = e.getKey();
            String dest = e.getValue();
            Object raw = getField(data, src);
            if (raw == null || String.valueOf(raw).isEmpty()) {
                continue;
            }
            if (stringEnumProps.contains(dest)) {
                putIfPresent(props, dest, raw);
            } else if (enumProps.contains(dest)) {
                putIfPresent(props, dest, toEnumValue(raw));
            } else if (intProps.contains(dest)) {
                putIfPresent(props, dest, toInt(raw));
            } else if (floatProps.contains(dest)) {
                putIfPresent(props, dest, toFloat(raw));
            } else {
                putIfPresent(props, dest, raw);
            }
        }
        putIfPresent(props, "deviceType", data.get("type"));
        putIfPresent(props, "lastCommandName", data.get("commandName"));
        putIfPresent(props, "lastSource", data.get("source"));
        return props;
    }

    private UpstreamPart.Event buildEvent(Map<String, ?> data) {
        for (Map.Entry<String, ProductProfile.EventRule> entry : profile.getEvents().entrySet()) {
            ProductProfile.EventRule rule = entry.getValue();
            if (!matchesEvent(data, rule)) {
                continue;
            }
            Map<String, Object> eventData = new HashMap<>();
            for (String field : rule.getPayload()) {
                Object raw = data.get(field);
                if (raw == null) {
                    continue;
                }
                String dest = profile.getPropertyMapping().getOrDefault(field, field);
                if (stringEnumProps.contains(dest)) {
                    putIfPresent(eventData, dest, raw);
                } else if (enumProps.contains(dest)) {
                    putIfPresent(eventData, dest, toEnumValue(raw));
                } else if (intProps.contains(dest)) {
                    putIfPresent(eventData, dest, toInt(raw));
                } else {
                    putIfPresent(eventData, dest, raw);
                }
            }
            return new UpstreamPart.Event(entry.getKey(), eventData);
        }
        return null;
    }

    private boolean matchesEvent(Map<String, ?> data, ProductProfile.EventRule rule) {
        for (Map.Entry<String, String> entry : rule.getMatch().entrySet()) {
            Object value = data.get(entry.getKey());
            if (value == null || !String.valueOf(value).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean isCommandResponse(Map<String, ?> data) {
        for (ProductProfile.CommandResponseRule rule : profile.getCommandResponse().getRules()) {
            if (matchesCommandResponse(data, rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCommandResponse(Map<String, ?> data, ProductProfile.CommandResponseRule rule) {
        String source = stringOrNull(data.get("source"));
        String commandName = stringOrNull(data.get("commandName"));
        String messageId = stringOrNull(data.get("messageId"));

        if (rule.getSource() != null && rule.getSource().equals(source)) {
            return true;
        }
        if (rule.getCommandNameIn() != null && !rule.getCommandNameIn().isEmpty()) {
            if (rule.getSourceNot() != null && rule.getSourceNot().equals(source)) {
                return false;
            }
            if (commandName != null && rule.getCommandNameIn().contains(commandName)) {
                if (Boolean.TRUE.equals(rule.getRequireMessageId())) {
                    return messageId != null;
                }
                return true;
            }
        }
        return false;
    }

    private UpstreamPart.FunctionReply buildFunctionReply(Map<String, ?> data) {
        if (!isCommandResponse(data)) {
            return null;
        }
        String messageId = stringOrNull(data.get("messageId"));
        if (messageId == null) {
            return null;
        }
        Map<String, Object> props = mapProperties(data);
        Object output = props.isEmpty() ? Boolean.TRUE : props;
        return new UpstreamPart.FunctionReply(messageId, true, output);
    }


    @SuppressWarnings("unchecked")
    private static Object getField(Map<String, ?> data, String key) {
        if (data == null || key == null) {
            return null;
        }
        if (data.containsKey(key)) {
            return data.get(key);
        }
        int dot = key.indexOf('.');
        if (dot <= 0 || dot >= key.length() - 1) {
            return null;
        }
        Object parent = data.get(key.substring(0, dot));
        if (parent instanceof Map<?, ?> nested) {
            return getField((Map<String, ?>) nested, key.substring(dot + 1));
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isEmpty()) {
            target.put(key, value);
        }
    }

    private static String toEnumValue(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return null;
        }
        return String.valueOf(Integer.parseInt(String.valueOf(value).split("\\.")[0]));
    }

    private static Integer toInt(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value).split("\\.")[0]);
    }

    private static Double toFloat(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String stringOrNull(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }
}
