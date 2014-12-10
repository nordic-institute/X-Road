package ee.cyber.sdsb.common.conf.globalconf;

import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.apache.commons.lang.StringUtils;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_TRANSFER_ENCODING;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_TYPE;

@RequiredArgsConstructor
abstract class AbstractPartParameters {

    @Getter
    protected final Map<String, String> parameters;

    public String getContentType() {
        return parameters.get(HEADER_CONTENT_TYPE);
    }

    public String getContentTransferEncoding() {
        return parameters.get(HEADER_CONTENT_TRANSFER_ENCODING);
    }

    static final void verifyFieldExists(Map<String, String> headers,
            String fieldName) {
        verifyFieldExists(headers, fieldName, null);
    }

    static final void verifyFieldExists(Map<String, String> headers,
            String fieldName, String expectedValue) {
        String value = headers.get(fieldName);
        if (StringUtils.isBlank(value)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Missing field " + fieldName);
        }

        if (expectedValue != null && !expectedValue.equals(value)) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Field " + fieldName + " must have value " + expectedValue);
        }
    }
}
