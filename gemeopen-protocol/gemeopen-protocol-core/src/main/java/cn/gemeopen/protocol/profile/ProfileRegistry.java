package cn.gemeopen.protocol.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ProfileRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProfileRegistry.class);
    private static final String ENV_PROFILES_PATH = "GEMEOPEN_PROFILES_PATH";
    private static final String PROP_PROFILES_PATH = "gemeopen.profiles.path";

    private final ClassLoader classLoader;
    private final Map<String, ProductProfile> cache = new ConcurrentHashMap<>();
    private final Path externalDir;

    public ProfileRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.externalDir = resolveExternalDir();
        if (externalDir != null) {
            log.info("GemeOpen profile external directory: {}", externalDir.toAbsolutePath());
        }
    }

    public ProductProfile getRequired(String productId) {
        return cache.computeIfAbsent(productId, this::loadUnchecked);
    }

    public void register(ProductProfile profile) {
        cache.put(profile.getProductId(), profile);
    }

    public void clearCache() {
        cache.clear();
        log.info("GemeOpen profile cache cleared");
    }

    public Stream<String> listAvailableProfiles() {
        if (externalDir == null || !Files.isDirectory(externalDir)) {
            return Stream.empty();
        }
        try {
            return Files.list(externalDir)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString())
                .map(n -> n.substring(0, n.length() - 5));
        } catch (IOException e) {
            log.warn("Failed to list external GemeOpen profiles", e);
            return Stream.empty();
        }
    }

    private ProductProfile loadUnchecked(String productId) {
        if (externalDir != null) {
            Path externalFile = externalDir.resolve(productId + ".json");
            if (Files.isRegularFile(externalFile)) {
                try {
                    ProductProfile profile = ProfileLoader.loadPath(externalFile);
                    log.info("Loaded GemeOpen profile from external dir: productId={}", productId);
                    return profile;
                } catch (IOException e) {
                    log.warn("Failed to load external profile {}: {}", productId, e.getMessage());
                }
            }
        }
        try {
            ProductProfile profile = ProfileLoader.loadClasspath(classLoader, productId);
            log.info("Loaded GemeOpen profile from classpath: productId={}", productId);
            return profile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load profile: " + productId, e);
        }
    }

    private static Path resolveExternalDir() {
        String path = System.getProperty(PROP_PROFILES_PATH);
        if (path == null || path.isBlank()) {
            path = System.getenv(ENV_PROFILES_PATH);
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        Path dir = Paths.get(path);
        if (!Files.isDirectory(dir)) {
            log.warn("Configured GemeOpen profile directory is missing: {}", dir.toAbsolutePath());
            return null;
        }
        return dir;
    }
}
