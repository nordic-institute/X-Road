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
package org.niis.xroad.proxy.core.signature;

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BatchSignerTest extends AbstractSignerTest {

    @Mock
    private SignerRpcClient signerClient;
    @Mock
    private SignerRpcChannelProperties signerRpcChannelProperties;

    @BeforeEach
    void beforeEach() {
        org.apache.xml.security.Init.init();
        when(signerRpcChannelProperties.deadlineAfter()).thenReturn(60_000);
        signer = new BatchSigner(signerClient, signerSignClient, signerRpcChannelProperties);
    }

    @AfterEach
    void afterEach() {
        ((BatchSigner) signer).destroy();
        signer = null;
    }

    @Test
    void shouldSignAllTheMessagesReceivedInParallel() throws Exception {
        final int count = 500;

        when(signerClient.isTokenBatchSigningEnabled(any())).thenReturn(true);
        // Sign with producer private key
        when(signerSignClient.sign(any(), any(), any())).thenAnswer(invocation -> {
            var args = invocation.getArguments();
            var signatureAlgId = (SignAlgorithm) args[1];
            var digest = (byte[]) args[2];
            byte[] data = SignDataPreparer.of(signatureAlgId).prepare(digest);
            SignAlgorithm signAlgorithm = (new KeyManagers(2048, "secp256r1"))
                    .getForRSA().getSoftwareTokenSignAlgorithm();
            Signature signature = Signature.getInstance(signAlgorithm.name(), BOUNCY_CASTLE);
            signature.initSign(producerP12.key);
            signature.update(data);
            return signature.sign();
        });


        final var signingCtx = createSigningCtxProvider(producerClientId).createSigningCtx(producerClientId);

        List<String> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add("random-msg:" + RandomStringUtils.secure().nextAlphabetic(100, 1000));
        }

        List<Callable<SignResult>> callables = getCallables(messages, signingCtx);

        List<Future<SignResult>> results = invokeCallables(callables, 50);

        final AtomicInteger batchSignatureDetectCounter = new AtomicInteger();
        for (Future<SignResult> result : results) {
            try {
                var signResult = result.get();

                assertThat(signResult.signatureData()).isNotNull();
                assertThat(signResult.signatureData().getSignatureXml()).isNotEmpty();

                verify(signResult);

                if (signResult.signatureData().isBatchSignature()) {
                    batchSignatureDetectCounter.incrementAndGet();
                }
            } catch (Throwable e) {
                fail("Verification has failed.", e);
            }
        }

        if (batchSignatureDetectCounter.get() < 1) {
            fail("Not a single batch signature was detected.");
        } else {
            log.info("Batch signature was triggered {} times", batchSignatureDetectCounter.get());
        }
    }

}
