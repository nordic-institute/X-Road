/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.AbstractHttpSender;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.conf.SigningCtx;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.ByteOrderMark;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;

/**
 * Base class for a message test case.
 */
@Slf4j
public class MessageTestCase {

    private static final int DEFAULT_CLIENT_TIMEOUT = 45000;
    public static final String QUERIES_DIR = "src/test/queries";

    protected Message sentRequest;
    protected Message sentResponse;
    protected Message receivedResponse;

    protected String httpMethod = "POST";

    @Getter
    protected String requestFileName;
    @Getter
    protected String responseFile;

    protected boolean addUtf8BomToRequestFile = false;
    protected boolean addUtf8BomToResponseFile = false;

    protected String requestContentType = MimeTypes.TEXT_XML_UTF8;

    @Getter
    protected String responseContentType = MimeTypes.TEXT_XML_UTF8;

    protected String responseServiceContentType;

    protected String url = "http://localhost:"
            + SystemProperties.getClientProxyHttpPort();
    protected final Map<String, String> requestHeaders = new HashMap<>();

    @Getter
    @Setter
    private String id;

    @Getter
    private String queryId;

    @Getter
    @Setter
    private boolean failed = false;

    /**
     * Adds a new HTTP header to the request.
     * @param name the name of the header
     * @param value the value of the header
     */
    public void addRequestHeader(String name, String value) {
        requestHeaders.put(name, value);
    }

    /**
     * @param providerName the name of the provider
     * @return the address of the provider with the given name
     */
    public String getProviderAddress(String providerName) {
        return "127.0.0.1";
    }

    /**
     * @param service the service name
     * @return the address if the service with the given name
     */
    public String getServiceAddress(String service) {
        return "http://127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service : "");
    }

    /**
     * @param service the service ID
     * @return the service address of the address with the given ID
     */
    public String getServiceAddress(ServiceId service) {
        return "http://127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service.getServiceCode() : "");
    }

    /**
     * @param sender the sender
     * @return the signing context of the sender with the given name
     */
    public SigningCtx getSigningCtx(String sender) {
        return null;
    }

    /**
     * @param service the service ID
     * @return true if the service with the given ID exists
     */
    public boolean serviceExists(ServiceId service) {
        return true;
    }

    /**
     * @param sender the sender client ID
     * @param service the service ID
     * @return true if the query from sender to service is allowed
     */
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return true;
    }

    /**
     * @param service the service ID
     * @return the disabled notice of the service with the given ID
     */
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    /**
     * @return the server proxy HTTP handler
     */
    public AbstractHandler getServerProxyHandler() {
        return null;
    }

    /**
     * @return the service HTTP handler
     */
    public AbstractHandler getServiceHandler() {
        return null;
    }

    /**
     * @return the response service content type
     */
    public String getResponseServiceContentType() {
        return responseServiceContentType != null
                ? responseServiceContentType : getResponseContentType();
    }

    protected URI getClientUri() throws URISyntaxException {
        return new URI(url);
    }

    /**
     * Performs the request and validates the response.
     * @throws Exception in case of any unexpected errors
     */
    public void execute() throws Exception {
        startUp();
        generateQueryId();

        CloseableHttpAsyncClient client = getClient();
        client.start();

        // Request input stream is read twice, once for recording,
        // second time for HTTP request.
        Pair<String, InputStream> requestInput = getRequestInput(false);

        try (InputStream is = requestInput.getRight()) {
            sentRequest = new Message(is, requestInput.getLeft()).parse();
        }

        AsyncHttpSender sender = new AsyncHttpSender(client);
        // Needed by some test cases
        sender.addHeader(HEADER_HASH_ALGO_ID, DEFAULT_DIGEST_ALGORITHM_ID);

        // Get the input again.
        requestInput = getRequestInput(addUtf8BomToRequestFile);

        try (InputStream is = requestInput.getRight()) {
            for (Entry<String, String> e : requestHeaders.entrySet()) {
                sender.addHeader(e.getKey(), e.getValue());
            }

            if ("post".equalsIgnoreCase(httpMethod)) {
                sender.doPost(getClientUri(), is, CHUNKED_LENGTH,
                        requestInput.getLeft());
            } else {
                sender.doGet(getClientUri());
            }

            sender.waitForResponse(DEFAULT_CLIENT_TIMEOUT);
        }

        try {
            receivedResponse = extractResponse(sender);

            if (sentRequest != null && sentRequest.getSoap() != null
                    && sentRequest.getSoap() instanceof SoapMessageImpl) {
                sentResponse = receivedResponse;
            }
        } finally {
            sender.close();
            client.close();
            closeDown();
        }

        if (failed) {
            throw new Exception("Test failed in previous stage");
        }

        if (receivedResponse.getSoap() != null) {
            log.debug("Validating SOAP message\n{}",
                    receivedResponse.getSoap().getXml());
        }


        if (receivedResponse.isFault()) {
            log.debug("Validating fault: {}, {}",
                    ((SoapFault) receivedResponse.getSoap()).getCode(),
                    ((SoapFault) receivedResponse.getSoap()).getString());
            validateFaultResponse(receivedResponse);
            return;
        }

        if (!receivedResponse.isResponse()) {
            throw new Exception("Received SOAP message is not a response");
        }

        if (sentResponse != null
                && !checkConsistency(sentResponse, receivedResponse)) {
            throw new Exception(
                    "Received response is not the same as sent response");
        }

        log.debug("Validating normal response");
        validateNormalResponse(receivedResponse);
    }

    protected void startUp() throws Exception {
        KeyConf.reload(new TestSuiteKeyConf());
        ServerConf.reload(new TestSuiteServerConf());
        GlobalConf.reload(new TestSuiteGlobalConf());
    }

    protected void closeDown() throws Exception {
    }

    protected Message extractResponse(AbstractHttpSender sender) throws Exception {
        return new Message(sender.getResponseContent(),
                sender.getResponseContentType()).parse();
    }

    protected void validateNormalResponse(Message response)
            throws Exception {
        throw new Exception("Received normal response, fault was expected");
    }

    protected void validateFaultResponse(Message response)
            throws Exception {
        throw new Exception(
                "Received fault response, answer was expected");
    }

    /**
     * Returns pair of <contenttype, inputstream> containing the request.
     */
    protected Pair<String, InputStream> getRequestInput(
            boolean addUtf8Bom) throws Exception {
        if (requestFileName != null) {
            String file = QUERIES_DIR + "/" + requestFileName;
            return Pair.of(requestContentType, getQueryInputStream(
                    file, addUtf8Bom));
        }

        throw new IllegalArgumentException("requestFileName must be specified");
    }

    protected InputStream getQueryInputStream(String fileName,
            boolean addUtf8Bom) throws Exception {
        InputStream is = changeQueryId(new FileInputStream(fileName));

        return addUtf8Bom ? addUtf8Bom(is) : is;
    }

    private InputStream addUtf8Bom(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        out.write(ByteOrderMark.UTF_8.getBytes());
        out.write(IOUtils.toByteArray(is));

        return new ByteArrayInputStream(out.toByteArray());
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

    protected void onServiceReceivedHttpRequest(HttpServletRequest request) throws Exception {
        // NOP
    }

    protected void onServiceReceivedRequest(Message receivedRequest) throws Exception {
        if (!checkConsistency(sentRequest, receivedRequest)) {
            log.error("Sent request and received request are not "
                    + "consistent, sending fault response.");
            failed = true;
        }
    }

    private static boolean checkConsistency(Message m1, Message m2) {
        return m1.checkConsistency(m2);
    }

    void onSendResponse(Message response) {
        this.sentResponse = response;
    }

    protected final String errorCode(String... parts) {
        return StringUtils.join(parts, ".");
    }

    protected final void assertErrorCode(String... parts) {
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

    protected final void assertErrorCodeStartsWith(String... parts) {
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
        data = data.replaceAll("\\<xroad:id\\>(.?)+\\<\\/xroad:id\\>",
                "<xroad:id>" + queryId + "</xroad:id>");
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }
}
