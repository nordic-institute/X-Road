package ee.cyber.sdsb.asyncsender;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.eclipse.jetty.http.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.message.SoapUtils;
import ee.cyber.sdsb.common.util.HttpSender;
import ee.cyber.sdsb.common.util.StartStop;

import static ee.cyber.sdsb.common.util.AbstractHttpSender.CHUNKED_LENGTH;

final class ProxyClient implements StartStop {
    private static final Logger LOG = LoggerFactory
            .getLogger(ProxyClient.class);

    private static final int CLIENT_TIMEOUT = 300000; // 30 sec.

    private static ProxyClient instance = new ProxyClient();

    static ProxyClient getInstance() {
        return instance;
    }

    private final HttpClient httpClient;

    private URI proxyAddress;

    private ProxyClient() {
        httpClient = createHttpClient();
    }

    SoapMessageImpl send(String contentType, InputStream message)
            throws Exception {
        try (HttpSender sender = new HttpSender(httpClient)) {
            sender.addHeader(SoapUtils.X_IGNORE_ASYNC, "true");
            sender.doPost(getProxyAddress(), message, CHUNKED_LENGTH,
                    contentType);
            return handleResponse(sender);
        }
    }

    @Override
    public void start() throws Exception {
        // Nothing to do.
    }

    @Override
    public void join() throws InterruptedException {
        // Nothing to do.
    }

    @Override
    public void stop() throws Exception {
        httpClient.getConnectionManager().shutdown();
    }

    private URI getProxyAddress() throws Exception {
        if (proxyAddress == null) {
            proxyAddress = new URI("http://127.0.0.1:"
                    + SystemProperties.getClientProxyHttpPort());
        }

        return proxyAddress;
    }

    private SoapMessageImpl handleResponse(HttpSender sender) throws Exception {
        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(sender.getResponseContentType());

        MimeStreamParser parser = new MimeStreamParser(config);
        ContentHandler handler = new ContentHandler(parser);

        parser.setContentHandler(handler);
        parser.parse(sender.getResponseContent());

        Soap responseSoap = handler.getResponseSoap();
        checkForFaultResponse(responseSoap);

        return (SoapMessageImpl) responseSoap;
    }

    private static void checkForFaultResponse(Soap responseSoap) {
        if (responseSoap != null && responseSoap instanceof SoapFault) {
            SoapFault soapFault = (SoapFault) responseSoap;
            LOG.error("checkForFaultResponse() - got fault message: {}",
                    soapFault.getXml());
            throw soapFault.toCodedException();
        }
    }

    private static HttpClient createHttpClient() {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpParams params = httpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(params, CLIENT_TIMEOUT);

        // Disable request retry
        httpClient.setHttpRequestRetryHandler(
                new DefaultHttpRequestRetryHandler(0, false));

        return httpClient;
    }

    private class ContentHandler extends AbstractContentHandler {

        private Soap responseSoap;
        private MimeStreamParser parser;

        ContentHandler(MimeStreamParser parser) {
            this.parser = parser;
        }

        Soap getResponseSoap() {
            return responseSoap;
        }

        @Override
        public void startMultipart(BodyDescriptor bd) throws MimeException {
            parser.setFlat();
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            // If the response is an XML, assume it is a Soap message.
            // Everything else goes to dev/null.
            if (MimeTypes.TEXT_XML.equalsIgnoreCase(bd.getMimeType())) {
                responseSoap = new SoapParserImpl().parse(bd.getMimeType(),
                                bd.getCharset(), is);
            } else {
                IOUtils.copy(is, NullOutputStream.NULL_OUTPUT_STREAM);
            }
        }
    }
}
