package ee.cyber.sdsb.common.asic;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import ee.cyber.sdsb.common.util.ExpectedCodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;

@RunWith(Parameterized.class)
public class AsicContainerTest {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Parameters(name = "{index}: verify(\"{0}\") should throw \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"valid-signed-message.asice", null},
                {"no-mimetype.asice", X_ASIC_MIME_TYPE_NOT_FOUND},
                {"no-message.asice", X_ASIC_MESSAGE_NOT_FOUND},
                {"no-signature.asice", X_ASIC_SIGNATURE_NOT_FOUND},
                {"not-asic.asice", X_ASIC_MIME_TYPE_NOT_FOUND}
        });
    }

    private String containerFile;
    private String errorCode;

    public AsicContainerTest(String containerFile, String errorCode) {
        this.containerFile = containerFile;
        this.errorCode = errorCode;
    }

    @Test
    public void test() throws Exception {
        thrown.expectError(errorCode);

        try (FileInputStream in =
                new FileInputStream("src/test/resources/" + containerFile)) {
            // just try to create the container -- the contents are verified
            // in the constructor
            AsicContainer.read(in);
        }
    }
}
