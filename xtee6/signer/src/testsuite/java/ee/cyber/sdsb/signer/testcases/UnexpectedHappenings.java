package ee.cyber.sdsb.signer.testcases;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
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

import static ee.cyber.sdsb.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.cyber.sdsb.signer.TestSuiteHelper.*;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UnexpectedHappenings extends TestCase {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(
            UnexpectedHappenings.class);

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    // list containing key and cert pairs
    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    private static ClientId client = createClientId("servicemember");
    private static ClientId client2 = createClientId("servicemember2");
    private static ClientId clientExpire =
            createClientId("My_Cert_Expires_Soon");
    private static ClientId clientNotInConf = createClientId("NoMember");
    // In conf there exist members:  "BUSINESS/servicemember" ,
    // "BUSINESS/servicemember2", "riigiasutus/publicmember3" ,
    // "BUSINESS/My_Cert_Expires_Soon" and
    // subsystem "servicemember4/subsystem1"
    private TokenInfo softToken = initializeSoftToken("1234");

    @Test
    public synchronized void t01signWithExpiredCert()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Started: get member sign info which is expired");
        LOG.info("###########################################################");

        listDevices();

        activateDevice(softToken, true);

        int keys = 1;
        int certs = 1;
        int secUntilExpired = 2;

        HashMap<String, CsrCertStructure> keyCert = genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, clientExpire, softToken);

        keyCertStruct.putAll(keyCert);
        String key = getFirstKey(keyCert);

        importCert(keyCertStruct.get(key).getCert(0));
        new KeyCertStructCheckRep(keyCertStruct, softToken);

        Thread.sleep(2100); // 2100ms to wait until cert expires and a bit more

        //      thrown.expectError(addSignerPrefix(X_INTERNAL_ERROR)); // TODO: Signer doesn't check expiration date itself?
        getMemberSignInfo(clientExpire);
    }

    @Test
    public void t02importExpiredCert() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t02 - Started: importing expired cert.");
        LOG.info("###########################################################");

        int keys = 1;
        int certs = 1;
        int secUntilExpired = 1;

        HashMap<String, CsrCertStructure> keyCert = genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, clientExpire, initializeSoftToken("1234"));
        Thread.sleep(2000); // 2000ms to wait until cert expires

        thrown.expectError(withSignerPrefix(X_CERT_VALIDATION));
        importCerts(keyCert);
        new KeyCertStructCheckRep(keyCertStruct, softToken);

    }

    @Test
    public void t03deleteP12AndRecoverIt() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t03 - Started: deleteP12 and recover it");
        LOG.info("###########################################################");

        int keys = 2;
        int certs = 1;
        int secUntilExpired = 999;

        HashMap<String, CsrCertStructure> keyCert = (genKeysWithSignCerts(
                keys, certs, KeyUsageInfo.SIGNING,
                secUntilExpired, client2, initializeSoftToken("1234")));
        keyCertStruct.putAll(keyCert);

        importCerts(keyCert);

        String keyToMove = getMemberSignInfo(client2);

        String confDir = "build/conf/";
        String tempDir = "build/conf/temp/";
        FileUtils.moveFileToDirectory(
                new File(confDir + keyToMove + ".p12"),
                new File(tempDir), true);
        LOG.debug("Deleted keyFile {}.p12, put copy to {}",
                confDir + keyToMove, tempDir);
        LOG.trace("Devices listed above{}.", listDevices());


        /* TODO: redmine'i:
         * Subject: p12 faili kustutamisel signer ei suuda muudatust
         * arvesse võtta
         *
         * content: p12 faili kadumisel (nt deletimisel ei suuda signer
         * ümber muuta memberi signeerimise serti (MemberSigningInfoRequest).
         * Vaid üritab ikka kasutada võtit, mida confi dir'is ei eksisteeri.
         */
//        softSign(getMemberSignInfo(client2)); // TODO: RM task... throws internal error... should select new sign cert for member

        FileUtils.moveFileToDirectory(new File(tempDir + keyToMove + ".p12"),
                new File(confDir), false);

        softSign(getMemberSignInfo(client2));
        softSign(keyToMove); // must be able to sign with recovered archive key
    }
    @Test
    public void t04deleteP12() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t04 - Started: delete p12 file");
        LOG.info("###########################################################");



        String keyToDelete = getMemberSignInfo(client2);
        softSign(keyToDelete);
//        // TODO: same problem with previous test
//        // should be able to find new key for member

//        keyCertStruct.remove(keyToDelete);
//        String confDir = "build/conf/";
//        FileUtils.forceDelete(new File(confDir + keyToDelete + ".p12"));
//        softSign(getMemberSignInfo(client2));
//        /* to check that signer doesn't isn't
//         * able to use key not in configuration
//         */
//        thrown.expectError(addSignerPrefix(X_KEY_NOT_FOUND));
//        softSign(keyToDelete);
    }

    @Test
    public void t05AskForMemberSignInfoNotInConfiguration()
            throws Exception {
        LOG.info("###########################################################");
        LOG.info("t05 - Started: member not in configuration");
        LOG.info("###########################################################");

        LOG.trace(listDevices());
        thrown.expectError(withSignerPrefix(X_UNKNOWN_MEMBER));
        getMemberSignInfo(createClientId("NOT_MEMBER"));
    }

    @Test
    public void t06getNoMemberCerts() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t06 - Started: Get all certs from member, "
                + "not in configuration. Failure expected");
        LOG.info("###########################################################");

        thrown.expectError(withSignerPrefix(X_UNKNOWN_MEMBER));
        getMemberCerts(createClientId("NOT_MEMBER"));
    }

//    @Test
    public void t07globalConfMembersLost() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t07 - Started: ");
        LOG.info("###########################################################");
        listDevices();
        String confDir = "build/conf/";
        FileUtils.copyFileToDirectory(
                new File("src/testsuite/resources/globalconf_2.xml"),
                new File(confDir));
        listDevices();
        //GlobalConfProvider globalConf =
        //        new GlobalConfImpl(confDir + "globalconf_2.xml");
        //GlobalConf.reload(globalConf);

        listDevices();
        softSign(getMemberSignInfo(client2));
    }

//    @Test


    //    @Test
    //    public void t04() throws Exception {
    //    TODO: Igasugu muud moodulist lähevad tagant ära (riistvaralised, daemonid lähevad katki)
    //    }

    //    @Test
    //    public void t05() throws Exception {
    //    TODO: Sertide revoke'imine
    //    }
}
