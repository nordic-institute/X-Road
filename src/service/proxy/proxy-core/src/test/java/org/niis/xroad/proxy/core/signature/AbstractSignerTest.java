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
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mock;
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
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.test.keyconf.TestKeyConf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ee.ria.xroad.common.TestCertUtil.getDefaultValidCertDate;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;

@Slf4j
public abstract class AbstractSignerTest {

    protected final GlobalConfProvider globalConf = new TestSuiteGlobalConf();
    protected final OcspVerifierFactory ocspVerifierFactory = new OcspVerifierFactory();
    protected final TestCertUtil.PKCS12 producerP12 = TestCertUtil.getProducer();
    protected final ClientId.Conf producerClientId = ClientId.Conf.create("EE", "BUSINESS", "producer");
    protected final ProxyProperties proxyProperties = ConfigUtils.defaultConfiguration(ProxyProperties.class);

    protected MessageSigner signer;

    @Mock
    protected SignerSignClient signerSignClient;

    protected List<Callable<SignResult>> getCallables(List<String> messages, SigningCtx signingCtx) {
        List<Callable<SignResult>> callables = new ArrayList<>();
        for (final String message : messages) {
            callables.add(() -> {
                try {
                    MessagePart hashPart = new MessagePart(MessageFileNames.MESSAGE, DigestAlgorithm.SHA512,
                            calculateDigest(DigestAlgorithm.SHA512, message.getBytes()), message.getBytes());

                    List<MessagePart> hashes = Collections.singletonList(hashPart);

                    SignatureBuilder builder = new SignatureBuilder();
                    builder.addPart(hashPart);
                    SignatureData signatureData = signingCtx.buildSignature(builder);

                    return new SignResult(producerClientId, message, signatureData, hashes);

                } catch (Exception e) {
                    log.error("Error", e);
                    return new SignResult(producerClientId, message, null, null);
                }

            });
        }
        return callables;
    }

    @SneakyThrows
    protected SigningCtxProvider createSigningCtxProvider(ClientId subject) {
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

        return new SigningCtxProviderImpl(globalConf, keyConf, signer, proxyProperties);
    }

    protected List<Future<SignResult>> invokeCallables(List<Callable<SignResult>> callables, int threads)
            throws InterruptedException {
        try (ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
            return executorService.invokeAll(callables);
        }
    }

    protected record SignResult(ClientId.Conf clientId, String message, SignatureData signatureData,
                                List<MessagePart> messageParts) {
    }

    protected void verify(final SignResult signResult)
            throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(globalConf, ocspVerifierFactory, signResult.signatureData());
        verifier.addParts(signResult.messageParts());

        HashChainReferenceResolver resolver = new HashChainReferenceResolver() {
            @Override
            public InputStream resolve(String uri) {
                return switch (uri) {
                    case MessageFileNames.SIG_HASH_CHAIN ->
                            new ByteArrayInputStream(signResult.signatureData().getHashChain().getBytes(StandardCharsets.UTF_8));
                    case MessageFileNames.MESSAGE -> new ByteArrayInputStream(signResult.message().getBytes(StandardCharsets.UTF_8));
                    default -> null;
                };
            }

            @Override
            public boolean shouldResolve(String uri, byte[] digestValue) {
                return true;
            }
        };

        if (signResult.signatureData().getHashChainResult() != null) {
            verifier.setHashChainResourceResolver(resolver);
        }

        verifier.verify(signResult.clientId(), getDefaultValidCertDate());
    }

}
