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

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.globalconf.impl.signature.SignatureVerifier;
import org.niis.xroad.keyconf.SigningInfo;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.conf.SigningCtxProviderImpl;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.test.keyconf.TestKeyConf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.TestCertUtil.getDefaultValidCertDate;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BatchSignerTest {

    private final GlobalConfProvider globalConf = new TestSuiteGlobalConf();
    private final OcspVerifierFactory ocspVerifierFactory = new OcspVerifierFactory();
    private final TestCertUtil.PKCS12 producerP12 = TestCertUtil.getProducer();
    private final ClientId.Conf producerClientId = ClientId.Conf.create("EE", "BUSINESS", "producer");
    private final ProxyProperties proxyProperties = ConfigUtils.defaultConfiguration(ProxyProperties.class);

    @Mock
    private SignerRpcClient signerClient;
    @Mock
    private SignerSignClient signerSignClient;
    @Mock
    private SignerRpcChannelProperties signerRpcChannelProperties;

    private BatchSigner batchSigner;

    @BeforeEach
    void beforeEach() {
        org.apache.xml.security.Init.init();
        when(signerRpcChannelProperties.deadlineAfter()).thenReturn(60_000);
        batchSigner = new BatchSigner(signerClient, signerSignClient, signerRpcChannelProperties);
    }

    @AfterEach
    void afterEach() {
        batchSigner.destroy();
        batchSigner = null;
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

        List<Callable<BatchSignResult>> callables = getCallables(messages, signingCtx);

        List<Future<BatchSignResult>> results = invokeCallables(callables, 50);

        final AtomicInteger batchSignatureDetectCounter = new AtomicInteger();
        for (Future<BatchSignResult> result : results) {
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

        if (batchSignatureDetectCounter.get() == 0) {
            fail("Batch signature was not detected.");
        } else {
            log.info("Batch signature was triggered {} times", batchSignatureDetectCounter.get());
        }
    }

    private List<Callable<BatchSignResult>> getCallables(List<String> messages, SigningCtx signingCtx) {
        List<Callable<BatchSignResult>> callables = new ArrayList<>();
        for (final String message : messages) {
            callables.add(() -> {
                try {
                    MessagePart hashPart = new MessagePart(MessageFileNames.MESSAGE, DigestAlgorithm.SHA512,
                            calculateDigest(DigestAlgorithm.SHA512, message.getBytes()), message.getBytes());

                    List<MessagePart> hashes = Collections.singletonList(hashPart);

                    SignatureBuilder builder = new SignatureBuilder();
                    builder.addPart(hashPart);
                    SignatureData signatureData = signingCtx.buildSignature(builder);

                    return new BatchSignResult(producerClientId, message, signatureData, hashes);

                } catch (Exception e) {
                    log.error("Error", e);
                    return new BatchSignResult(producerClientId, message, null, null);
                }

            });
        }
        return callables;
    }

    @SneakyThrows
    private SigningCtxProvider createSigningCtxProvider(ClientId subject) {
        var keyConf = new TestKeyConf(globalConf) {
            @Override
            public SigningInfo getSigningInfo(ClientId clientId) {
                return new SigningInfo("keyid", CKM_RSA_PKCS, subject, producerP12.certChain[0], null, null) {
                    @Override
                    public boolean verifyValidity(Date atDate) {
                        return true;
                    }
                };
            }
        };

        return new SigningCtxProviderImpl(globalConf, keyConf, batchSigner, proxyProperties);
    }

    private List<Future<BatchSignResult>> invokeCallables(List<Callable<BatchSignResult>> callables, int threads)
            throws InterruptedException {
        try (ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
            return executorService.invokeAll(callables);
        }
    }

    private record BatchSignResult(ClientId.Conf clientId, String message, SignatureData signatureData,
                                   List<MessagePart> messageParts) {
    }

    private void verify(final BatchSignResult batchSignResult)
            throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(globalConf, ocspVerifierFactory, batchSignResult.signatureData());
        verifier.addParts(batchSignResult.messageParts());

        HashChainReferenceResolver resolver = new HashChainReferenceResolver() {
            @Override
            public InputStream resolve(String uri) {
                return switch (uri) {
                    case MessageFileNames.SIG_HASH_CHAIN ->
                            new ByteArrayInputStream(batchSignResult.signatureData().getHashChain().getBytes(StandardCharsets.UTF_8));
                    case MessageFileNames.MESSAGE -> new ByteArrayInputStream(batchSignResult.message().getBytes(StandardCharsets.UTF_8));
                    default -> null;
                };
            }

            @Override
            public boolean shouldResolve(String uri, byte[] digestValue) {
                return true;
            }
        };

        if (batchSignResult.signatureData().getHashChainResult() != null) {
            verifier.setHashChainResourceResolver(resolver);
        }

        verifier.verify(batchSignResult.clientId(), getDefaultValidCertDate());
    }

}
