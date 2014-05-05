package ee.cyber.sdsb.signer.testcases;

import java.util.HashMap;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.CsrCertStructure;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.signer.TestSuiteHelper.*;

public class SimpleHardwareScenario extends TestCase {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(
            SimpleHardwareScenario.class);


    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    private static ClientId client = createClientId("servicemember");

    private TokenInfo dummyToken = initiateDummyToken("1234");
//    private DeviceInfo softToken = getSoftToken();

    private static String signKeyId = null;

    @Test
    public synchronized void t01activateDevices()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Started: activate");
        LOG.info("###########################################################");


        listDevices();
        int keys = 2;
        int certs = 2;
        int secUntilExpired = 999;
//        activateDevice(dummyToken, null, true);
        activateDevice(dummyToken, true);
//        new GenerateKeysRequest(dummyToken.id).execute();
//      HashMap<String, CsrCertStructure> keyCert = genKeysWithSignCerts(
//              keys, certs, KeyUsageInfo.SIGNING,
//              secUntilExpired, client, dummyToken);
//
//      importCerts(keyCert);
//      keyCertStruct.putAll(keyCert);
        listDevices();
        activateDevice(dummyToken, false);

        listDevices();
    }

    // TODO: do things work fine when pin is blocked
}
