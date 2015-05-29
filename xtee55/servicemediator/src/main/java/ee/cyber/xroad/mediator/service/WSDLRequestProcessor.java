package ee.cyber.xroad.mediator.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSession;
import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.protocol.HttpContext;
import org.eclipse.jetty.http.MimeTypes;

import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.AbstractMediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;
import ee.cyber.xroad.mediator.message.V5XRoadListMethods;
import ee.cyber.xroad.mediator.message.V5XRoadNamespaces;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.MimeUtils;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.message.SoapUtils.*;

@Slf4j
class WSDLRequestProcessor implements MediatorMessageProcessor {

    /**
     * List of known namespaces to use when sending listMethods request.
     * Some backends might only accept a particular namespace, and since
     * we have no way of knowing which backend supports which namespace,
     * we try to send the ListMethods with different namespace until we get
     * a valid response or try all namespaces.
     */
    private static final String[] NAMESPACES = {
            V5XRoadNamespaces.NS_DL_EE,
            V5XRoadNamespaces.NS_DL_EU,
            V5XRoadNamespaces.NS_DL_XX,
            V5XRoadNamespaces.NS_RPC
        };

    /**
     * After ListMethods is successful for a backend, we remember the namespace
     * for future use. Next time the previously successful namespace is
     * tried first.
     */
    private static final Map<URI, String[]> NAMESPACES_TO_URI = new HashMap<>();

    private final HttpClientManager httpClientManager;

    private URI targetAddress;

    WSDLRequestProcessor(String target, HttpClientManager httpClientManager)
            throws Exception {
        this.httpClientManager = httpClientManager;

        this.targetAddress = getTargetAddress(
                AbstractMediatorMessageProcessor.stripSlash(target));
        if (this.targetAddress == null) {
            throw new IllegalArgumentException(
                    "Target address must not be null");
        }
    }

    WSDLRequestProcessor(URI backendUri, HttpClientManager httpClientManager)
            throws Exception {
        this.httpClientManager = httpClientManager;

        this.targetAddress = backendUri;
        if (this.targetAddress == null) {
            throw new IllegalArgumentException(
                    "Target address must not be null");
        }
    }

    @Override
    public void process(MediatorRequest request, MediatorResponse response)
            throws Exception {
        if (targetAddress == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Target address not specified");
        }

        log.info("Processing WSDL request '{}'...", targetAddress);

        for (String nsUri : getNamespaceUris(targetAddress)) {
            if (trySendReceive(nsUri, response)) {
                rememberNamespaceUri(nsUri, targetAddress);
                return;
            }
        }

        log.error("Could not get successful response from backend ({}) "
                + "using any known namespace uri", targetAddress);
        throw new CodedException(X_INTERNAL_ERROR, "ListMethods failed");
    }

    private boolean trySendReceive(String nsUri, MediatorResponse response)
            throws Exception {
        try (AsyncHttpSender sender =
                new AsyncHttpSender(httpClientManager.getDefaultHttpClient())) {
            try {
                sendRequest(sender, nsUri, targetAddress);
                handleResponse(sender, targetAddress, response);
            } catch (Exception e) {
                log.error("ListMethods failed (NS = {}, URL = {}): {}",
                        new Object[] {nsUri, targetAddress, e});
                return false;
            }
        }

        return true;
    }

    private void sendRequest(AsyncHttpSender sender, String nsUri,
            URI address) throws Exception {
        log.debug("Sending ListMethods request (NS = {}) to {}...", nsUri,
                address);

        sender.addHeader("SOAPAction", "");

        sender.setAttribute(ServerTrustVerifier.class.getName(),
                new TrustVerifier());

        sender.doPost(address, V5XRoadListMethods.getXmlAsString(nsUri),
                MimeTypes.TEXT_XML);
        sender.waitForResponse(AsyncHttpSender.DEFAULT_TIMEOUT_SEC);
    }

    private static void handleResponse(AsyncHttpSender sender, URI address,
            MediatorResponse response) throws Exception {
        verifyResponseContentType(sender.getResponseContentType());

        String charset = getCharset(sender.getResponseContentType());
        SOAPMessage soap = readSoap(sender.getResponseContent(), charset);

        log.debug("ListMethods response: {}", getXml(soap, charset));

        List<String> methods = listMethods(soap.getSOAPBody());

        // Add implicit get{Service|Producer}ACL methods to output.
        methods.add("dummyProducer.getServiceACL");
        methods.add("dummyProducer.getProducerACL");

        DummyWSDLCreator creator = new DummyWSDLCreator(address.toString());
        writeWSDL(creator.create(methods), response.getOutputStream());
    }

    private static void verifyResponseContentType(String responseContentType) {
        String contentType = MimeUtils.getBaseContentType(responseContentType);
        if (!MimeTypes.TEXT_XML.equalsIgnoreCase(contentType)) {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Invalid content type '%s'", contentType);
        }
    }

    private static SOAPMessage readSoap(InputStream is, String charset)
            throws Exception {
        SOAPMessage soap = createSOAPMessage(is, charset);
        if (soap.getSOAPBody() != null
                && soap.getSOAPBody().getFault() != null) {
            throw new SoapFault(
                    soap.getSOAPBody().getFault()).toCodedException();
        }

        return soap;
    }

    private static String getCharset(String contentType) {
        String charset = MimeUtils.getCharset(contentType);
        return charset != null ? charset : StandardCharsets.UTF_8.name();
    }

    private static List<String> listMethods(SOAPBody soapBody) {
        List<String> methods = new ArrayList<>();

        SOAPElement responseElement = getFirstChild(soapBody);
        if (responseElement == null) {
            throw new CodedException(X_MALFORMED_SOAP, "Body has no children");
        }

        if (!responseElement.getLocalName().endsWith("listMethodsResponse")) {
            throw new CodedException(X_MALFORMED_SOAP,
                    "Unexpected response element: %s",
                    responseElement.getLocalName());
        }

        SOAPElement dataElement = getFirstChild(responseElement);
        if (dataElement == null) {
            throw new CodedException(X_MALFORMED_SOAP,
                    "Response element is missing data element");
        }

        List<SOAPElement> items = getChildElements(dataElement);
        for (SOAPElement item : items) {
            if (item.getLocalName().equals("item")) {
                methods.add(item.getValue());
            }
        }

        return methods;
    }

    private URI getTargetAddress(String producerName) throws Exception {
        ClientId clientId = clientId(producerName);
        log.debug("getTargetAddress({})", clientId);

        String address = MediatorServerConf.getBackendURL(clientId);
        if (address != null) {
            return new URI(address);
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Could not find target address for '%s'", producerName);
    }

    private static String[] getNamespaceUris(URI targetAddress) {
        if (NAMESPACES_TO_URI.containsKey(targetAddress)) {
            return NAMESPACES_TO_URI.get(targetAddress);
        }

        return NAMESPACES;
    }

    private static void rememberNamespaceUri(String nsUri, URI address) {
        String[] sequence = new String[NAMESPACES.length];
        sequence[0] = nsUri;

        int idx = 1;
        for (String otherNsUri : NAMESPACES) {
            if (!otherNsUri.equals(nsUri)) {
                sequence[idx++] = otherNsUri;
            }
        }

        NAMESPACES_TO_URI.put(address, sequence);
    }

    private static ClientId clientId(String producer) throws Exception {
        return IdentifierMapping.getInstance().getClientId(producer);
    }

    private static void writeWSDL(Definition wsdl, OutputStream out)
            throws Exception {
        WSDLFactory wsdlFactory =
                WSDLFactory.newInstance("com.ibm.wsdl.factory.WSDLFactoryImpl");
        wsdlFactory.newWSDLWriter().writeWSDL(wsdl, out);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class TrustVerifier implements ServerTrustVerifier {

        @Override
        public void checkServerTrusted(HttpContext httpContext,
                SSLSession session) throws Exception {
            // Simply make sure the server sent its certificate...
            X509Certificate[] certs =
                    (X509Certificate[]) session.getPeerCertificates();
            if (certs.length == 0) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Could not get peer certificates");
            }
        }
    }
}
