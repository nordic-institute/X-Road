package ee.cyber.sdsb.common.ocsp;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;

/**
 * Holds OCSP response per key. When getting the response, it is checked
 * if the response is expired at the specified date, and if it is, the
 * response is removed from the cache and null is returned.
 */
@Slf4j
public class OcspCache {

    protected final Map<String, OCSPResp> cache = new ConcurrentHashMap<>();

    /**
     * @param key the key
     * @param atDate the date
     * @return the OCSP response or null if the response is not found or is
     * expired at the specified date
     */
    public OCSPResp get(Object key, Date atDate) {
        return getResponse(key, atDate);
    }

    /**
     * @param key the key
     * @return the OCSP response or null if the response is not found or is
     * expired at the current date
     */
    public OCSPResp get(Object key) {
        return getResponse(key, new Date());
    }

    /**
     * Associates a key with the OCSP response.
     * @param key the key
     * @param value the OCSP response
     * @return the OCSP response
     */
    public OCSPResp put(String key, OCSPResp value) {
        log.trace("Setting OCSP response for '{}'", key);
        return cache.put(key, value);
    }

    /**
     * Removes all OCSP responses from the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * @return a Set view of the mappings contained in this map.
     */
    public Set<Entry<String, OCSPResp>> entrySet() {
        return cache.entrySet();
    }

    protected OCSPResp getResponse(Object key, Date atDate) {
        log.trace("Retrieving OCSP response for certificate '{}' at {}", key,
                atDate);

        OCSPResp cachedResponse = cache.get(key);
        try {
            if (cachedResponse != null && isExpired(cachedResponse, atDate)) {
                log.trace("Cached OCSP response for certificate "
                        + "'{}' has expired", key);
                cache.remove(key);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to check if OCSP response is expired", e);
            cache.remove(key);
            return null;
        }

        return cachedResponse;
    }

    protected static boolean isExpired(OCSPResp response, Date atDate)
            throws Exception {
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(true));
        return verifier.isExpired(response, atDate);
    }
}
