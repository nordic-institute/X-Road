/*
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
package org.niis.xroad.edc.extension.rpc;

import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;

/**
 * Shared helpers for EDC provisioning gRPC service handlers.
 */
public final class EdcProvisioningHelper {

    private EdcProvisioningHelper() {
    }

    /**
     * Succeeds silently if the result succeeded or failed with CONFLICT (idempotency contract).
     * Any other failure reason throws an {@link XrdRuntimeException}.
     */
    public static void requireSuccessOrConflict(ServiceResult<?> result, ErrorCode errorCode, String metadata) {
        if (result.succeeded() || result.reason() == ServiceFailure.Reason.CONFLICT) {
            return;
        }
        throw failure(errorCode, metadata, result.getFailureDetail());
    }

    /**
     * Builds an {@link XrdRuntimeException} for a dataspace provisioning failure.
     */
    public static XrdRuntimeException failure(ErrorCode errorCode, String metadata, String detail) {
        return XrdRuntimeException.systemException(errorCode)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(metadata)
                .details(detail)
                .build();
    }

    /**
     * Validates that the participant context id and DID required for a {@code ParticipantManifest} are non-blank.
     */
    public static void validateManifestFields(String participantContextId, String did) {
        if (participantContextId == null || participantContextId.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED, "participantContextId must not be blank");
        }
        if (did == null || did.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED, "did must not be blank");
        }
    }
}
