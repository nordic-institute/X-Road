package ee.ria.xroad.common.request;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import static ee.ria.xroad.common.util.CryptoUtils.loadPkcs12KeyStore;

/**
 * Management request sender test program.
 */
public final class ManagementMessageTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementMessageTest.class);

    private ManagementMessageTest() {
    }

    /**
     * Main program entry point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                "../signer/src/test/resources/globalconf");

        try {
            ManagementRequestClient.getInstance().start();

            // SOAP header field values
            String userId = "userid";
            ClientId sender =
                    ClientId.create("EE", "BUSINESS", "consumer");
            ClientId receiver = GlobalConf.getManagementRequestService();

            ManagementRequestSender requestSender =
                    new ManagementRequestSender(userId, sender, receiver);
            /*{
                @Override
                protected URI getCentralServiceURI() throws Exception {
                    return new URI("https://iks2-central.cyber.ee:8443"
                            + "/center-service/");
                }

                @Override
                protected URI getSecurityServerURI() throws Exception {
                    return new URI("http://localhost:"
                            + PortNumbers.CENTER_SERVICE_HTTP_PORT);
                }
            };*/

            sendAuthCertRegRequest(requestSender);
            //sendAuthCertDeletionRequest(requestSender);
            //sendClientRegRequest(requestSender);
            //sendClientDeletionRequest(requestSender);

        } catch (CodedException e) {
            LOG.error("Error when sending message", e);
            LOG.error("Detail: {}", e.getFaultDetail());
        } catch (Exception e) {
            LOG.error("Error when sending message", e);
        } finally {
            ManagementRequestClient.getInstance().stop();
        }
    }

    private static void sendAuthCertDeletionRequest(
            ManagementRequestSender sender) throws Exception {
        SecurityServerId securityServerId = SecurityServerId.create(
                "EE", "BUSINESS", "servicemember2", "server");
        byte[] authCert = {0x01, 0x02, 0x03, 0x04};

        sender.sendAuthCertDeletionRequest(securityServerId, authCert);
    }

    private static void sendAuthCertRegRequest(ManagementRequestSender sender)
            throws Exception {
        SecurityServerId securityServerId = SecurityServerId.create(
                "EE", "BUSINESS", "servicemember2", "server");

        String address = "http://foo.bar.baz:12345";
        byte[] authCert = getAuthCert();

        ClientId owner = securityServerId.getOwner();

        sender.sendAuthCertRegRequest(securityServerId, address, authCert);
    }

    private static void sendClientRegRequest(ManagementRequestSender sender)
            throws Exception {
        SecurityServerId securityServerId = SecurityServerId.create(
                "EE", "BUSINESS", "servicemember2", "server");

        ClientId clientId = ClientId.create(
                "EE", "BUSINESS", "theClient");

        sender.sendClientRegRequest(securityServerId, clientId);
    }


    private static void sendClientDeletionRequest(
            ManagementRequestSender sender) throws Exception {
        SecurityServerId securityServerId = SecurityServerId.create(
                "EE", "BUSINESS", "servicemember2", "server");

        ClientId clientId = ClientId.create(
                "EE", "BUSINESS", "theClient");

        sender.sendClientDeletionRequest(securityServerId, clientId);
    }

    private static byte[] getAuthCert() throws Exception {
        File file = new File("../proxy/src/test/producer.p12");
        char[] password = "test".toCharArray();

        KeyStore ks = loadPkcs12KeyStore(file, password);

        X509Certificate cert = (X509Certificate) ks.getCertificate("producer");
        return cert.getEncoded();
    }
}
