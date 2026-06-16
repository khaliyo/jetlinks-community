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

class Gscw1m2pCodecTest {

    private static ProductProfile profile;
    private static UpstreamDecoder upstream;
    private static DownstreamEncoder downstream;

    @BeforeAll
    static void loadProfile() throws Exception {
        try (InputStream in = Gscw1m2pCodecTest.class.getClassLoader()
            .getResourceAsStream("gemeopen/profiles/gscw1m2p.json")) {
            profile = ProfileLoader.load(in);
        }
        upstream = new UpstreamDecoder(profile);
        downstream = new DownstreamEncoder(profile);
    }

    @Test
    void writeSwitchState1Downstream() {
        DownstreamEncoder.EncodedDownstream encoded = downstream.encodeWriteProperty(
            "switchState1",
            1,
            "100"
        );
        String json = new String(encoded.payload());
        assertTrue(json.contains("\"key1\":1"));
    }

    @Test
    void dualChannelPropertyReport() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mac", "aa1122334455");
        data.put("key1", 1);
        data.put("key2", 0);
        List<UpstreamPart> parts = upstream.decode(data, "aa1122334455");
        assertEquals(1, parts.size());
        UpstreamPart.PropertyReport report = (UpstreamPart.PropertyReport) parts.get(0);
        assertEquals("1", report.properties().get("switchState1"));
        assertEquals("0", report.properties().get("switchState2"));
    }
}
