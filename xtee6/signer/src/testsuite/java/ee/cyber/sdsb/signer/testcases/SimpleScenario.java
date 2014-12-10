package ee.cyber.sdsb.signer.testcases;


import java.util.HashMap;
import java.util.Map.Entry;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.dummies.certificateauthority.CAMock;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.GenerateCertRequestResponse;

import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleScenario extends TestCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(SimpleScenario.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();
    // list containing key and cert pairs
    private static HashMap<String, byte[]> keyCertList = new HashMap<>();

    private static ClientId memberId = createClientId("servicemember");

    private static ClientId memberId2 = createClientId("servicemember2");

    private static String authKeyId = null;

    private static String signKeyId = null;

    @Test
    public void t01genKeyAndAuthCsr() throws Exception {
        // TODO: maybe move over to SignerProxyImpl?
        LOG.info("###########################################################");
        LOG.info("t01 - Generate authentication key.");
        LOG.info("###########################################################");

        TokenInfo softToken = initializeSoftToken("1234");

        // TODO: check return value.
        activateDevice(softToken, true);

        KeyInfo key = generateKey(softToken);
        authKeyId = key.getId();

        GenerateCertRequestResponse csrRequest =
                generateCertRequest(softToken, key,
                        createClientId("null"), KeyUsageInfo.AUTHENTICATION,
                        subjectName(createClientId("null")));

        // save key str and cert pari for later use
        keyCertList.put(key.getId(),
                CAMock.certRequest(csrRequest.getCertRequest(),
                        KeyUsageInfo.AUTHENTICATION, 1000));

        LOG.info("t01 - Finished successfully.");
    }

    @Test
    public void t02genKeyAndSignCsr() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t02 - Started: Generate signing key.");
        LOG.info("###########################################################");

        TokenInfo softToken = initializeSoftToken("1234");
        assertTrue(softToken.isActive());

        // TODO: check return value.
        KeyInfo key = generateKey(softToken);

        GenerateCertRequestResponse csrRequest =
                generateCertRequest(softToken, key, memberId,
                        KeyUsageInfo.SIGNING, subjectName(memberId));
        signKeyId = key.getId();

        // save key str and cert pari for later use
        keyCertList.put(key.getId(),
                CAMock.certRequest(csrRequest.getCertRequest(),
                        KeyUsageInfo.SIGNING, 1000));

        LOG.info("t02 - Successfully: Generated signing keys.");
    }

    @Test
    public void t03importCerts() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t03 - Started: importing and activating certs.");
        LOG.info("###########################################################");

        for (Entry<String, byte[]> keyCert : keyCertList.entrySet()) {
            String keyId = importCert(keyCert.getValue());
            assertEquals(keyCert.getKey(), keyId);

            activateCert(keyCertList.get(keyId), true);
            assertTrue(isCertActive(keyCertList.get(keyId)));
        }

        // TODO: This requires that the generated auth cert has been registered in GlobalConf
        //String authKey = getAuthKey();
        //assertEquals(authKeyId, authKey);
    }

    @Test
    public void t04softSignData() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t04 - Started: Signing random data.");
        LOG.info("###########################################################");


        for (String keyId : keyCertList.keySet()) {
            LOG.trace(listDevices());
            softSign(keyId);
            LOG.debug("Signed with cert {} under key {}",
                    certByteConversion(keyCertList.get(keyId)), keyId);
        }
    }

    @Test
    public void t05signThroughMemberSigningInfo() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t05 - Started: Signing random data for member.");
        LOG.info("###########################################################");

        String keyId = getMemberSignInfo(memberId);
        assertEquals(signKeyId, keyId);
        softSign(keyId);
    }

    @Test
    public void t06signFailedThroughMemberSigningInfo()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t06 - Started: Signing random data for member with "
                + "no active certs.");
        LOG.info("###########################################################");


        String keyId = getMemberSignInfo(memberId);

        assertTrue(keyCertList.containsKey(keyId));

        softSign(keyId);

        activateCert(keyCertList.get(keyId), false);

        thrown.expectError(withSignerPrefix(X_UNKNOWN_MEMBER));
        keyId = getMemberSignInfo(memberId);
    }

    @Test
    public void t07signAfterCertActivation()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t07 - Started: Signing random data for member with after "
                + "cert activation.");
        LOG.info("###########################################################");

        for (String keyId : keyCertList.keySet()) {
            activateCert(keyCertList.get(keyId), true);
        }

        String keyId = getMemberSignInfo(memberId);
        softSign(keyId);
    }
}
