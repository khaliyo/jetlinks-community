package cn.gemeopen.protocol.codec;

import cn.gemeopen.protocol.profile.ProductProfile;
import cn.gemeopen.protocol.profile.ProfileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gspm1bCodecTest {

    private static ProductProfile profile;
    private static UpstreamDecoder upstream;
    private static DownstreamEncoder downstream;

    @BeforeAll
    static void loadProfile() throws Exception {
        try (InputStream in = Gspm1bCodecTest.class.getClassLoader()
            .getResourceAsStream("gemeopen/profiles/gspm1b.json")) {
            profile = ProfileLoader.load(in);
        }
        upstream = new UpstreamDecoder(profile);
        downstream = new DownstreamEncoder(profile);
    }

    @Test
    void propertyReportMapsEnumAsString() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "c82b96f821e2");
        data.put("key", 1);
        data.put("current", 0.05);

        List<UpstreamPart> parts = upstream.decode(data, "c82b96f821e2");
        assertEquals(1, parts.size());
        UpstreamPart.PropertyReport report = (UpstreamPart.PropertyReport) parts.get(0);
        assertEquals("1", report.properties().get("switchState"));
        assertEquals(0.05, report.properties().get("current"));
    }

    @Test
    void commandResponseProducesFunctionReplyAndProperties() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "c82b96f821e2");
        data.put("source", "command");
        data.put("messageId", "1234567890");
        data.put("key", 1);

        List<UpstreamPart> parts = upstream.decode(data, "c82b96f821e2");
        assertEquals(2, parts.size());
        assertInstanceOf(UpstreamPart.FunctionReply.class, parts.get(0));
        assertInstanceOf(UpstreamPart.PropertyReport.class, parts.get(1));
        UpstreamPart.FunctionReply reply = (UpstreamPart.FunctionReply) parts.get(0);
        assertEquals("1234567890", reply.messageId());
    }

    @Test
    void controllerEventWithProperties() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "c82b96f821e2");
        data.put("commandName", "controller-event");
        data.put("key", 1);
        data.put("onState", 0);

        List<UpstreamPart> parts = upstream.decode(data, "c82b96f821e2");
        assertEquals(2, parts.size());
        assertInstanceOf(UpstreamPart.Event.class, parts.get(0));
        assertInstanceOf(UpstreamPart.PropertyReport.class, parts.get(1));
    }

    @Test
    void powerControlDownstreamUsesIntegerKey() {
        DownstreamEncoder.EncodedDownstream encoded = downstream.encodeFunction(
            "powerControl",
            Map.of("key", 1),
            "999"
        );
        String json = new String(encoded.payload());
        assertTrue(json.contains("\"key\":1"));
        assertTrue(json.contains("\"messageId\":\"999\""));
        assertTrue(json.contains("\"type\":\"event\""));
    }

    @Test
    void writeSwitchStateDownstream() {
        DownstreamEncoder.EncodedDownstream encoded = downstream.encodeWriteProperty(
            "switchState",
            0,
            "888"
        );
        String json = new String(encoded.payload());
        assertTrue(json.contains("\"key\":0"));
        assertTrue(json.contains("\"type\":\"event\""));
    }


    @Test
    void readPropertyDownstreamUsesInfoByDefault() {
        DownstreamEncoder.EncodedDownstream encoded = downstream.encodeReadProperty(List.of("switchState"), "777");
        String json = new String(encoded.payload());
        assertTrue(json.contains("\"type\":\"info\""));
        assertTrue(json.contains("\"messageId\":\"777\""));
    }

    @Test
    void readPropertyDownstreamUsesStatisticForFloat() {
        DownstreamEncoder.EncodedDownstream encoded = downstream.encodeReadProperty(List.of("current"), "666");
        String json = new String(encoded.payload());
        assertTrue(json.contains("\"type\":\"statistic\""));
    }

    @Test
    void parseUpstreamTopic() {
        var info = TopicPaths.parseUpstreamTopic("/gspm1b/c82b96f821e2/up").orElseThrow();
        assertEquals("gspm1b", info.productId());
        assertEquals("c82b96f821e2", info.deviceId());
    }
}
