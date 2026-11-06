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
package org.niis.xroad.securityserver.restapi.service;

import com.apicatalog.did.Did;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The outcome of minting a participant DID: the DID, or the reason this server's registered address
 * cannot carry one. A rejection is a normal outcome, reported rather than thrown.
 */
public sealed interface DidMintResult {

    /** Why no DID can be minted. */
    enum Reason {
        /** The registered address contains a non-ASCII character. */
        NON_ASCII_HOST,
        /** The registered address is not a host that {@code did:web} and an HTTPS URL can carry. */
        INVALID_HOST,
        /** The DID would be longer than {@code ParticipantIdentifierScheme.MAX_DID_LENGTH}. */
        DID_TOO_LONG
    }

    /**
     * @param value the minted DID
     */
    record Minted(Did value) implements DidMintResult {
    }

    /**
     * @param reason the category of the rejection
     * @param detail a human-readable explanation naming the offending value or its length
     * @param cause  the exception that caused the rejection, or {@code null} when there is none
     */
    record Rejected(Reason reason, String detail, Throwable cause) implements DidMintResult {

        Rejected(Reason reason, String detail) {
            this(reason, detail, null);
        }

        /**
         * @return a {@code VALIDATION_ERROR} exception carrying the detail and the original cause
         */
        public XrdRuntimeException toException() {
            return XrdRuntimeException.systemException(VALIDATION_ERROR, cause, "%s", detail);
        }
    }

    /**
     * @return the minted DID, or empty when the minting was rejected
     */
    default Optional<Did> did() {
        return this instanceof Minted(var value) ? Optional.of(value) : Optional.empty();
    }
}
