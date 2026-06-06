package cn.gemeopen.protocol.codec;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TopicPaths {

    private static final Pattern UP_TOPIC = Pattern.compile("^/([^/]+)/([^/]+)/up$");

    private TopicPaths() {
    }

    public static Optional<TopicInfo> parseUpstreamTopic(String topic) {
        if (topic == null) {
            return Optional.empty();
        }
        Matcher m = UP_TOPIC.matcher(topic);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new TopicInfo(m.group(1), m.group(2)));
    }

    public record TopicInfo(String productId, String deviceId) {
    }
}
