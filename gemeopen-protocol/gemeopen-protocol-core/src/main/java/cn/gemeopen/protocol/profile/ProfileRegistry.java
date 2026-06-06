package cn.gemeopen.protocol.profile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileRegistry {

    private final ClassLoader classLoader;
    private final Map<String, ProductProfile> cache = new ConcurrentHashMap<>();

    public ProfileRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ProductProfile getRequired(String productId) {
        return cache.computeIfAbsent(productId, this::loadUnchecked);
    }

    public void register(ProductProfile profile) {
        cache.put(profile.getProductId(), profile);
    }

    private ProductProfile loadUnchecked(String productId) {
        try {
            return ProfileLoader.loadClasspath(classLoader, productId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load profile: " + productId, e);
        }
    }
}
