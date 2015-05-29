package ee.ria.xroad.common.signature;

import java.security.PrivateKey;
import java.security.Signature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.proxy.signedmessage.SigningKey;

/**
 * Signing key that is located in PKCS12 key store.
 */
public class TestSigningKey implements SigningKey {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestSigningKey.class);

    /** The private key. */
    private PrivateKey key;

    /**
     * Creates a new Pkcs12SigningKey with provided key.
     * @param key the private key.
     */
    public TestSigningKey(PrivateKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        this.key = key;
    }

    @Override
    public SignatureData calculateSignature(SigningRequest request,
            String signatureAlgorithmId) throws Exception {
        LOG.debug("calculateSignature({})", request);

        SignatureCtx ctx = new SignatureCtx(signatureAlgorithmId);
        ctx.add(request);

        byte[] tbsData = ctx.getDataToBeSigned();
        byte[] signatureValue = sign(ctx.getSignatureAlgorithmId(), tbsData);

        String signatureXML = ctx.createSignatureXml(signatureValue);
        return ctx.createSignatureData(signatureXML, 0);
    }

    protected byte[] sign(String signatureAlgorithmId, byte[] data)
            throws Exception {
        Signature signature = Signature.getInstance(signatureAlgorithmId);

        signature.initSign(key);
        signature.update(data);

        return signature.sign();
    }
}
