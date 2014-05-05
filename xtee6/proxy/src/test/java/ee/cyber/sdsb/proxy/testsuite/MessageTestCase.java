package ee.cyber.sdsb.proxy.testsuite;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.bouncycastle.operator.DigestCalculator;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.conf.VerificationCtx;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.conf.SigningCtx;

public class MessageTestCase {
    private static final Logger LOG = LoggerFactory.getLogger(
            MessageTestCase.class);

    private static final int DEFAULT_CLIENT_TIMEOUT = 45000;
    public static final String QUERIES_DIR = "src/test/queries";

    protected Message sentRequest;
    protected Message sentResponse;
    protected Message receivedResponse;

    protected String httpMethod = "POST";
    protected String requestFileName;
    protected String responseFileName;
    protected String requestContentType = MimeUtils.TEXT_XML_UTF8;
    protected String responseContentType = MimeUtils.TEXT_XML_UTF8;
    protected String url = "http://localhost:" + PortNumbers.CLIENT_HTTP_PORT;
    protected final Map<String, String> requestHeaders = new HashMap<>();

    private String id;
    private String queryId;
    private boolean testFailed = false;

    public void addRequestHeader(String name, String value) {
        requestHeaders.put(name, value);
    }

    public String getProviderAddress(String providerName) {
        return "127.0.0.1";
    }

    public String getServiceAddress(String service) {
        return "http://127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service : "");
    }

    public String getServiceAddress(ServiceId service) {
        return "http://127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service.getServiceCode() : "");
    }

    public SigningCtx getSigningCtx(String sender) {
        return null;
    }

    public VerificationCtx getVerificationCtx() {
        return null;
    }

    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return Collections.emptySet();
    }

    public Set<SecurityCategoryId> getProvidedCategories() {
        return Collections.emptySet();
    }

    public boolean serviceExists(ServiceId service) {
        return true;
    }

    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return true;
    }

    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    public AbstractHandler getServerProxyHandler() {
        return null;
    }

    public AbstractHandler getServiceHandler() {
        return null;
    }

    public String getResponseFile() {
        return responseFileName;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean hasFailed() {
        return testFailed;
    }

    public void setFailed(boolean failed) {
        testFailed = failed;
    }

    public void execute() throws Exception {
        startUp();
        generateQueryId();

        CloseableHttpAsyncClient client = getClient();
        client.start();

        // Request input stream is read twice, once for recording,
        // second time for HTTP request.
        Pair<String, InputStream> requestInput = getRequestInput();
        try (InputStream is = requestInput.getRight()) {
            sentRequest = new Message(is, requestInput.getLeft());
        }

        AsyncHttpSender sender = new AsyncHttpSender(client);

        // Get the input again.
        requestInput = getRequestInput();
        try (InputStream is = requestInput.getRight()) {
            for (Entry<String, String> e : requestHeaders.entrySet()) {
                sender.addHeader(e.getKey(), e.getValue());
            }

            if ("post".equalsIgnoreCase(httpMethod)) {
                sender.doPost(new URI(url), is, requestInput.getLeft());
            } else {
                sender.doGet(new URI(url));
            }

            sender.waitForResponse(DEFAULT_CLIENT_TIMEOUT);
        }

        try {
            receivedResponse = new Message(sender.getResponseContent(),
                    sender.getResponseContentType());

            if (sentRequest != null && sentRequest.getSoap() != null
                    && sentRequest.getSoap() instanceof SoapMessageImpl
                    && ((SoapMessageImpl) sentRequest.getSoap()).isAsync() &&
                    !requestHeaders.containsKey(SoapMessageImpl.X_IGNORE_ASYNC)) {
                sentResponse = receivedResponse;
            }
        } finally {
            sender.close();
            client.close();
            closeDown();
        }

        if (testFailed) {
            throw new Exception("Test failed in previous stage");
        }

        LOG.debug("Validating SOAP message\n{}",
                receivedResponse.getSoap().getXml());

        if (receivedResponse.isFault()) {
            LOG.debug("Validating fault: {}, {}",
                    ((SoapFault) receivedResponse.getSoap()).getCode(),
                    ((SoapFault) receivedResponse.getSoap()).getString());
            validateFaultResponse(receivedResponse);
            return;
        }

        if (!receivedResponse.isResponse()) {
            throw new Exception("Received SOAP message is not a response");
        }

        if (!checkConsistency(sentResponse, receivedResponse)) {
            throw new Exception(
                    "Received response is not the same as sent response");
        }

        LOG.debug("Validating normal response");
        validateNormalResponse(receivedResponse);
    }

    protected void startUp() throws Exception {
        KeyConf.reload(new TestKeyConf());
        ServerConf.reload(new TestServerConf());
        GlobalConf.reload(new TestGlobalConf());
    }

    protected void closeDown() throws Exception {
    }

    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        throw new Exception("Received normal response were fault was expected");
    }

    protected void validateFaultResponse(Message receivedResponse)
            throws Exception {
        throw new Exception(
                "Received fault response were normal answer was expected");
    }

    /**
     * Returns pair of <contenttype, inputstream> containing the request.
     */
    protected Pair<String, InputStream> getRequestInput() throws Exception {
        if (requestFileName != null) {
            String queryFileName = QUERIES_DIR + "/" + requestFileName;
            return Pair.of(requestContentType,
                    getQueryInputStream(queryFileName));
        }

        throw new IllegalArgumentException("requestFileName must be specified");
    }

    protected InputStream getQueryInputStream(String fileName)
            throws Exception {
        return changeQueryId(new FileInputStream(fileName));
    }

    protected CloseableHttpAsyncClient getClient() throws Exception {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(getClientTimeout())
                .setSoTimeout(30000)
                .build();

        ConnectingIOReactor ioReactor =
                new DefaultConnectingIOReactor(ioReactorConfig);

        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor);

        connManager.setMaxTotal(1);
        connManager.setDefaultMaxPerRoute(1);

        builder.setConnectionManager(connManager);

        return builder.build();
    }

    protected int getClientTimeout() {
        return DEFAULT_CLIENT_TIMEOUT;
    }

    protected void generateQueryId() throws Exception {
        long seed = System.currentTimeMillis();
        DigestCalculator dc = CryptoUtils.createDigestCalculator(
                CryptoUtils.MD5_ID);
        dc.getOutputStream().write(
                ByteBuffer.allocate(8).putLong(seed).array());
        dc.getOutputStream().close();
        this.queryId = CryptoUtils.encodeHex(dc.getDigest());
    }

    void onReceiveRequest(Message receivedRequest) throws Exception {
        if (!checkConsistency(sentRequest, receivedRequest)) {
            LOG.error("Sent request and received request are not " +
                    "consistent, sending fault response.");
            testFailed = true;
        }
    }

    private static boolean checkConsistency(Message m1, Message m2) {
        return m1.checkConsistency(m2);
    }

    void onSendResponse(Message sentResponse) {
        this.sentResponse = sentResponse;
    }

    protected final String errorCode(String ...parts) {
        return StringUtils.join(parts, ".");
    }

    protected final void assertErrorCode(String ...parts) {
        String errorCode = errorCode(parts);

        if (!receivedResponse.isFault()) {
            throw new RuntimeException("Fault required, normal message received");
        }

        SoapFault fault = (SoapFault) receivedResponse.getSoap();
        if (!errorCode.equals(fault.getCode())) {
            throw new RuntimeException("Error code " + errorCode
                    + " required, " + fault.getCode() + " received");
        }
    }

    protected final void assertErrorCodeStartsWith(String ...parts) {
        String errorCode = StringUtils.join(parts, ".");

        if (!receivedResponse.isFault()) {
            throw new RuntimeException("Fault required, normal message received");
        }

        SoapFault fault = (SoapFault) receivedResponse.getSoap();
        if (!fault.getCode().startsWith(errorCode)) {
            throw new RuntimeException("Error code " + errorCode
                    + " required, " + fault.getCode() + " received");
        }
    }

    protected InputStream changeQueryId(InputStream is) throws Exception {
        String data = IOUtils.toString(is, StandardCharsets.UTF_8);
        data = data.replaceAll("\\<sdsb:id\\>(.?)+\\<\\/sdsb:id\\>",
                "<sdsb:id>" + queryId + "</sdsb:id>");
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }
}
