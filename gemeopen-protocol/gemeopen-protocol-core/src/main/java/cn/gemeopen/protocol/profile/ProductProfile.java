package cn.gemeopen.protocol.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductProfile {

    private String profileVersion = "1";
    private String productId;
    private String name;
    private String deviceModelHint;
    private TopicTemplate topic = new TopicTemplate();
    private Map<String, String> propertyMapping = Collections.emptyMap();
    private List<String> enumProperties = Collections.emptyList();
    private List<String> stringEnumProperties = Collections.emptyList();
    private List<String> intProperties = Collections.emptyList();
    private List<String> floatProperties = Collections.emptyList();
    private CommandResponseRules commandResponse = new CommandResponseRules();
    private Map<String, EventRule> events = Collections.emptyMap();
    private Map<String, FunctionRule> functions = Collections.emptyMap();
    private Map<String, WritePropertyRule> writeProperties = Collections.emptyMap();

    public String getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(String profileVersion) {
        this.profileVersion = profileVersion;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceModelHint() {
        return deviceModelHint;
    }

    public void setDeviceModelHint(String deviceModelHint) {
        this.deviceModelHint = deviceModelHint;
    }

    public TopicTemplate getTopic() {
        return topic;
    }

    public void setTopic(TopicTemplate topic) {
        this.topic = topic != null ? topic : new TopicTemplate();
    }

    public Map<String, String> getPropertyMapping() {
        return propertyMapping;
    }

    public void setPropertyMapping(Map<String, String> propertyMapping) {
        this.propertyMapping = propertyMapping != null ? propertyMapping : Collections.emptyMap();
    }

    public List<String> getEnumProperties() {
        return enumProperties;
    }

    public void setEnumProperties(List<String> enumProperties) {
        this.enumProperties = enumProperties != null ? enumProperties : Collections.emptyList();
    }

    public List<String> getStringEnumProperties() {
        return stringEnumProperties;
    }

    public void setStringEnumProperties(List<String> stringEnumProperties) {
        this.stringEnumProperties = stringEnumProperties != null ? stringEnumProperties : Collections.emptyList();
    }

    public List<String> getIntProperties() {
        return intProperties;
    }

    public void setIntProperties(List<String> intProperties) {
        this.intProperties = intProperties != null ? intProperties : Collections.emptyList();
    }

    public List<String> getFloatProperties() {
        return floatProperties;
    }

    public void setFloatProperties(List<String> floatProperties) {
        this.floatProperties = floatProperties != null ? floatProperties : Collections.emptyList();
    }

    public CommandResponseRules getCommandResponse() {
        return commandResponse;
    }

    public void setCommandResponse(CommandResponseRules commandResponse) {
        this.commandResponse = commandResponse != null ? commandResponse : new CommandResponseRules();
    }

    public Map<String, EventRule> getEvents() {
        return events;
    }

    public void setEvents(Map<String, EventRule> events) {
        this.events = events != null ? events : Collections.emptyMap();
    }

    public Map<String, FunctionRule> getFunctions() {
        return functions;
    }

    public void setFunctions(Map<String, FunctionRule> functions) {
        this.functions = functions != null ? functions : Collections.emptyMap();
    }

    public Map<String, WritePropertyRule> getWriteProperties() {
        return writeProperties;
    }

    public void setWriteProperties(Map<String, WritePropertyRule> writeProperties) {
        this.writeProperties = writeProperties != null ? writeProperties : Collections.emptyMap();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TopicTemplate {
        private String upstream = "/{productId}/{deviceId}/up";
        private String downstream = "/{productId}/{deviceId}/down";

        public String getUpstream() {
            return upstream;
        }

        public void setUpstream(String upstream) {
            this.upstream = upstream;
        }

        public String getDownstream() {
            return downstream;
        }

        public void setDownstream(String downstream) {
            this.downstream = downstream;
        }

        public String resolveUpstream(String productId, String deviceId) {
            return apply(upstream, productId, deviceId);
        }

        public String resolveDownstream(String productId, String deviceId) {
            return apply(downstream, productId, deviceId);
        }

        private static String apply(String template, String productId, String deviceId) {
            return template
                .replace("{productId}", productId)
                .replace("{deviceId}", deviceId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommandResponseRules {
        private List<CommandResponseRule> rules = Collections.emptyList();

        public List<CommandResponseRule> getRules() {
            return rules;
        }

        public void setRules(List<CommandResponseRule> rules) {
            this.rules = rules != null ? rules : Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommandResponseRule {
        private String source;
        private List<String> commandNameIn;
        private String sourceNot;
        private Boolean requireMessageId;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public List<String> getCommandNameIn() {
            return commandNameIn;
        }

        public void setCommandNameIn(List<String> commandNameIn) {
            this.commandNameIn = commandNameIn;
        }

        public String getSourceNot() {
            return sourceNot;
        }

        public void setSourceNot(String sourceNot) {
            this.sourceNot = sourceNot;
        }

        public Boolean getRequireMessageId() {
            return requireMessageId;
        }

        public void setRequireMessageId(Boolean requireMessageId) {
            this.requireMessageId = requireMessageId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventRule {
        private Map<String, String> match = Collections.emptyMap();
        private List<String> payload = Collections.emptyList();

        public Map<String, String> getMatch() {
            return match;
        }

        public void setMatch(Map<String, String> match) {
            this.match = match != null ? match : Collections.emptyMap();
        }

        public List<String> getPayload() {
            return payload;
        }

        public void setPayload(List<String> payload) {
            this.payload = payload != null ? payload : Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionRule {
        private DownstreamTemplate downstream = new DownstreamTemplate();

        public DownstreamTemplate getDownstream() {
            return downstream;
        }

        public void setDownstream(DownstreamTemplate downstream) {
            this.downstream = downstream != null ? downstream : new DownstreamTemplate();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WritePropertyRule {
        private DownstreamTemplate downstream = new DownstreamTemplate();

        public DownstreamTemplate getDownstream() {
            return downstream;
        }

        public void setDownstream(DownstreamTemplate downstream) {
            this.downstream = downstream != null ? downstream : new DownstreamTemplate();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DownstreamTemplate {
        private String type;
        private Map<String, Object> fields = Collections.emptyMap();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getFields() {
            return fields;
        }

        public void setFields(Map<String, Object> fields) {
            this.fields = fields != null ? fields : Collections.emptyMap();
        }
    }
}
