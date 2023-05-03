/**
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
package ee.ria.xroad.common.signature;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestSecurityUtil;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MessageFileNames;
import ee.ria.xroad.proxy.signedmessage.SignerSigningKey;
import ee.ria.xroad.signer.protocol.SignerClient;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.SHA512_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.createDigestCalculator;

/**
 * Batch signer test program.
 */
@Slf4j
public final class BatchSignerIntegrationTest {

    private static final int SIGNER_INIT_DELAY = 2500;

    private static final String ALGORITHM = DEFAULT_DIGEST_ALGORITHM_ID;

    private static final String KEY_ID = "consumer";

    private static final ClientId CORRECT_MEMBER = ClientId.Conf.create("EE", "FOO", "consumer");

    private static final Date CORRECT_VALIDATION_DATE = createDate(30, 9, 2014);

    private static CountDownLatch latch;
    private static Integer sigIdx = 0;

    static {
        TestSecurityUtil.initSecurity();
    }

    private BatchSignerIntegrationTest() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();

            return;
        }

        ActorSystem actorSystem = ActorSystem.create("Proxy", ConfigFactory.load().getConfig("proxy"));
        SignerClient.init(actorSystem);

        Thread.sleep(SIGNER_INIT_DELAY); // wait for signer client to connect

        BatchSigner.init(actorSystem);

        X509Certificate subjectCert = TestCertUtil.getConsumer().certChain[0];
        X509Certificate issuerCert = TestCertUtil.getCaCert();
        X509Certificate signerCert = TestCertUtil.getOcspSigner().certChain[0];
        PrivateKey signerKey = TestCertUtil.getOcspSigner().key;

        List<String> messages = new ArrayList<>();

        for (String arg : args) {
            messages.add(FileUtils.readFileToString(new File(arg)));
        }

        latch = new CountDownLatch(messages.size());
        Date thisUpdate = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));
        final OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subjectCert, issuerCert, signerCert, signerKey,
                CertificateStatus.GOOD, thisUpdate, null);

        for (final String message : messages) {
            new Thread(() -> {
                try {
                    byte[] hash = hash(message);
                    log.info("File: {}, hash: {}", message, hash);

                    MessagePart hashPart = new MessagePart(MessageFileNames.MESSAGE, SHA512_ID,
                            calculateDigest(SHA512_ID, message.getBytes()), message.getBytes());

                    List<MessagePart> hashes = Collections.singletonList(hashPart);

                    SignatureBuilder builder = new SignatureBuilder();
                    builder.addPart(hashPart);

                    builder.setSigningCert(subjectCert);
                    builder.addOcspResponses(Collections.singletonList(ocsp));

                    log.info("### Calculating signature...");

                    SignatureData signatureData = builder.build(new SignerSigningKey(KEY_ID,
                            CryptoUtils.CKM_RSA_PKCS_NAME), CryptoUtils.SHA512_ID);

                    synchronized (sigIdx) {
                        log.info("### Created signature: {}", signatureData.getSignatureXml());

                        log.info("HashChainResult: {}", signatureData.getHashChainResult());
                        log.info("HashChain: {}", signatureData.getHashChain());

                        toFile("message-" + sigIdx + ".xml", message);

                        String sigFileName = signatureData.getHashChainResult() != null ? "batch-sig-" : "sig-";

                        toFile(sigFileName + sigIdx + ".xml", signatureData.getSignatureXml());

                        if (signatureData.getHashChainResult() != null) {
                            toFile("hash-chain-" + sigIdx + ".xml", signatureData.getHashChain());
                            toFile("hash-chain-result.xml", signatureData.getHashChainResult());
                        }

                        sigIdx++;
                    }

                    try {
                        verify(signatureData, hashes, message);

                        log.info("Verification successful (message hash: {})", hash);
                    } catch (Exception e) {
                        log.error("Verification failed (message hash: {})", hash, e);
                    }
                } catch (Exception e) {
                    log.error("Error", e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        Await.ready(actorSystem.terminate(), Duration.Inf());
    }

    private static void verify(final SignatureData signatureData, final List<MessagePart> hashes, final String message)
            throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(signatureData);
        verifier.addParts(hashes);

        HashChainReferenceResolver resolver = new HashChainReferenceResolver() {
            @Override
            public InputStream resolve(String uri) throws IOException {
                switch (uri) {
                    case MessageFileNames.SIG_HASH_CHAIN:
                        return new ByteArrayInputStream(signatureData.getHashChain().getBytes(StandardCharsets.UTF_8));
                    case MessageFileNames.MESSAGE:
                        return new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8));
                    default:
                        return null;
                }
            }

            @Override
            public boolean shouldResolve(String uri, byte[] digestValue) {
                return true;
            }
        };

        if (signatureData.getHashChainResult() != null) {
            verifier.setHashChainResourceResolver(resolver);
        }

        verifier.verify(CORRECT_MEMBER, CORRECT_VALIDATION_DATE);
    }

    private static void printUsage() {
        log.info("BatchSigner <message1> <message2> ...\n"
                + "NOTE: It assumes that Signer has configured a batch signing token with keyId 'testorg', "
                + "where the key and cert are the ones found in 'common-test/src/test/certs/testorg.p12'");
    }

    private static byte[] hash(String data) throws Exception {
        DigestCalculator calc = createDigestCalculator(ALGORITHM);
        IOUtils.write(data, calc.getOutputStream());

        return calc.getDigest();
    }

    private static void toFile(String fileName, String data) throws Exception {
        IOUtils.write(data, new FileOutputStream(fileName));

        log.info("Created file " + fileName);
    }

    private static Date createDate(int day, int month, int year) {
        Calendar cal = Calendar.getInstance();
        cal.clear(); // Let's clear the current time.
        cal.set(year, month, day);

        return cal.getTime();
    }
}
