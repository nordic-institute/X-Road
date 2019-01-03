package org.niis.xroad.authproto.repository;

import org.niis.xroad.authproto.domain.ApiKey;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dummy in-memory repository for api keys
 */
@Component
public class ApiKeyRepository {

    AtomicInteger apiKeyIndex = new AtomicInteger(1);
    Map<String, ApiKey> keys = new ConcurrentHashMap<>();

    /**
     * Naive key implementation
     * @return
     */
    private String createApiKey() {
        return "naive-api-key-" + apiKeyIndex.getAndAdd(1);
    }

    /**
     * create api key with one role
     */
    public ApiKey create(String role) {
        return create(Collections.singletonList(role));
    }

    /**
     * create api key with collection of roles
     */
    public ApiKey create(Collection<String> roles) {
        ApiKey key = new ApiKey(createApiKey(),
                Collections.unmodifiableCollection(roles));
        keys.put(key.getKey(), key);
        return key;
    }

    public ApiKey get(String key) {
        return keys.get(key);
    }

}
