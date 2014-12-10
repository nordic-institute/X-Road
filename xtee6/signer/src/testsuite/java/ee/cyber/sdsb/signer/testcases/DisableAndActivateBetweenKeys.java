package ee.cyber.sdsb.signer.testcases;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.CsrCertStructure;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.TestSuiteHelper.Pair;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DisableAndActivateBetweenKeys extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(
            DisableAndActivateBetweenKeys.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    static ClientId client = createClientId("servicemember");

    @Test
    public synchronized void t01genKeysWithCertsAndImport()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Generate and import certs and csrs for keys.");
        LOG.info("###########################################################");

        TokenInfo softToken = initializeSoftToken("1234");

        activateDevice(softToken, true);

        int keys = 3;
        assumeTrue("Tests are designed ta have at least 3 keys", keys >= 3);
        int certs = 1;
        int secUntilExpired = 999;

        HashMap<String, CsrCertStructure> keyCert = genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, client, softToken);

        importCerts(keyCert);
        keyCertStruct.putAll(keyCert);
    }

    @Test
    public void t02deleteCertsUnderKeyAndSignWithThisKey()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t02 - Started: Deleting all certs under one key and "
                + "checking ability to sign afterwards. Failure expected");
        LOG.info("###########################################################");

        // only under one key to have some keys with certs in the next test
        // for being able to test more possible states
        LOG.trace(listDevices());

        Iterator<Entry<String, CsrCertStructure>> it =
                keyCertStruct.entrySet().iterator();
        Entry<String, CsrCertStructure> item = it.next();
        String keyId = item.getKey();
        for (byte[] certBytes : item.getValue().getCertListCerts()) {
            // deleteCert(certBytes); // FIXME
        }

        // remove key from map, because signer deletes key when there
        // aren't any csr or certs under key
        it.remove();

        LOG.trace("Removed key: {}", keyId);
        LOG.trace(listDevices());

        thrown.expectError(withSignerPrefix(X_KEY_NOT_FOUND));
        softSign(keyId);
    }

    @Test
    public void t03memberSigningInfoTransferring() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t03 - Started: check memeber signing info after "
                + "disabling previously used cert ");
        LOG.info("###########################################################");

        // TODO: Try transferring to cert under same key and
        // transferring cert under other key
        Pair keyCert = getMemberSignInfo(client, true);
        assertNotNull(keyCert);

        CsrCertStructure csr = keyCertStruct.get(keyCert.getId());
        assertNotNull(csr);
        for (byte[] certBytes : csr.getCertListCerts()) {
            activateCert(certBytes, false);
        }

        Pair keyCertAfterDeactivation = getMemberSignInfo(client, true);

        //if failure: we know that signer is using after deact same key AND cert
        assertFalse(keyCertAfterDeactivation.equals(keyCert));
        //if failure: we know that signer is using same key
        assertFalse(keyCertAfterDeactivation.getId().equals(
                keyCert.getId()));

        csr = keyCertStruct.get(keyCert.getId());
        assertNotNull(csr);
        for (byte[] certBytes : csr.getCertListCerts()) {
            activateCert(certBytes, true);
        }

        Pair keyCertAfterActivation = getMemberSignInfo(client, true);

        // If first sert is activated again then signer takes first cert for signing
        assertFalse(keyCertAfterActivation.equals(keyCert));
    }

    @Test
    public void t04disableDeviceAndSignWithKeyUnderIt()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t04 - Started: Disabling certs under key and "
                + "checking ability to sign afterwards. Failure expected");
        LOG.info("###########################################################");

        LOG.trace(listDevices());

        TokenInfo softToken = initializeSoftToken("1234");

        activateDevice(softToken, false);
        // only one to have some keys with certs in the next test for being
        // able to test more possible states
        Iterator<Entry<String, CsrCertStructure>> it =
                keyCertStruct.entrySet().iterator();
        Entry<String, CsrCertStructure> item = it.next();
        String keyId = item.getKey();

        thrown.expectError(withSignerPrefix(X_TOKEN_NOT_ACTIVE));
        LOG.trace(listDevices());

        softSign(keyId);
    }

    @Test
    public void t05deleteAllKeys() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t05 - Started: Deleting all certs under one key and "
                + "checking ability to sign afterwards. Failure expected");
        LOG.info("###########################################################");

        TokenInfo softToken = initializeSoftToken("1234");

        activateDevice(softToken, true);

        LOG.trace(listDevices());

        Iterator<Entry<String, CsrCertStructure>> it =
                keyCertStruct.entrySet().iterator();
        String keyId = null;

        while (it.hasNext()) {
            Entry<String, CsrCertStructure> item = it.next();
            if (!it.hasNext()) {
                // for checking is it possible to delete
                // final key when token inactive
                activateDevice(softToken, false);
            }
            keyId = item.getKey();

            deleteKey(keyId);

            it.remove();
            LOG.trace("Wanted to remove key: {}", keyId);
            LOG.trace(listDevices());
        }

        assertEquals(0, keyCount("1")); // 1 for softtoken

        // previous must work, now we should get an exception.
        thrown.expectError(withSignerPrefix(X_UNKNOWN_MEMBER));

        LOG.trace(listDevices());

        softSign(getMemberSignInfo(client)); /** If happened "Signer signed
        content when key was not in the configuration."*/
    }
}

