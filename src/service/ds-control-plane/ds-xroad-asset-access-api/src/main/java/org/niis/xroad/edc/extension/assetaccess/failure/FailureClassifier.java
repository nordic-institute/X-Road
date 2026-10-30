/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.assetaccess.failure;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.response.StatusResult;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Maps an acquisition stage failure to a {@link FailureKind}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FailureClassifier {

    private static final int NO_RESPONSE_STATUS = 0;
    private static final Pattern PROVIDER_4XX_TEXT =
            Pattern.compile("Counter Party responded with Response\\{protocol=[^,]*, code=4\\d\\d,");
    private static final String HTTP_CLIENT_EXCEPTION_TEXT_PREFIX = EdcHttpClientException.class.getName() + ": ";
    private static final String HTTP_STATUS_TEXT_PREFIX = "Server response to [";
    private static final List<String> NEGOTIATION_ERROR_PREFIXES = List.of(
            "Failed to request contract to provider: ",
            "Failed to send verification to provider: ",
            "Failed to send termination to counter party: ");

    /**
     * A failed future from an EDC protocol request. The future fails with an {@link EdcHttpClientException}: status 0
     * when the provider gave no response, or the provider's 5xx status once retries end. EDC drops the underlying
     * {@code IOException}, so only the status tells them apart. A 4xx answer never fails the future: EDC returns it as a
     * failed {@link StatusResult}, classified by {@link #classify(StatusResult)}. A stage {@link TimeoutException}
     * anywhere in the cause chain is a transport fault. Local EDC failures and anything else are {@link FailureKind#OTHER}.
     */
    public static FailureKind classify(Throwable failure) {
        var httpFailure = ExceptionUtils.throwableOfType(failure, EdcHttpClientException.class);
        if (httpFailure != null) {
            return httpFailure.getStatusCode() == NO_RESPONSE_STATUS ? FailureKind.TRANSPORT_FAULT : FailureKind.OTHER;
        }
        return ExceptionUtils.indexOfType(failure, TimeoutException.class) >= 0 ? FailureKind.TRANSPORT_FAULT : FailureKind.OTHER;
    }

    /**
     * A failed {@link StatusResult}; it expects a failed result. EDC's DSP dispatcher turns only a 4xx answer into a
     * failed {@link StatusResult}, as a fatal error.
     */
    public static FailureKind classify(StatusResult<?> result) {
        return result.fatalError() ? FailureKind.PROVIDER_REJECTION : FailureKind.OTHER;
    }

    /**
     * A terminated or terminating consumer-side negotiation. EDC keeps only an error text, and the ids of the protocol
     * messages received from the provider. The error text, once EDC's consumer prefix is removed, decides first, because
     * a failed verification send also has received messages. Without an error text, a received message means the
     * provider terminated after acknowledging: a provider rejection. Otherwise {@link FailureKind#OTHER}.
     */
    public static FailureKind classify(ContractNegotiation negotiation) {
        var errorText = withoutNegotiationPrefix(negotiation.getErrorDetail());
        if (errorText != null) {
            return classifySendText(errorText);
        }
        return hasReceivedMessages(negotiation.getProtocolMessages()) ? FailureKind.PROVIDER_REJECTION : FailureKind.OTHER;
    }

    /**
     * A terminated or terminating consumer-side transfer process. A received message means the provider terminated it:
     * a provider rejection. Otherwise EDC's raw error text decides.
     */
    public static FailureKind classify(TransferProcess transferProcess) {
        if (hasReceivedMessages(transferProcess.getProtocolMessages())) {
            return FailureKind.PROVIDER_REJECTION;
        }
        return classifySendText(transferProcess.getErrorDetail());
    }

    private static boolean hasReceivedMessages(@Nullable ProtocolMessages protocolMessages) {
        return protocolMessages != null && !protocolMessages.getReceived().isEmpty();
    }

    @Nullable
    private static String withoutNegotiationPrefix(@Nullable String errorDetail) {
        if (errorDetail == null) {
            return null;
        }
        return NEGOTIATION_ERROR_PREFIXES.stream()
                .filter(errorDetail::startsWith)
                .findFirst()
                .map(prefix -> errorDetail.substring(prefix.length()))
                .orElse(errorDetail);
    }

    /**
     * The stored text of a failed send. The 4xx and 5xx texts embed the provider's response body, so every match is
     * anchored at the start of the text.
     */
    private static FailureKind classifySendText(@Nullable String text) {
        if (text == null) {
            return FailureKind.OTHER;
        }
        if (PROVIDER_4XX_TEXT.matcher(text).lookingAt()) {
            return FailureKind.PROVIDER_REJECTION;
        }
        return isHttpClientFailureWithoutStatus(text) ? FailureKind.TRANSPORT_FAULT : FailureKind.OTHER;
    }

    private static boolean isHttpClientFailureWithoutStatus(String text) {
        return text.startsWith(HTTP_CLIENT_EXCEPTION_TEXT_PREFIX)
                && !text.startsWith(HTTP_STATUS_TEXT_PREFIX, HTTP_CLIENT_EXCEPTION_TEXT_PREFIX.length());
    }
}
