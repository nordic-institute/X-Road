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

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises a provider refusal of a transfer in the free-text failure detail the consumer's EDC records.
 *
 * <p>The consumer receives the provider's HTTP answer as one sentence, {@code Counter Party responded with
 * Response{protocol=.., code=NNN, ..}. Body: ..}, inside the transfer failure detail. A provider refusal is
 * a 409, or a 404 whose body says {@value #AGREEMENT_NOT_VALID}: the provider no longer holds the agreement
 * valid. A bare 404 (the provider could not verify the request token), a 400 and every other status are not
 * refusals.
 *
 * <p>{@link #isRefusal} returning {@code false} says nothing more than "not a refusal". A detail without a
 * provider sentence is not the same as a failed connection: provider-side termination reasons, consumer-local
 * failures and connection failures all carry no such sentence and cannot be told apart by their text.
 */
public final class ProviderFailureClassifier {

    /** The wording EDC puts in the 404 body of a transfer request naming an agreement it does not hold valid. */
    public static final String AGREEMENT_NOT_VALID = "agreement not found or not valid";

    private static final int CONFLICT = 409;
    private static final int NOT_FOUND = 404;
    private static final Pattern PROVIDER_STATUS =
            Pattern.compile("Counter Party responded with Response\\{[^}]*?\\bcode=(\\d{3})\\b");

    private ProviderFailureClassifier() {
    }

    /**
     * Tells whether the failure detail describes a provider refusal of the agreement.
     *
     * @param failureDetail the transfer failure detail, may be {@code null}
     * @return {@code true} for a 409, or a 404 carrying {@value #AGREEMENT_NOT_VALID}
     */
    public static boolean isRefusal(String failureDetail) {
        if (failureDetail == null) {
            return false;
        }
        var status = PROVIDER_STATUS.matcher(failureDetail);
        if (!status.find()) {
            return false;
        }
        var code = Integer.parseInt(status.group(1));
        return code == CONFLICT
                || code == NOT_FOUND && failureDetail.toLowerCase(Locale.ROOT).contains(AGREEMENT_NOT_VALID);
    }
}
