package cn.gemeopen.protocol.codec;

import cn.gemeopen.protocol.profile.ProductProfile;
import cn.gemeopen.protocol.profile.ProfileLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProductProfilesCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void profilesOnClasspath() throws Exception {
        for (String productId : List.of("gspw1b2", "gscu1b", "gssm0b")) {
            try (InputStream in = MultiProductProfilesCodecTest.class.getClassLoader()
                .getResourceAsStream("gemeopen/profiles/" + productId + ".json")) {
                assertNotNull(in, "profile missing: " + productId);
                ProductProfile profile = ProfileLoader.load(in);
                assertEquals(productId, profile.getProductId());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gspw1b2", "gscu1b", "gssm0b"})
    void profileLoadsWithNestedDownstreamFields(String productId) throws Exception {
        ProductProfile profile = loadProfile(productId);
        assertFalse(profile.getFunctions().isEmpty());
        assertFalse(profile.getPropertyMapping().isEmpty());
    }

    @Test
    void gscu1bEmitInfraredNestedData() throws Exception {
        DownstreamEncoder encoder = new DownstreamEncoder(loadProfile("gscu1b"));
        String json = new String(encoder.encodeFunction("emitInfraredCode", Map.of("no", 1), "mid-ir").payload(),
            StandardCharsets.UTF_8);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("infrared", node.get("type").asText());
        assertEquals("emit", node.get("action").asText());
        assertEquals(1, node.get("data").get("no").asInt());
        assertEquals("mid-ir", node.get("messageId").asText());
    }

    @Test
    void gspw1b2SetCountdownNestedFinishCommand() throws Exception {
        DownstreamEncoder encoder = new DownstreamEncoder(loadProfile("gspw1b2"));
        String json = new String(encoder.encodeFunction(
            "setCountdown",
            Map.of("countdownSecond", 60, "finishCommandKey", 1, "timerInterval", 30),
            "mid-cd"
        ).payload(), StandardCharsets.UTF_8);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("setting", node.get("type").asText());
        assertEquals("countdown", node.get("task").asText());
        assertEquals(1, node.get("finishCommand").get("key").asInt());
        assertEquals("event", node.get("finishCommand").get("type").asText());
    }

    @Test
    void gspw1b2DotPathPropertyMapping() throws Exception {
        UpstreamDecoder decoder = new UpstreamDecoder(loadProfile("gspw1b2"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "aa1122334455");
        data.put("command", Map.of("key", 2));
        List<UpstreamPart> parts = decoder.decode(data, "aa1122334455");
        assertEquals(1, parts.size());
        UpstreamPart.PropertyReport report = (UpstreamPart.PropertyReport) parts.get(0);
        assertEquals("2", report.properties().get("timerTaskCommandKey"));
    }

    @Test
    void gssm0bStringEnumPlayerMode() throws Exception {
        UpstreamDecoder decoder = new UpstreamDecoder(loadProfile("gssm0b"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "bb1122334455");
        data.put("player-mode", "OnePlay");
        data.put("vol", 8);
        List<UpstreamPart> parts = decoder.decode(data, "bb1122334455");
        assertEquals(1, parts.size());
        UpstreamPart.PropertyReport report = (UpstreamPart.PropertyReport) parts.get(0);
        assertEquals("OnePlay", report.properties().get("playerMode"));
        assertEquals(8, report.properties().get("vol"));
    }

    @Test
    void gssm0bPlayUsesMessageIdPlaceholder() throws Exception {
        DownstreamEncoder encoder = new DownstreamEncoder(loadProfile("gssm0b"));
        String json = new String(encoder.encodeFunction("play", Map.of(), "mid-play").payload(), StandardCharsets.UTF_8);
        JsonNode node = MAPPER.readTree(json);
        assertEquals("player-play", node.get("action").asText());
        assertEquals("mid-play", node.get("messageId").asText());
        assertTrue(json.contains("\"type\":\"event\""));
    }

    private static ProductProfile loadProfile(String productId) throws Exception {
        try (InputStream in = MultiProductProfilesCodecTest.class.getClassLoader()
            .getResourceAsStream("gemeopen/profiles/" + productId + ".json")) {
            return ProfileLoader.load(in);
        }
    }
}
