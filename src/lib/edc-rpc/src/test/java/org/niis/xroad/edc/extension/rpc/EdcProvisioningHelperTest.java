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

import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.requireSuccessOrNotFound;

class EdcProvisioningHelperTest {

    @Test
    void requireSuccessOrNotFoundToleratesSuccess() {
        assertThatCode(() -> requireSuccessOrNotFound(ServiceResult.success(), DSP_PARTICIPANT_CONTEXT_FAILED, "ctx-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSuccessOrNotFoundToleratesNotFound() {
        assertThatCode(() -> requireSuccessOrNotFound(ServiceResult.notFound("gone"), DSP_PARTICIPANT_CONTEXT_FAILED, "ctx-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSuccessOrNotFoundThrowsOnOtherFailures() {
        var result = ServiceResult.unexpected("storage error");
        assertThatThrownBy(() -> requireSuccessOrNotFound(result, DSP_PARTICIPANT_CONTEXT_FAILED, "ctx-1"))
                .isInstanceOf(XrdRuntimeException.class);
    }
}
