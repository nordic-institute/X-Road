package ee.cyber.sdsb.signer.testcases;

import java.util.Arrays;
import java.util.HashMap;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.CsrCertStructure;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MemberSubsystem extends TestCase {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(
            MemberSubsystem.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    private static ClientId client = createClientId("servicemember4");
    private static ClientId clientSubsystem =
            createClientId("servicemember4", "subsystem1");
    // Serverconf.xml is assumed to have subsystem
    // EE/BUSINESS/servicemember4/subsystem1 and
    // NOT member EE/BUSINESS/servicemember4

    private TokenInfo softToken = initializeSoftToken("1234");

    @Test
    public synchronized void t01memberCertAsSubsystems()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Started: " +
                "1. Genereeri serdipäring, pannes nimeks liikme " +
                "nime (ilma alamsüsteemi koodita). Genereerimisel peab olema " +
                "valitav vaid liikme nimi." +
                "2. Genereeri sert." +
                "3. Impordi sert. Importimine peab õnnestuma.");
        LOG.info("###########################################################");


        listDevices();

        activateDevice(softToken, true);

        int keys = 1;
        int certs = 1;
        int secUntilExpired = 999;

        HashMap<String, CsrCertStructure> keyCert = genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, client, softToken);

        keyCertStruct.putAll(keyCert);
        importCerts(keyCert);

        keyCertStructCheckRep();
    }

    private void keyCertStructCheckRep() throws Exception {
        assertEquals(keyCertStruct.size(), keyCount(softToken));
        if (keyCertStruct.size() == 0) {
            return;
        }
        for (KeyInfo keyInfo : getKeys(softToken)) {

            boolean success = false;

            // check keys
            if (keyCertStruct.keySet().contains(keyInfo.getId())) {
                success = true;
            } else { success = false; }
            assertTrue("Inconsistency in conf: Keys don't match: \n" +
                    keyInfo.getId() + "\nNot in:\n" +
                    Arrays.toString(
                            keyCertStruct.keySet().toArray(new String[0])),
                            success);

            // check csrs
            if (keyInfo.getCertRequests().size() ==
                    keyCertStruct.get(keyInfo.getId()).getCsrMemList().size()) {
                success = true;
            } else { success = false; }
            assertTrue("Inconsistency in conf: Csrs don't match:\n" +
                    "Conf: " + Integer.toString(keyInfo.getCertRequests().size()) +
                    " csr(s) for members: " + Arrays.toString(getCsrMembers(
                            softToken, keyInfo.getId()).toArray()) + "\n" +
                            "test: " + Integer.toString(keyCertStruct.get(
                                    keyInfo.getId()).getCsrMemList().size()) +
                            " csr(s) for members: " + keyCertStruct.get(
                                    keyInfo.getId()).objListToString("csrMem") +
                            "\nUnder key: " + keyInfo.getId() + "\n", success);
//            for (CertRequestInfo csrInfo : keyInfo.certRequests) {
//                for (byte[] csrByte :
//                    keyCertStruct.get(keyInfo.getId()).getCsrList()) {
//                    /* TODO: compare members to whom csrs were made  - jump
//                     * from csrByte to ClientId client is missing*/
//                    if (csrInfo.memberId.equals(client)) {
//                        success = true;
//                    }
//                }
//            }

            // check certs
            for (CertificateInfo certInfo : keyInfo.getCerts()) {
                if (keyCertStruct.get(keyInfo.getId()).certListContains(
                        certInfo.getCertificateBytes())) {
                    success = true;
                    break;
                } else { success = false; }
            }
            assertTrue("Inconsistency in conf: Certs don't match." +
                    "Conf: " + Integer.toString(keyInfo.getCerts().size()) +
                    " cert(s)\n" +
                    "Test: " + Integer.toString(keyCertStruct.get(
                            keyInfo.getId()).getCertListCerts().size()) +
                            " cert(s)\n",
                            success);
        }
    }


}
