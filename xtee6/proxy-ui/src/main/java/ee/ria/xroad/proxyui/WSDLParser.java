/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxyui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Binding;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLLocator;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapParser;
import ee.ria.xroad.common.message.SoapParserImpl;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Contains utility methods for extracting information from WSDL files.
 */
@Slf4j
@SuppressWarnings("unchecked")
public final class WSDLParser {

    private static final String SOAP_NAMESPACE =
            "http://schemas.xmlsoap.org/wsdl/soap/";

    private static final String SOAP12_NAMESPACE =
            "http://schemas.xmlsoap.org/wsdl/soap12/";

    private static final QName SOAP_ADDRESS =
            new QName(SOAP_NAMESPACE, "address");
    private static final QName SOAP_BINDING =
            new QName(SOAP_NAMESPACE, "binding");

    private static final QName SOAP12_ADDRESS =
            new QName(SOAP12_NAMESPACE, "address");
    private static final QName SOAP12_BINDING =
            new QName(SOAP12_NAMESPACE, "binding");

    private static final String TRANSPORT =
        "http://schemas.xmlsoap.org/soap/http";

    private static final String VERSION = "version";

    private WSDLParser() {
    }

    /**
     * Extracts the list of services that are described in the given WSDL.
     * @param wsdlUrl the URL from which the WSDL is available
     * @return collection of ServiceInfo objects
     * @throws Exception in case of any errors
     */
    public static Collection<ServiceInfo> parseWSDL(String wsdlUrl)
            throws Exception {
        try {
            return internalParseWSDL(wsdlUrl);
        } catch (Exception e) {
            throw translateException(clarifyWsdlParsingException(e));
        }
    }

    private static Exception clarifyWsdlParsingException(Exception e) {
        if (identicalOperationsUnderSamePort(e)) {
            return new RuntimeException(
                    "WSDL violates specification: " + e.getMessage());
        }

        return e;
    }

    private static boolean identicalOperationsUnderSamePort(Exception e) {
        return (e instanceof IllegalArgumentException)
                && StringUtils.startsWith(
                    e.getMessage(), "Duplicate operation with name=");

    }

    private static Collection<ServiceInfo> internalParseWSDL(String wsdlUrl)
            throws Exception {
        WSDLFactory wsdlFactory = WSDLFactory.newInstance(
                "com.ibm.wsdl.factory.WSDLFactoryImpl");

        WSDLReader wsdlReader = wsdlFactory.newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", false);
        wsdlReader.setFeature("com.ibm.wsdl.parseXMLSchemas", false);

        Definition definition =
                wsdlReader.readWSDL(new TrustAllSslCertsWsdlLocator(wsdlUrl));

        Collection<Service> services = definition.getServices().values();

        Map<String, ServiceInfo> result = new HashMap<>();

        for (Service service : services) {
            for (Port port : (Collection<Port>) service.getPorts().values()) {
                if (!hasSoapOverHttpBinding(port)) {
                    continue;
                }

                String url = getUrl(port);
                for (BindingOperation operation
                        : (List<BindingOperation>)
                            port.getBinding().getBindingOperations()) {
                    String title = getChildValue("title",
                        operation.getOperation().getDocumentationElement());

                    String version = getVersion(operation);

                    result.put(operation.getName() + version, new ServiceInfo(
                        operation.getName(), title, url, version));
                }
            }
        }

        return result.values();
    }

    private static boolean hasSoapOverHttpBinding(Port port) throws Exception {
        for (ExtensibilityElement ext : (List<ExtensibilityElement>)
                 port.getBinding().getExtensibilityElements()) {

            if (ext.getElementType().equals(SOAP_BINDING)) {
                return TRANSPORT.equals(((SOAPBinding) ext).getTransportURI());
            }

            if (ext.getElementType().equals(SOAP12_BINDING)) {
                return TRANSPORT.equals(((SOAP12Binding) ext).getTransportURI());
            }
        }

        return false;
    }

    private static String getUrl(Port port) {
        for (ExtensibilityElement ext
                : (List<ExtensibilityElement>) port.getExtensibilityElements()) {
            if (ext.getElementType().equals(SOAP_ADDRESS)) {
                return ((SOAPAddress) ext).getLocationURI();
            }

            if (ext.getElementType().equals(SOAP12_ADDRESS)) {
                return ((SOAP12Address) ext).getLocationURI();
            }
        }

        return null;
    }

    private static String getVersion(BindingOperation operation) {
        for (ExtensibilityElement ext
                : (List<ExtensibilityElement>)
                    operation.getExtensibilityElements()) {
            if (ext.getElementType().getLocalPart().equals(VERSION)) {
                return getValue(
                        ((UnknownExtensibilityElement) ext).getElement());
            }
        }

        return null;
    }

    private static String getChildValue(String childName, Element element) {
        if (element == null) {
            return null;
        }

        NodeList nodeList = element.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            if (childName.equals(node.getLocalName())) {
                return getValue(node);
            }
        }

        return null;
    }

    private static String getValue(Node node) {
        if (node.hasChildNodes()) {
            String value = node.getFirstChild().getNodeValue();

            if (value != null) {
                value = value.trim();
            }

            return value;
        }

        return null;
    }

    /**
     * Encapsulates information about a service.
     */
    public static final class ServiceInfo {

        public final String name;
        public final String title;
        public final String url;
        public final String version;

        /**
         * Constructs a new service info object.
         * @param name the name of the service
         * @param title the title of the service
         * @param url the URL of the service
         * @param version the version of the service
         */
        public ServiceInfo(String name, String title, String url,
                String version) {
            this.name = name;
            this.title = title;
            this.url = url;
            this.version = version;
        }
    }

    private static final class TrustAllSslCertsWsdlLocator
            implements WSDLLocator {

        private static final int ERROR_RESPONSE_CODE = 500;

        private final String wsdlUrl;

        TrustAllSslCertsWsdlLocator(String wsdlUrl) {
            this.wsdlUrl = wsdlUrl;
        }

        @Override
        public InputSource getBaseInputSource() {
            try {
                URLConnection conn = new URL(wsdlUrl).openConnection();
                if (conn instanceof HttpsURLConnection) {
                    configureHttps((HttpsURLConnection) conn);
                }

                // cache the response
                byte[] response;
                try (InputStream in = conn.getInputStream()) {
                    response = IOUtils.toByteArray(in);
                }

                log.trace("Received WSDL response: {}", new String(response));
                checkForSoapFault(response);

                return new InputSource(new ByteArrayInputStream(response));
            } catch (CodedException e) {
                throw e;
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR, e);
            }
        }

        private void checkForSoapFault(byte[] response) {
            SoapParser parser = new SoapParserImpl();
            Soap soap = null;
            try {
                soap = parser.parse(MimeTypes.TEXT_XML,
                        StandardCharsets.UTF_8.name(),
                        new ByteArrayInputStream(response));
            } catch (Exception e) {
                log.info("Exception while parsing: {}", e);
                // Ignore exceptions, since the response might have
                // been a valid WSDL, which SoapParser cannot parse.
                return;
            }

            if (soap instanceof SoapFault) {
                throw ((SoapFault) soap).toCodedException();
            }
        }

        @Override
        public InputSource getImportInputSource(String parentLocation,
                String importLocation) {
            return null;
        }

        @Override
        public String getBaseURI() {
            return wsdlUrl;
        }

        @Override
        public String getLatestImportURI() {
            return null;
        }

        @Override
        public void close() {
        }

        private void configureHttps(HttpsURLConnection conn)
                throws Exception {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] certs, String authType) {
                    }
                    @Override
                    public void checkServerTrusted(
                            X509Certificate[] certs, String authType) {
                        // trust all
                    }
                }
            };

            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(null, trustAllCerts, new SecureRandom());

            conn.setSSLSocketFactory(ctx.getSocketFactory());
            conn.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }
    }
}
