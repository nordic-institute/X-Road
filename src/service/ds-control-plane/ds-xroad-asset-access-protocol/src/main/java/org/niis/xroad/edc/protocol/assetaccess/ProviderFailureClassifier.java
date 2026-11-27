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

package org.niis.xroad.edc.protocol.assetaccess;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises a provider refusal, or a provider rejection of the consumer's DCP identity, in the free-text
 * failure detail the consumer's EDC records for a catalog request, a contract negotiation or a transfer.
 *
 * <p>The consumer receives the provider's HTTP answer as one sentence, {@code Counter Party responded with
 * Response{protocol=.., code=NNN, ..}. Body: ..}, inside the failure detail. An agreement refusal is a 409,
 * or a 400 or 404 whose body says {@value #AGREEMENT_NOT_VALID}: the provider no longer holds the agreement
 * valid. For a contract negotiation or a transfer, a bare 404, without that wording, is the provider rejecting
 * a request token it could not verify: an identity rejection. A catalog request never answers an unverifiable
 * token with a 404; there, only a 401, with any body, is an identity rejection, and a 404 is neither (a wrong
 * DSP path or version, or a proxy). A bare 400 (for example an unsupported transfer type) and every other
 * status are neither.
 *
 * <p>{@link #classify} returning {@link Classification#NO_REFUSAL} says nothing more than "neither". A
 * detail without a provider sentence is not the same as a failed connection: provider-side termination
 * reasons, consumer-local failures and connection failures all carry no such sentence and cannot be told
 * apart by their text.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProviderFailureClassifier {

    /** The wording EDC puts in the body of an answer naming an agreement it does not hold valid. */
    public static final String AGREEMENT_NOT_VALID = "agreement not found or not valid";

    private static final int UNAUTHORIZED = 401;
    private static final int CONFLICT = 409;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final Pattern PROVIDER_STATUS =
            Pattern.compile("Counter Party responded with Response\\{[^}]*?\\bcode=(\\d{3})\\b");

    /** What a failure detail says about the provider's answer. */
    public enum Classification {
        /** Neither an agreement refusal nor an identity rejection. */
        NO_REFUSAL,
        /** A 409, or a 400 or 404 carrying {@value #AGREEMENT_NOT_VALID}: the agreement is no longer valid. */
        AGREEMENT_REFUSAL,
        /**
         * A bare 404 on a contract negotiation or a transfer, or a 401 on a catalog request: the provider
         * could not verify the consumer's DCP identity.
         */
        IDENTITY_REJECTION
    }

    /** The stage of the asset access acquisition that produced the failure detail. */
    public enum Stage {
        CATALOG, NEGOTIATION, TRANSFER
    }

    /**
     * Classifies the provider's answer from the failure detail of a catalog request, a contract negotiation
     * or a transfer.
     *
     * @param failureDetail the failure detail, may be {@code null}
     * @param stage the stage of the acquisition the failure detail comes from
     * @return the classification of the provider's answer
     */
    public static Classification classify(String failureDetail, Stage stage) {
        if (failureDetail == null) {
            return Classification.NO_REFUSAL;
        }
        var status = PROVIDER_STATUS.matcher(failureDetail);
        if (!status.find()) {
            return Classification.NO_REFUSAL;
        }
        var code = Integer.parseInt(status.group(1));
        if (stage == Stage.CATALOG) {
            return code == UNAUTHORIZED ? Classification.IDENTITY_REJECTION : Classification.NO_REFUSAL;
        }
        var namesInvalidAgreement = failureDetail.toLowerCase(Locale.ROOT).contains(AGREEMENT_NOT_VALID);
        if (code == CONFLICT || ((code == BAD_REQUEST || code == NOT_FOUND) && namesInvalidAgreement)) {
            return Classification.AGREEMENT_REFUSAL;
        }
        if (code == NOT_FOUND || code == UNAUTHORIZED) {
            return Classification.IDENTITY_REJECTION;
        }
        return Classification.NO_REFUSAL;
    }
}
