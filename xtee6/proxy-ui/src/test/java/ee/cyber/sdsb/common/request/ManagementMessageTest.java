package ee.cyber.sdsb.common.request;

import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.CryptoUtils;

public class ManagementMessageTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementMessageTest.class);

    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.GLOBAL_CONFIGURATION_FILE,
                "../signer/src/test/resources/globalconf.xml");

        try {
            ManagementRequestClient.getInstance().start();

            // SOAP header field values
            String userId = "userid";
            ClientId sender =
                    ClientId.create("EE", "BUSINESS", "consumer");
            ClientId receiver = GlobalConf.getManagementRequestService();

            ManagementRequestSender requestSender =
                    new ManagementRequestSender(userId, sender, receiver) {
                @Override
                protected URI getCentralServiceURI() throws Exception {
                    return new URI("https://iks2-central.cyber.ee:8443" +
                            "/center-service/");
                }

                @Override
                protected URI getSecurityServerURI() throws Exception {
                    return new URI("http://localhost:" +
                            PortNumbers.CENTER_SERVICE_HTTP_PORT);
                }
            };

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
        byte[] authCert = { 0x01, 0x02, 0x03, 0x04 };

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
        String fileName = "../proxy/src/test/producer.p12";
        char[] password = "test".toCharArray();

        KeyStore ks = CryptoUtils.loadKeyStore("pkcs12", fileName, password);

        X509Certificate cert = (X509Certificate) ks.getCertificate("producer");
        return cert.getEncoded();
    }
}
