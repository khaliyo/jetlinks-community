package cn.gemeopen.protocol.profile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProfileLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProfileLoader() {
    }

    public static ProductProfile load(InputStream in) throws IOException {
        return MAPPER.readValue(in, ProductProfile.class);
    }

    public static ProductProfile loadPath(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        }
    }

    public static ProductProfile loadClasspath(ClassLoader loader, String productId) throws IOException {
        String path = "gemeopen/profiles/" + productId + ".json";
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Profile not found on classpath: " + path);
            }
            return load(in);
        }
    }
}
