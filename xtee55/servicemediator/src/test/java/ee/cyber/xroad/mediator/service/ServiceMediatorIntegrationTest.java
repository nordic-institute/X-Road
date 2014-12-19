package ee.cyber.xroad.mediator.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Random;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.TestResources;

import static ee.cyber.sdsb.common.util.CryptoUtils.loadPkcs12KeyStore;

public class ServiceMediatorIntegrationTest {

    private static final int SERVER_HTTP_PORT = 8060;
    private static final int SERVER_HTTPS_PORT = 8061;

    private static Server dummyService;

    private static final String TEST_CASE_1 = "xrdrpc-andmekogu";
    private static final String TEST_CASE_2 = "xrddl-andmekogu";
    private static final String TEST_CASE_FAULT = "fault";

    private static X509Certificate serverCert;
    private static PrivateKey serverKey;

    private static String responseFile = "sdsb-simple.response";

    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                "src/test/resources/globalconf");

        System.setProperty(MediatorSystemProperties.IDENTIFIER_MAPPING_FILE,
                "src/test/resources/identifiermapping.xml");

        System.setProperty(MediatorSystemProperties.XROAD_PROXY_ADDRESS,
                "http://127.0.0.1:" + SERVER_HTTP_PORT);
        System.setProperty(MediatorSystemProperties.XROAD_URIPROXY_ADDRESS,
                "http://127.0.0.1:" + SERVER_HTTP_PORT);

        System.setProperty(
                SystemProperties.DATABASE_PROPERTIES,
                "src/test/resources/db.properties");

        startServer();
        loadServerKeystore();

        ServiceMediator mediator = new ServiceMediator();
        MediatorServerConf.reload(new IntegrationTestServerConfImpl());
        try {
            mediator.start();

            int port = SERVER_HTTPS_PORT; //MediatorSystemProperties.getServiceMediatorHttpPort();

            //doGet(TEST_CASE_1); // listMethods
            //doGet(TEST_CASE_2); // listMethods
            //doGet("?backend=https://127.0.0.1:8061/" + TEST_CASE_1);
            responseFile = "listMethods.response";
            String clientId =
                    String.format("&sdsbInstance=%s&memberClass=%s&memberCode=%s",
                            "EE", "BUSINESS", "producer_sslauth");
            String url = URLEncoder.encode("https://127.0.0.1:" + port + "/");
            doGet("?backend=" + url + clientId);

            //doGet("?backend=http://127.0.0.1:8060/" + TEST_CASE_FAULT);
            doGet(getWsdlRequestUrl());

            //doPost("consumer", "producer_nossl");
            //doPost("consumer", "producer_sslnoauth");
            //doPost("consumer", "producer_sslnoauth");

            responseFile = "sdsb-simple.response";
            for (int i = 0; i < 0; i++) {
                Thread.sleep(1000);
                doPost("consumer", "producer_nossl");
                Thread.sleep(1000);
                doPost("consumer", "producer_sslnoauth");
                Thread.sleep(1000);
                doPost("consumer", "producer_sslauth");
            }

        } finally {
            mediator.stop();
            mediator.join();

            if (dummyService != null) {
                dummyService.stop();
                dummyService.join();
            }
        }
    }

    private static void loadServerKeystore() throws Exception {
        char[] password = "test".toCharArray();
        KeyStore ks = loadPkcs12KeyStore(
                new File("src/test/resources/root_ca.p12"), password);
        String alias = null;
        Enumeration<String> aliases = ks.aliases();
        if (aliases.hasMoreElements()) {
            alias = aliases.nextElement();
        }

        serverCert = (X509Certificate) ks.getCertificate(alias);
        serverKey = (PrivateKey) ks.getKey(alias, password);

        System.out.println("Server cert (base64)");
        System.out.println(CryptoUtils.encodeBase64(serverCert.getEncoded()));

        try {
            System.out.println(CryptoUtils.encodeBase64(IOUtils.toByteArray(new FileInputStream("src/test/resources/intcert.pem"))));

            //KeyStore ks2 = CryptoUtils.loadKeyStore("pkcs12",
            //        "src/test/resources/intkey.p12", "sslkey".toCharArray());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void doGet(String requestUrl) throws Exception {
        String host = MediatorSystemProperties.getServiceMediatorConnectorHost();
        int port = MediatorSystemProperties.getServiceMediatorHttpPort();

        URL url = new URL("http://" + host + ":" + port + "/" + requestUrl);

        System.out.println("################################################");
        System.out.println("Sending GET request to " + url);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("GET");

        System.out.println("Response: ");
        IOUtils.copy(conn.getInputStream(), System.out);
    }

    private static void doPost(String clientMemberCode,
            String serviceMemberCode) throws Exception {
        String host = MediatorSystemProperties.getServiceMediatorConnectorHost();
        int port = MediatorSystemProperties.getServiceMediatorHttpPort();

        URL url = new URL("http://" + host + ":" + port);

        System.out.println("################################################");
        System.out.println("Sending POST request to " + url);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml");

        OutputStream out = conn.getOutputStream();
        IOUtils.write(getRequest(clientMemberCode, serviceMemberCode), out);

        System.out.println("Response: ");
        IOUtils.copy(conn.getInputStream(), System.out);
    }

    private static void startServer() throws Exception {
        dummyService = new Server();

        Connector httpConnector = new SelectChannelConnector();
        httpConnector.setPort(SERVER_HTTP_PORT);
        httpConnector.setHost("127.0.0.1");
        dummyService.addConnector(httpConnector);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] { new TestServerKeyManager() },
                new TrustManager[] { new TestServerTrustManager() },
                new SecureRandom());
        SslContextFactory cf = new SslContextFactory(true);
        cf.setSslContext(ctx);
        //cf.setNeedClientAuth(true);

        Connector httpsConnector = new SslSelectChannelConnector(cf);
        httpsConnector.setPort(SERVER_HTTPS_PORT);
        httpsConnector.setHost("127.0.0.1");

        dummyService.addConnector(httpsConnector);

        dummyService.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                try {
                    System.out.println("handle()");
                    if (request.getMethod().equals("POST")) {
                        handleSimpleResponse(response);
                    } else {
                        switch (target.substring(1)) {
                            case TEST_CASE_1:
                                sendResponse(response, "listMethods.response");
                                break;
                            case TEST_CASE_2:
                                sendResponse(response,
                                        "invalid-listMethods.response");
                                break;
                            case TEST_CASE_FAULT:
                                sendResponse(response, "fault.response");
                                break;
                        }
                    }
                } finally {
                    baseRequest.setHandled(true);
                }
            }
        });

        dummyService.start();
    }

    private static void handleSimpleResponse(HttpServletResponse response)
            throws IOException {
        response.setContentType(MimeTypes.TEXT_XML);
        try {
            if (responseFile.equals("listMethods.response")) {
                // Random fail
                if (new Random().nextBoolean()) {
                    throw new Exception("List methods failed");
                }
            }

            IOUtils.copy(TestResources.get(responseFile),
                    response.getOutputStream());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static void sendResponse(HttpServletResponse response,
            String responseFile) throws IOException {
        response.setContentType(MimeTypes.TEXT_XML);

        String data = IOUtils.toString(
                new FileInputStream("src/test/resources/" + responseFile));
        System.out.println("sending response: " + data);
        IOUtils.write(data, response.getOutputStream());
    }

    private static String getRequest(String clientMemberCode,
            String serviceMemberCode) throws Exception {
        String data = IOUtils.toString(new FileInputStream(
                "src/test/resources/sdsb-soap-template.request"));

        data = data.replaceFirst("<id:memberCode>consumer</id:memberCode>",
                "<id:memberCode>" + clientMemberCode + "</id:memberCode>");
        data = data.replaceFirst("<id:memberCode>producer</id:memberCode>",
                "<id:memberCode>" + serviceMemberCode + "</id:memberCode>");

        return data;
    }

    private static String getWsdlRequestUrl() {
        return String.format(
                "wsdl?sdsbInstance=%s&memberClass=%s&memberCode=%s",
                "EE", "BUSINESS", "producer_nossl");
    }

    private static class TestServerKeyManager extends X509ExtendedKeyManager {

        private static final String ALIAS = "AuthKeyManager";

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers,
                Socket socket) {
            System.out.println("chooseClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers,
                Socket socket) {
            System.out.println("chooseServerAlias");
            return ALIAS;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[] { serverCert };
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            System.out.println("getClientAliases");
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            System.out.println("getPrivateKey " + alias + " - " + serverKey);
            return serverKey;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            System.out.println("getServerAliases");
            return null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                SSLEngine engine) {
            System.out.println("chooseEngineClientAlias");
            return ALIAS;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                SSLEngine engine) {
            System.out.println("chooseEngineServerAlias");
            return ALIAS;
        }
    }

    private static class TestServerTrustManager implements X509TrustManager {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            System.out.println("getAcceptedIssuers " + serverCert);
            return new X509Certificate[] { serverCert };
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            System.out.println("checkClientTrusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType)
                throws CertificateException {
            // Check for the certificates later in AuthTrustVerifier
            System.out.println("checkServerTrusted");
        }

    }
}
