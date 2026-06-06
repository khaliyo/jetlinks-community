package cn.gemeopen.protocol.codec;

import java.util.Map;

public sealed interface UpstreamPart permits UpstreamPart.PropertyReport, UpstreamPart.Event, UpstreamPart.FunctionReply {

    record PropertyReport(Map<String, Object> properties) implements UpstreamPart {
    }

    record Event(String eventId, Map<String, Object> data) implements UpstreamPart {
    }

    record FunctionReply(String messageId, boolean success, Object output) implements UpstreamPart {
    }
}
