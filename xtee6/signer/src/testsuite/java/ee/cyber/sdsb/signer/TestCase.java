package ee.cyber.sdsb.signer;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestCase {

    public static boolean isTestSuiteRunning = false;

    @BeforeClass
    public static void initTest() throws Exception {
        if (!isTestSuiteRunning) {
            SignerHelper.startSigner();
        }
    }

    @AfterClass
    public static void closeTest() throws Exception {
        if (!isTestSuiteRunning) {
            SignerHelper.stopSigner();
        }
    }
}
