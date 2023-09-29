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
package org.niis.xroad.securityserver.restapi.wsdl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_DOWNLOAD_FAILED;

/**
 * Utils for WSDL parsing
 */
@Slf4j
public final class WsdlParser {
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

    private static final String TRANSPORT = "http://schemas.xmlsoap.org/soap/http";

    private static final String VERSION = "version";
    private static final int BUF_SIZE = 8192;
    private static final long MAX_DESCRIPTION_SIZE = 10 * 1024 * 1024;

    private WsdlParser() {
    }

    /**
     * Extracts the list of services that are described in the given WSDL.
     * @param wsdlUrl the URL from which the WSDL is available
     * @return collection of ServiceInfo objects
     * @throws WsdlNotFoundException if a WSDL was not found at given URL
     * @throws WsdlParseException if anything else than WsdlNotFoundException went wrong in parsing (e.g. document
     * size exceeds the limit defined by {@link #MAX_DESCRIPTION_SIZE})
     */
    public static Collection<ServiceInfo> parseWSDL(String wsdlUrl) throws WsdlNotFoundException, WsdlParseException {
        try {
            return internalParseWSDL(wsdlUrl);
        } catch (PrivateWsdlNotFoundException e) {
            log.error("Reading WSDL from {} failed", wsdlUrl, e);
            throw new WsdlNotFoundException(e);
        } catch (Exception e) {
            log.error("Reading WSDL from {} failed", wsdlUrl, e);
            throw new WsdlParseException(clarifyWsdlParsingException(e));
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

    private static Collection<ServiceInfo> internalParseWSDL(String wsdlUrl) throws Exception {
        log.info("running WSDL parser");
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
                for (BindingOperation operation : (List<BindingOperation>) port.getBinding().getBindingOperations()) {
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
        for (ExtensibilityElement ext : (List<ExtensibilityElement>) port.getBinding().getExtensibilityElements()) {

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
        for (ExtensibilityElement ext : (List<ExtensibilityElement>) port.getExtensibilityElements()) {
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
        for (ExtensibilityElement ext : (List<ExtensibilityElement>) operation.getExtensibilityElements()) {
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

    /**
     * keep this one private and dont let it leak
     */
    private static final class PrivateWsdlNotFoundException extends RuntimeException {
        PrivateWsdlNotFoundException(Throwable t) {
            super(t);
        }
    }

    private static final class TrustAllSslCertsWsdlLocator implements WSDLLocator {

        private final String wsdlUrl;

        TrustAllSslCertsWsdlLocator(String wsdlUrl) {
            this.wsdlUrl = wsdlUrl;
        }

        @Override
        public InputSource getBaseInputSource() {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                URLConnection conn = new URL(wsdlUrl).openConnection();
                if (conn instanceof HttpsURLConnection) {
                    configureHttps((HttpsURLConnection) conn);
                }

                try (InputStream in = conn.getInputStream()) {
                    long count = 0;
                    int n;
                    byte[] buf = new byte[BUF_SIZE];
                    while ((n = in.read(buf)) != -1) {
                        count += n;
                        if (count > MAX_DESCRIPTION_SIZE) {
                            throw new UncheckedIOException("Error reading WSDL: Size exceeds "
                                    + MAX_DESCRIPTION_SIZE + " bytes.", new IOException());
                        }
                        byteArrayOutputStream.write(buf, 0, n);
                    }
                }
                byte[] response = byteArrayOutputStream.toByteArray();
                if (log.isTraceEnabled()) {
                    log.trace("Received WSDL response: {}", new String(response));
                }

                return new InputSource(new ByteArrayInputStream(response));
            } catch (UncheckedIOException e) {
                throw e;
            } catch (Exception t) {
                throw new PrivateWsdlNotFoundException(t);
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
            // no-op
        }

        private void configureHttps(HttpsURLConnection conn) throws Exception {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        @Override
                        @SuppressWarnings("java:S4830") // Won't fix: Works as designed
                        // ("Server certificates should be verified")
                        public void checkClientTrusted(
                                X509Certificate[] certs, String authType) {
                            // never called as used by client
                        }

                        @Override
                        @SuppressWarnings("java:S4830") // Won't fix: Works as designed
                        // ("Server certificates should be verified")
                        public void checkServerTrusted(
                                X509Certificate[] certs, String authType) {
                            // trust all
                        }
                    }
            };

            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(new KeyManager[] {new ClientSslKeyManager()}, trustAllCerts, new SecureRandom());

            conn.setSSLSocketFactory(ctx.getSocketFactory());
            conn.setHostnameVerifier(HostnameVerifiers.ACCEPT_ALL);
        }
    }

    /**
     * Thrown if WSDL parsing fails
     */
    public static class WsdlParseException extends InvalidWsdlException {
        public WsdlParseException(Throwable t) {
            super(toListOrNull(t.getMessage()));
        }

        public WsdlParseException(String message) {
            super(message);
        }

        private static List<String> toListOrNull(String message) {
            if (message == null) {
                return null;
            } else {
                return Collections.singletonList(message);
            }
        }
    }

    /**
     * Thrown if WSDL file is not found
     */
    public static class WsdlNotFoundException extends ServiceException {
        public WsdlNotFoundException(Throwable cause) {
            super(cause, new ErrorDeviation(ERROR_WSDL_DOWNLOAD_FAILED));
        }
    }
}
