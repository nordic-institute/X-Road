package ee.cyber.sdsb.signer.randomtester;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.CsrCertStructure;
import ee.cyber.sdsb.signer.KeyCertStructCheckRep;
import ee.cyber.sdsb.signer.TestCase;
import ee.cyber.sdsb.signer.dummies.certificateauthority.CAMock;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.GenerateCertRequestResponse;

import static ee.cyber.sdsb.signer.TestSuiteHelper.*;
import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RandomTester extends TestCase {

    private static final Logger LOG =
            LoggerFactory.getLogger(RandomTester.class);
//    @Rule
//    public ExpectedCodedException thrown = ExpectedCodedException.none();

    private static HashMap<String, CsrCertStructure> keyCertStruct =
            new HashMap<>();

    private static HashMap<String, CsrCertStructure> keyCertsToImport =
            new HashMap<>(); /** As it is not required only import certs which
            * have active csr we need a way how to stash certs created,
            * but not imported.
            */

    private static final TokenInfo token = initializeSoftToken("1234");

    private int nrOfLoops = 40;

    private KeyUsageInfo certType = KeyUsageInfo.SIGNING;

    private ArrayList<String> seedList = new ArrayList<>();

    private String[] actions =
        {"CreateKey", "CreateCsr", "CreateCert", "ImportCert"};
    //            "Sign", "DelKey", "DelCsr", "DelCert"};

    private ClientId[] members =
        {createClientId("servicemember"), createClientId("servicemember2")};

    @Test
    public void t01randomTestSoftToken() throws Exception {
        LOG.info("###########################################################");
        LOG.info("t01 - Random test initiated");
        LOG.info("###########################################################");

        // init some counters

        activateDevice(token, true);

        Random rand = new Random();
        String task = "";
        String randKey = "";
        String randKeyImport = "";
        ClientId client = members[rand.nextInt(members.length)];

        try {
            for (int i = 0; i < nrOfLoops; i++) {
                listDevices();
                task = actions[rand.nextInt(actions.length)];
                client = members[rand.nextInt(members.length)];

                randKey = "";
                randKeyImport = "";

                int keySetSize = keyCertStruct.keySet().size();
                int importKeySetSize = keyCertsToImport.keySet().size();
                if (keySetSize == 0) {
                    task = "CreateKey";
                } else {
                    randKey = keyCertStruct.keySet().toArray(
                            new String[0])[rand.nextInt(keySetSize)];
                    if (importKeySetSize != 0) {
                        randKeyImport = keyCertsToImport.keySet().toArray(
                                new String[0])[rand.nextInt(importKeySetSize)];
                    }
                }

                switch (task) {
                // TODO: ebaproportsionaalselt palju tehakse võtmeid.
                case "CreateKey":
                    LOG.info("Creating new KEY.");
                    KeyInfo keyReq = generateKey(token);
                    keyCertStruct.put(keyReq.getId(),
                            new CsrCertStructure(token, keyReq));
                    break;
                case "CreateCsr":
                    LOG.info("Creating new CSR.");
                    KeyInfo keyReq1 =
                            keyCertStruct.get(randKey).getTokenKeyReq(token);

                    GenerateCertRequestResponse csrReqest =
                            generateCertRequest(token, keyReq1,
                                    client, KeyUsageInfo.SIGNING,
                                    subjectName(client));
                    keyCertStruct.get(randKey).addToCsrList(
                            csrReqest.getCertRequest(), client);
                    break;
                case "CreateCert":
                    if (keyCertStruct.get(randKey).getCsrMemList().size() == 0) {
                        continue;
                    }
                    LOG.info("CA is creating new cert.");
                    Collection<byte[]> csrCollection =
                            keyCertStruct.get(randKey).getCsrMemList().values();
                    int randNum = rand.nextInt(csrCollection.size());

                    List<Object> randCsrReq = Arrays.asList(csrCollection.toArray());
                    byte[] newCert = CAMock.certRequest(
                            (byte[]) randCsrReq.get(randNum), certType, 9999);
                    keyCertsToImport.put(
                            randKey, new CsrCertStructure("foobar" + Math.random(),
                                    newCert, "cert", client));
                    break;
                case "Sign":
                    LOG.info("Signing content.");

                    break;
                case "DelKey":
                    break;
                case "DelCsr":
                    break;
                case "DelCert":
                    break;
                case "ImportCert":
                    if (null == keyCertsToImport.get(randKeyImport)) {
                        continue;
                    } else if (keyCertsToImport.get(
                            randKeyImport).getCertListCerts().size() == 0) {
                        continue;
                    }
                    LOG.info("Importing cert.");

                    int randIndex = rand.nextInt(keyCertsToImport.get(
                            randKeyImport).getCertListCerts().size());

                    // TODO: Wasn't it pseudo random?????????????????????????
                    byte[] newCertI = keyCertsToImport.get(
                            randKeyImport).getCertListCerts().get(randIndex);
                    ClientId member = keyCertsToImport.get(
                            randKeyImport).getCertListMember().get(randIndex);

                    String importedCertKey = importCert(newCertI);

                    assertEquals("Imported Cert to key doesn't match expected.",
                            randKeyImport, importedCertKey);
                    // Update Test conf
                    keyCertStruct.get(randKeyImport).delCsr(member);
                    keyCertsToImport.get(randKeyImport).delCert(newCertI);
                    keyCertStruct.get(randKeyImport).addToCertList(
                            newCertI, member);

                    break;
                default:
                    throw new Exception("Cannot happen");
                    // TODO: more cases - faulty actions
                }
                LOG.trace(listDevices());
                seedList.add(task);

                new KeyCertStructCheckRep(keyCertStruct, token, seedList);
            }
        } catch (Exception ex) {
            seedList.add(task);
            LOG.error("Exception cause was:\n{}\n"
                    + "Stacktrace: \n{}", ex.getCause(), ex.fillInStackTrace());
            throw new Exception("Test finished UNsuccessfully.");
        }
    }

    //    private ClientId getAllClientsWithCsr() {
    //
    //    }
}
