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

    private static final Set<String> FLOAT_SOURCES = Set.of("current", "energy", "power", "voltage");

    private final ProductProfile profile;
    private final Set<String> enumProps;
    private final Set<String> intProps;

    public UpstreamDecoder(ProductProfile profile) {
        this.profile = profile;
        this.enumProps = new HashSet<>(profile.getEnumProperties());
        this.intProps = new HashSet<>(profile.getIntProperties());
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
            Object raw = data.get(src);
            if (raw == null || String.valueOf(raw).isEmpty()) {
                continue;
            }
            if (enumProps.contains(dest)) {
                putIfPresent(props, dest, toEnumValue(raw));
            } else if (intProps.contains(dest)) {
                putIfPresent(props, dest, toInt(raw));
            } else if (FLOAT_SOURCES.contains(src)) {
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
        if (!"controller-event".equals(String.valueOf(data.get("commandName")))) {
            return null;
        }
        Map<String, Object> eventData = new HashMap<>();
        putIfPresent(eventData, "key", toInt(data.get("key")));
        putIfPresent(eventData, "onState", toInt(data.get("onState")));
        putIfPresent(eventData, "mac", data.get("mac"));
        return new UpstreamPart.Event("controllerEvent", eventData);
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
