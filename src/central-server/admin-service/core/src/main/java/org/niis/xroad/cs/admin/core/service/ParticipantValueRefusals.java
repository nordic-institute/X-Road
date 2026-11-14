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
package org.niis.xroad.cs.admin.core.service;

import lombok.experimental.UtilityClass;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.function.Supplier;

/**
 * Turns the refusal of an administrator's replacement of a published value into a bad request.
 */
@UtilityClass
class ParticipantValueRefusals {

    /**
     * Runs a replacement and refuses it when the stored value is rejected or another server holds the DID.
     *
     * @param replacement         the replacement in the value store
     * @param exclusionConstraint the database constraint that stops two servers from holding the same DID
     * @throws BadRequestException when the replacement is refused
     */
    static void replaceOrRefuse(Supplier<ParticipantValueResult> replacement, String exclusionConstraint) {
        ParticipantValueResult result;
        try {
            result = replacement.get();
        } catch (DataIntegrityViolationException e) {
            if (!ConstraintViolations.violates(e, exclusionConstraint)) {
                throw e;
            }
            throw new BadRequestException(ErrorMessage.DID_HELD_BY_ANOTHER_SERVER.build());
        }
        if (result.outcome() == ParticipantValueResult.Outcome.REJECTED) {
            throw new BadRequestException(errorOf(result.rejection()).build());
        }
    }

    static ErrorMessage errorOf(ParticipantValueRejection rejection) {
        return switch (rejection) {
            case NO_STORED_VALUE -> ErrorMessage.DID_NOT_STORED;
            case WRONG_SCHEME -> ErrorMessage.DID_WRONG_SCHEME;
            case DID_TOO_LONG -> ErrorMessage.DID_TOO_LONG;
            case NOT_CANONICAL -> ErrorMessage.DID_NOT_CANONICAL;
            case HOST_DIFFERS -> ErrorMessage.DID_HOST_DIFFERS;
            case NON_ASCII_HOST -> ErrorMessage.DID_NON_ASCII_HOST;
            case UNPARSEABLE_REGISTERED_ADDRESS -> ErrorMessage.DID_UNPARSEABLE_SERVER_ADDRESS;
            case UNDECODABLE_DID -> ErrorMessage.DID_UNDECODABLE;
            case FOREIGN_SYSTEM_DID -> ErrorMessage.DID_FOREIGN_SYSTEM;
            case DID_OF_OTHER_MEMBER -> ErrorMessage.DID_OF_OTHER_MEMBER;
            case MEMBER_NOT_SERVED -> ErrorMessage.DID_MEMBER_NOT_SERVED;
            case DID_HELD_BY_ANOTHER_SERVER -> ErrorMessage.DID_HELD_BY_ANOTHER_SERVER;
            case DSP_BASE_URL_NOT_HTTPS -> ErrorMessage.DSP_BASE_URL_NOT_HTTPS;
            case DSP_BASE_URL_MALFORMED -> ErrorMessage.DSP_BASE_URL_MALFORMED;
            case DSP_BASE_URL_NOT_CANONICAL -> ErrorMessage.DSP_BASE_URL_NOT_CANONICAL;
            case DSP_BASE_URL_HOST_DIFFERS -> ErrorMessage.DSP_BASE_URL_HOST_DIFFERS;
            case DSP_BASE_URL_NON_ASCII_HOST -> ErrorMessage.DSP_BASE_URL_NON_ASCII_HOST;
            case VALUE_DIFFERS ->
                    throw new IllegalStateException("A replacement cannot be refused as " + rejection);
        };
    }
}
