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
package org.niis.xroad.signer.core.tokenmanager.token;

import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BlockingTokenWorkerTest {

    @Test
    void refreshDoesNothingWhenUnderlyingWorkerSucceeds() throws Exception {
        AbstractTokenWorker tokenWorker = mock(AbstractTokenWorker.class);

        BlockingTokenWorker blockingTokenWorker = new BlockingTokenWorker(tokenWorker);
        blockingTokenWorker.refresh();

        verify(tokenWorker, times(1)).refresh();
        verify(tokenWorker, times(1)).onActionHandled();
    }

    @Test
    void refreshPropagatesPkcs11ExceptionUnaltered() throws Exception {
        AbstractTokenWorker tokenWorker = mock(AbstractTokenWorker.class);
        PKCS11Exception stuckOperation = new PKCS11Exception(PKCS11Constants.CKR_OPERATION_ACTIVE);
        doThrow(stuckOperation).when(tokenWorker).refresh();

        BlockingTokenWorker blockingTokenWorker = new BlockingTokenWorker(tokenWorker);

        assertThatThrownBy(blockingTokenWorker::refresh)
                .isSameAs(stuckOperation);

        verify(tokenWorker, times(1)).onActionHandled();
    }

    @Test
    void refreshTranslatesOtherExceptions() throws Exception {
        AbstractTokenWorker tokenWorker = mock(AbstractTokenWorker.class);
        doThrow(new IllegalStateException("permanent failure")).when(tokenWorker).refresh();

        BlockingTokenWorker blockingTokenWorker = new BlockingTokenWorker(tokenWorker);

        assertThatThrownBy(blockingTokenWorker::refresh)
                .isInstanceOf(XrdRuntimeException.class);

        verify(tokenWorker, times(1)).onActionHandled();
    }
}
