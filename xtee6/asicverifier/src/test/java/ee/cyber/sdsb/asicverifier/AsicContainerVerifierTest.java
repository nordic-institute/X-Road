package ee.cyber.sdsb.asicverifier;

import java.io.FileInputStream;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;

import static ee.cyber.sdsb.common.ErrorCodes.*;

public class AsicContainerVerifierTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @BeforeClass
    public static void setUpConf() {
        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                "src/test/resources/globalconf.xml");
    }

    @Test
    public void validContainer() throws Exception {
        verify("valid-signed-message.asice");
    }

    @Test
    public void validContainerWithAttachments() throws Exception {
        verify("valid-signed-hashchain.asice");
    }

    @Test
    public void wrongMessageInContainer() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);
        verify("wrong-message.asice");
    }

    @Test
    public void invalidDigest() throws Exception {
        thrown.expectError(X_INVALID_SIGNATURE_VALUE);
        verify("invalid-digest.asice");
    }

    @Test
    public void invalidHashChain() throws Exception {
        thrown.expectError(X_MALFORMED_SIGNATURE, X_INVALID_HASH_CHAIN_REF);
        verify("invalid-signed-hashchain.asice");
    }

    private static void verify(String fileName) throws Exception {
        try (FileInputStream is =
                new FileInputStream("src/test/resources/" + fileName)) {
            AsicContainerVerifier verifier = AsicContainerVerifier.create(is);
            verifier.verify();
        }
    }
}
