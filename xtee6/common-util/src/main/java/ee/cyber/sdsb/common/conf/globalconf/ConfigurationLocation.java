package ee.cyber.sdsb.common.conf.globalconf;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.builder.HashCodeBuilder;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_HTTP_ERROR;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

/**
 * Describes a configuration location where configuration can be downloaded.
 */
@RequiredArgsConstructor
@Getter
@Slf4j
public class ConfigurationLocation {

    private final ConfigurationSource source;

    private final String downloadURL;

    private final List<byte[]> verificationCerts;

    /**
     * @return the input stream acquired by connecting to the download url.
     * @throws Exception if an error occurs
     */
    public InputStream getInputStream() throws Exception {
        try {
            return new URL(downloadURL).openStream();
        } catch (IOException e) {
            throw new CodedException(X_HTTP_ERROR, e);
        }
    }

    /**
     * @param certHashBase64 the base64 encoded certificate hash
     * @param hashAlgoId the hash algorithm id
     * @return verification certificate for a given certificate hash or null
     * if not found
     */
    public X509Certificate getVerificationCert(String certHashBase64,
            String hashAlgoId) {
        byte[] certHash = decodeBase64(certHashBase64);
        for (byte[] certBytes : verificationCerts) {
            try {
                log.trace("Calculating certificate hash using algorithm {}",
                        hashAlgoId);

                if (Arrays.equals(certHash, hash(hashAlgoId, certBytes))) {
                    return readCertificate(certBytes);
                }
            } catch (Exception e) {
                log.error("Failed to calculate certificate hash using "
                        + "algorithm identifier " + hashAlgoId, e);
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ConfigurationLocation)) {
            return false;
        }

        ConfigurationLocation that = (ConfigurationLocation) obj;
        if (!this.getDownloadURL().equals(that.getDownloadURL())) {
            return false;
        }

        List<byte[]> thisCerts = this.verificationCerts;
        List<byte[]> thatCerts = that.verificationCerts;
        if (thisCerts.size() != thatCerts.size()) {
            return false;
        }

        for (int i = 0; i < thisCerts.size(); i++) {
            if (!Arrays.equals(thisCerts.get(i), thatCerts.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    private static byte[] hash(String hashAlgoId, byte[] data)
            throws Exception {
        return calculateDigest(getAlgorithmId(hashAlgoId), data);
    }
}
