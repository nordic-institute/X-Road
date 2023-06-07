/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

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

    public static final int READ_TIMEOUT = 30000;

    /**
     * @return the input stream acquired by connecting to the download url.
     * @throws Exception if an error occurs
     */
    public InputStream getInputStream() throws Exception {
        try {
            URLConnection connection = getDownloadURLConnection(downloadURL);
            return connection.getInputStream();
        } catch (IOException e) {
            throw new CodedException(X_HTTP_ERROR, e);
        }
    }

    /**
     * @return the connection used to connect to the download url
     * @throws IOException
     */
    public static URLConnection getDownloadURLConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        URLConnection connection = url.openConnection();
        connection.setReadTimeout(READ_TIMEOUT);
        return connection;
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

    /**
     * @return Instance identifier of this configuration location
     */
    public String getInstanceIdentifier() {
        return source.getInstanceIdentifier();
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

    @Override
    public String toString() {
        return downloadURL;
    }
}
