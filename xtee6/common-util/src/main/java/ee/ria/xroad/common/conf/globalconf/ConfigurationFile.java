/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.http.HttpFields;
import org.joda.time.DateTime;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.MimeUtils.*;

final class ConfigurationFile extends AbstractConfigurationPart {

    private final ContentIdentifier contentIdentifier;

    @Getter
    private final DateTime expirationDate;

    @Getter
    private final String hash;

    private ConfigurationFile(Map<String, String> parameters,
            ContentIdentifier contentIdentifier, DateTime expirationDate,
            String hash) {
        super(parameters);

        this.contentIdentifier = contentIdentifier;
        this.expirationDate = expirationDate;
        this.hash = hash;
    }

    String getContentLocation() {
        return parameters.get(HEADER_CONTENT_LOCATION);
    }

    String getHashAlgorithmId() {
        return parameters.get(HEADER_HASH_ALGORITHM_ID);
    }

    String getContentFileName() {
        return parameters.get(HEADER_CONTENT_FILE_NAME);
    }

    String getContentIdentifier() {
        return contentIdentifier.getIdentifier();
    }

    String getInstanceIdentifier() {
        return contentIdentifier.getInstance();
    }

    @Override
    public String toString() {
        return !StringUtils.isBlank(getContentIdentifier())
                ? getContentIdentifier() : getContentLocation();
    }

    ConfigurationPartMetadata getMetadata() {
        ConfigurationPartMetadata metadata = new ConfigurationPartMetadata();
        metadata.setContentIdentifier(getContentIdentifier());
        metadata.setInstanceIdentifier(getInstanceIdentifier());
        metadata.setExpirationDate(getExpirationDate());
        metadata.setContentFileName(getContentFileName());
        metadata.setContentLocation(getContentLocation());
        return metadata;
    }

    static ConfigurationFile of(Map<String, String> headers,
            DateTime expirationDate, String hash) {
        if (headers == null) {
            throw new IllegalArgumentException("headers must not be null");
        }

        if (expirationDate == null) {
            throw new IllegalArgumentException(
                    "expirationDate must not be null");
        }

        Map<String, String> h = new HashMap<>(headers);

        verifyFieldExists(h, HEADER_CONTENT_TYPE, "application/octet-stream");
        verifyFieldExists(h, HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        verifyFieldExists(h, HEADER_CONTENT_LOCATION);
        verifyFieldExists(h, HEADER_HASH_ALGORITHM_ID);

        return new ConfigurationFile(headers,
                getContentIdentififer(h.get(HEADER_CONTENT_IDENTIFIER)),
                expirationDate, hash);
    }

    private static ContentIdentifier getContentIdentififer(String value) {
        if (StringUtils.isBlank(value)) {
            return new ContentIdentifier("", "");
        }

        Map<String, String> p = new HashMap<>();

        String id = HttpFields.valueParameters(value, p);
        String instance = p.get(PARAM_INSTANCE);

        if ((PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS.equals(id)
                || SharedParameters.CONTENT_ID_SHARED_PARAMETERS.equals(id))
                && StringUtils.isBlank(instance)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Field " + HEADER_CONTENT_IDENTIFIER
                        + " is missing parameter " + PARAM_INSTANCE);
        }

        return new ContentIdentifier(id, instance);
    }

    @Data
    private static class ContentIdentifier {
        private final String identifier;
        private final String instance;
    }
}
