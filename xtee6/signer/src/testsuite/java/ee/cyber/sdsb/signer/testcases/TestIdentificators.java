package ee.cyber.sdsb.signer.testcases;

import java.util.HashMap;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.CsrCertStructure;
import ee.cyber.sdsb.signer.KeyCertStructCheckRep;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.signer.TestSuiteHelper.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestIdentificators extends TestCase {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(
            TestIdentificators.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
    new HashMap<>();

    private static TokenInfo softToken = initializeSoftToken("1234");

    private static ClientId client3 =
            createClientId("riigiasutus", "publicmember3", null);

    @Test
    public synchronized void t01genKeysWithCertsToClassRiigiasutus()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Generate and import certs and csrs for "
                + "MemberClass riigiasutus");
        LOG.info("###########################################################");

        activateDevice(softToken, true);

        int keys = 2; // at least 2, will be used in test t05 and t06
        int certs = 1;
        int secUntilExpired = 999;
        HashMap<String, CsrCertStructure> keyCert = (genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, client3, softToken));
        LOG.debug(listDevices());
        importCerts(keyCert);
        keyCertStruct.putAll(keyCert);
        new KeyCertStructCheckRep(keyCertStruct, softToken);
    }

    @Test
    public synchronized void t02memberSigningInfoForClassRiigiasutus()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t02 - Check signer capability to give signing info for "
                + "MemberClass riigiasutus");
        LOG.info("###########################################################");

        getMemberSignInfo(client3);
    }
}

