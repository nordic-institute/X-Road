package ee.cyber.sdsb.asicverifier;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;

import static ee.cyber.sdsb.common.ErrorCodes.*;

@RunWith(Parameterized.class)
public class AsicContainerVerifierTest {

    private final String containerFile;
    private final String errorCode;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    public AsicContainerVerifierTest(String containerFile, String errorCode) {
        this.containerFile = containerFile;
        this.errorCode = errorCode;
    }

    @BeforeClass
    public static void setUpConf() {
        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                "src/test/resources/globalconf.xml");
    }

    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"valid-signed-message.asice", null},
                {"valid-signed-hashchain.asice", null},
                {"valid-batch-ts.asice", null},
                {"wrong-message.asice", X_INVALID_SIGNATURE_VALUE},
                {"invalid-digest.asice", X_INVALID_SIGNATURE_VALUE},
                {"invalid-signed-hashchain.asice",
                    X_MALFORMED_SIGNATURE + "." + X_INVALID_HASH_CHAIN_REF},
                /* TODO: {"invalid-hashchain-modified-message.asice",
                    X_MALFORMED_SIGNATURE + "." + X_HASHCHAIN_UNUSED_INPUTS},*/
                // This verification actually passes, since the hash chain
                // is not verified and the signature is correct otherwise
                // TODO: {"invalid-not-signed-hashchain.asice", null},
                {"invalid-incorrect-references.asice", X_MALFORMED_SIGNATURE},
                {"invalid-ts-hashchainresult.asice", X_MALFORMED_SIGNATURE}
        });
    }

    @Test
    public void test() throws Exception {
        thrown.expectError(errorCode);
        verify(containerFile);
    }

    private static void verify(String fileName) throws Exception {
        try (FileInputStream is =
                new FileInputStream("src/test/resources/" + fileName)) {
            AsicContainerVerifier verifier = AsicContainerVerifier.create(is);
            verifier.verify();
        }
    }
}
