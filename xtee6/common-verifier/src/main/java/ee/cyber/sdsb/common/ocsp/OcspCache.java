package ee.cyber.sdsb.common.ocsp;

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import static ee.cyber.sdsb.common.ocsp.OcspVerifier.isExpired;

@Slf4j
public class OcspCache extends ConcurrentHashMap<String, OCSPResp> {

    @Override
    public OCSPResp get(Object key) {
        log.trace("Retrieving OCSP response for certificate '{}'", key);

        OCSPResp cachedResponse = super.get(key);
        try {
            if (cachedResponse != null && isExpired(cachedResponse)) {
                log.trace("Cached OCSP response for certificate " +
                        "'{}' has expired", key);
                remove(key);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to check if OCSP response is expired", e);
            remove(key);
            return null;
        }

        return cachedResponse;
    }

    @Override
    public OCSPResp put(String key, OCSPResp value) {
        log.trace("Setting OCSP response for '{}'", key);
        return super.put(key, value);
    }
}
