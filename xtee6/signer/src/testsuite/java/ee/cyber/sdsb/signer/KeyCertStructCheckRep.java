package ee.cyber.sdsb.signer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;

import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeyCertStructCheckRep {
    public KeyCertStructCheckRep(
            HashMap<String, CsrCertStructure> keyCertStruct,
            TokenInfo token) throws Exception {
        assertEquals(keyCertStruct.size(), keyCount(token));
        if (keyCertStruct.size() == 0) {
            return;
        }
        for (KeyInfo keyInfo : getKeys(token)) {

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
                            token, keyInfo.getId()).toArray()) + "\n" +
                            "test: " + Integer.toString(keyCertStruct.get(
                                    keyInfo.getId()).getCsrMemList().size()) +
                            " csr(s) for members: " + keyCertStruct.get(
                                    keyInfo.getId()).objListToString("csrMem") +
                            "\nUnder key: " + keyInfo.getId() + "\n", success);
//            for (CertRequestInfo csrInfo : keyInfo.certRequests) {
//                for (byte[] csrByte :
//                    keyCertStruct.get(keyInfo.keyId).getCsrList()) {
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

    // Second constructor prints when an error extra information about what
    // processes have been going on, used in RandomTester
    public KeyCertStructCheckRep(
            HashMap<String, CsrCertStructure> keyCertStruct,
            TokenInfo token, ArrayList<String> seedList) throws Exception {
        assertEquals(keyCertStruct.size(), keyCount(token));
        if (keyCertStruct.size() == 0) {
            return;
        }

        for (KeyInfo keyInfo : getKeys(token)) {

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
                            token, keyInfo.getId()).toArray()) + "\n" +
                            "test: " + Integer.toString(keyCertStruct.get(
                                    keyInfo.getId()).getCsrMemList().size()) +
                            " csr(s) for members: " + keyCertStruct.get(
                                    keyInfo.getId()).objListToString("csrMem") +
                            "\nUnder key: " + keyInfo.getId() + "\n" +
                            "Actions taken: " +
                                    stringArrayListToString(seedList), success);
//            for (CertRequestInfo csrInfo : keyInfo.certRequests) {
//                for (byte[] csrByte :
//                    keyCertStruct.get(keyInfo.keyId).getCsrList()) {
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
