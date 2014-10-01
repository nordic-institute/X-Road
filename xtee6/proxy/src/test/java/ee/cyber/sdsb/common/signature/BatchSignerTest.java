package ee.cyber.sdsb.common.signature;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;

import com.typesafe.config.ConfigFactory;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestSecurityUtil;
import ee.cyber.sdsb.common.hashchain.HashChainReferenceResolver;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MessageFileNames;
import ee.cyber.sdsb.proxy.signedmessage.SignerSigningKey;
import ee.cyber.sdsb.signer.protocol.SignerClient;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;

public class BatchSignerTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(BatchSignerTest.class);

    private static final String ALGORITHM = DEFAULT_DIGEST_ALGORITHM_ID;

    private static final String KEY_ID = "testorg";

    private static final ClientId CORRECT_MEMBER =
            ClientId.create("EE", "TODO", "Test Org");

    private static final Date CORRECT_VALIDATION_DATE = createDate(1, 9, 2012);

    private static X509Certificate subjectCert;
    private static X509Certificate issuerCert;
    private static X509Certificate signerCert;
    private static PrivateKey signerKey;

    private static CountDownLatch latch;
    private static Integer sigIdx = 0;

    private static ActorSystem actorSystem;

    static {
        TestSecurityUtil.initSecurity();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        actorSystem = ActorSystem.create("Proxy",
                ConfigFactory.load().getConfig("proxy"));
        SignerClient.init(actorSystem);
        BatchSigner.init(actorSystem);

        subjectCert = TestCertUtil.getTestOrg().cert;
        issuerCert = TestCertUtil.getCaCert();
        signerCert = TestCertUtil.getOcspSigner().cert;
        signerKey = TestCertUtil.getOcspSigner().key;

        List<String> messages = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            messages.add(IOUtils.toString(new FileInputStream(args[i])));
        }

        latch = new CountDownLatch(messages.size());

        Date thisUpdate = new DateTime().plusDays(1).toDate();
        final OCSPResp ocsp = OcspTestUtils.createOCSPResponse(
                subjectCert, issuerCert, signerCert,
                signerKey, CertificateStatus.GOOD, thisUpdate, null);

        for (final String message : messages) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String hash = hash(message);
                        LOG.info("File: {}, hash: {}", message, hash);

                        List<MessagePart> hashes = new ArrayList<>();
                        hashes.add(new MessagePart(MessageFileNames.MESSAGE,
                                SHA512_ID, hash));

                        SignatureBuilder builder = new SignatureBuilder();
                        builder.addParts(hashes);

                        builder.setSigningCert(subjectCert, ocsp);

                        LOG.info("### Calculating signature...");

                        SignatureData signatureData = builder.build(
                                new SignerSigningKey(KEY_ID),
                                CryptoUtils.SHA512WITHRSA_ID);

                        synchronized (sigIdx) {
                            LOG.info("### Created signature: "
                                    + signatureData.getSignatureXml());

                            LOG.info("HashChainResult: "
                                    + signatureData.getHashChainResult());
                            LOG.info("HashChain: "
                                    + signatureData.getHashChain());

                            toFile("message-" + sigIdx + ".xml", message);

                            String sigFileName =
                                    signatureData.getHashChainResult() != null ?
                                            "batch-sig-" : "sig-";

                            toFile(sigFileName + sigIdx + ".xml",
                                    signatureData.getSignatureXml());

                            if (signatureData.getHashChainResult() != null) {
                                toFile("hash-chain-" + sigIdx + ".xml",
                                        signatureData.getHashChain());
                                toFile("hash-chain-result.xml",
                                        signatureData.getHashChainResult());
                            }
                            sigIdx++;
                        }

                        try {
                            verify(signatureData, hashes, message);
                            LOG.info("Verification successful (message hash: "
                                    + "{})", hash);
                        } catch (Exception e) {
                            LOG.error("Verification failed (message hash: "
                                    + hash + ")", e);
                        }
                    } catch (Exception e) {
                        LOG.error("Error: " + e);
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();
        }

        latch.await();
        actorSystem.shutdown();
    }

    private static void verify(final SignatureData signatureData,
            final List<MessagePart> hashes, final String message)
                    throws Exception {
        SignatureVerifier verifier = new SignatureVerifier(signatureData);
        verifier.addParts(hashes);

        HashChainReferenceResolver resolver =
                new HashChainReferenceResolver() {
            @Override
            public InputStream resolve(String uri) throws IOException {
                switch (uri) {
                    case MessageFileNames.SIG_HASH_CHAIN:
                        return new ByteArrayInputStream(
                                signatureData.getHashChain().getBytes(
                                        StandardCharsets.UTF_8));
                    case MessageFileNames.MESSAGE:
                        return new ByteArrayInputStream(
                                message.getBytes(StandardCharsets.UTF_8));
                }
                return null;
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
        LOG.info("BatchSigner <message1> <message2> ...\n"
                + "NOTE: It assumes that Signer has configured a batch signing "
                + "token with keyId 'testorg', where the key and cert are the "
                + "ones found in 'common-test/src/test/certs/testorg.p12'");
    }

    private static String hash(String data) throws Exception {
        DigestCalculator calc = createDigestCalculator(ALGORITHM);
        IOUtils.write(data, calc.getOutputStream());

        return encodeBase64(calc.getDigest());
    }

    private static void toFile(String fileName, String data) throws Exception {
        IOUtils.write(data, new FileOutputStream(fileName));
        LOG.info("Created file " + fileName);
    }

    private static Date createDate(int day, int month, int year) {
        Calendar cal = Calendar.getInstance();
        cal.clear(); // Let's clear the current time.
        cal.set(year, month, day);
        return cal.getTime();
    }
}
