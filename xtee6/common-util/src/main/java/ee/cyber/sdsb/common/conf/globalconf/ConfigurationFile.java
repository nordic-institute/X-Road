package ee.cyber.sdsb.common.conf.globalconf;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.Getter;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.http.HttpFields;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.util.MimeUtils.*;

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

        if (PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS.equals(id)
                || SharedParameters.CONTENT_ID_SHARED_PARAMETERS.equals(id)) {
            if (StringUtils.isBlank(instance)) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Field " + HEADER_CONTENT_IDENTIFIER
                            + " is missing parameter " + PARAM_INSTANCE);
            }
        }

        return new ContentIdentifier(id, instance);
    }

    @Data
    private static class ContentIdentifier {
        private final String identifier;
        private final String instance;
    }
}
