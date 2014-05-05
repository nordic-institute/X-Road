package ee.cyber.sdsb.signer.testcases;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;

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
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.cyber.sdsb.common.ErrorCodes.X_TOKEN_NOT_FOUND;
import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSimpleRequests extends TestCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestSimpleRequests.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    private static TokenInfo softToken = initializeSoftToken("1234");

    private static ClientId client = createClientId("servicemember");

    @Test
    public synchronized void t01genKeysWithCerts() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Generate and import certs and csrs for keys.");
        LOG.info("###########################################################");

        activateDevice(softToken, true);
        LOG.trace(listDevices());

        int keys = 2; // at least 2, will be used in test t05 and t06
        int certs = 1;
        int secUntilExpired = 999;

        HashMap<String, CsrCertStructure> keyCert =
                genKeysWithSignCerts(keys, certs, KeyUsageInfo.SIGNING,
                            secUntilExpired, client, softToken);

        importCerts(keyCert);
        keyCertStruct.putAll(keyCert);
    }

    @Test
    public void t02keyFriendlyName() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t02 - Started: Assign one friendly name to a key");
        LOG.info("###########################################################");

        LOG.trace(listDevices());

        Iterator<Entry<String, CsrCertStructure>> it =
                keyCertStruct.entrySet().iterator();
        Entry<String, CsrCertStructure> item = it.next();
        String keyId = item.getKey();

        String name = "My key1";
        setKeyFriendlyName(keyId, name);
        assertEquals(name, getKeyFriendlyName(keyId));
        softSign(getMemberSignInfo(client));

        name = "ÖÄÕÜ;'!@#$";
        setKeyFriendlyName(keyId, name);
        assertEquals(name, getKeyFriendlyName(keyId));
        softSign(getMemberSignInfo(client));

        name = "";
        setKeyFriendlyName(keyId, name);
        assertEquals(name, getKeyFriendlyName(keyId));
        softSign(getMemberSignInfo(client));

        name = null;
        setKeyFriendlyName(keyId, name);
        assertEquals(name, getKeyFriendlyName(keyId));
        softSign(getMemberSignInfo(client)); // To check

        byte[] b = new byte[200];
        new Random().nextBytes(b);
        name = new String(b);
        setKeyFriendlyName(keyId, name);
        assertEquals(name, getKeyFriendlyName(keyId));
        softSign(getMemberSignInfo(client));

        thrown.expectError(withSignerPrefix(X_KEY_NOT_FOUND));
        setKeyFriendlyName("FOOO", name);
    }

    @Test
    public void t03tokenFriendlyName() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t03 - Started: Assign one friendly name to a token");
        LOG.info("###########################################################");

        LOG.trace(listDevices());

        String name = "My device1";
        setDeviceFriendlyName(initializeSoftToken("1234").getId(), name);
        assertEquals(name, getDeviceFriendlyName(initializeSoftToken("1234").getId()));
        softSign(getMemberSignInfo(client));

        LOG.trace(listDevices());

        name = "@!#$!@#%P:{:''as'as+_)(*&^%$#@!";
        setDeviceFriendlyName(initializeSoftToken("1234").getId(), name);
        assertEquals(name, getDeviceFriendlyName(initializeSoftToken("1234").getId()));
        softSign(getMemberSignInfo(client));

        LOG.trace(listDevices());

        name = "";
        setDeviceFriendlyName(initializeSoftToken("1234").getId(), name);
        assertEquals(name, getDeviceFriendlyName(initializeSoftToken("1234").getId()));
        softSign(getMemberSignInfo(client));
        LOG.trace(listDevices());

        byte[] b = new byte[200];
        new Random().nextBytes(b);
        name = new String(b);
        setDeviceFriendlyName(initializeSoftToken("1234").getId(), name);
        assertEquals(name, getDeviceFriendlyName(initializeSoftToken("1234").getId()));
        LOG.trace(listDevices());

        name = null;
        setDeviceFriendlyName(initializeSoftToken("1234").getId(), name);
        assertEquals(name, getDeviceFriendlyName(initializeSoftToken("1234").getId()));

        LOG.trace(listDevices());

        thrown.expectError(withSignerPrefix(X_TOKEN_NOT_FOUND));
        setDeviceFriendlyName("FOOO", name);
    }

    @Test
    public void t04genAndDeleteCsrs() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t04 - Started: Delete Csrs under on key + one " +
                "to see it fail");
        LOG.info("###########################################################");

        int keys = 2;
        int secUntilExpired = 999;

        HashMap<String, CsrCertStructure> keyCert = createCsrs(
                keys, KeyUsageInfo.SIGNING,
                secUntilExpired, client, initializeSoftToken("1234"));

        keyCertStruct.putAll(keyCert);

        LOG.trace(listDevices());
        for (String keyId : keyCert.keySet()) {
            deleteCsr(keyCert.get(keyId).getId());
            keyCertStruct.remove(keyId);
            /* If no csr or certs are under
             * key then key will be deleted
             */
        }
        LOG.trace(listDevices());
    }

    @Test
    public void t05deleteCert() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t05 - Started: Delete Cert");
        LOG.info("###########################################################");

        LOG.trace(listDevices());
        assertTrue(keyCertStruct.keySet().size() >= 1);
        for (String keyId : keyCertStruct.keySet()) {
            //deleteCert(keyCertStruct.get(keyId).getId()); // FIXME
            keyCertStruct.remove(keyId);
            /* If no csr or certs are under
             * key then key will be deleted
             */
            break;
        }
        LOG.trace(listDevices());
    }

    @Test
    public void t06deleteKey() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t06 - Started: Delete Key");
        LOG.info("###########################################################");

        LOG.trace(listDevices());
        assertTrue(keyCertStruct.keySet().size() >= 1);
        for (String keyId : keyCertStruct.keySet()) {
            deleteKey(keyId);
            keyCertStruct.remove(keyId);
            break;
            // delete only one key, to have some keys for next tests if specified
        }
        LOG.trace(listDevices());
    }

}

