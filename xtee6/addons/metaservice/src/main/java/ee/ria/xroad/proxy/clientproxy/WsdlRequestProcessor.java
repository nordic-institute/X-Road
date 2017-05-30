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
package ee.ria.xroad.proxy.clientproxy;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.Marshaller;
import javax.xml.soap.SOAPBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.MimeTypes;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.JaxbUtils;
import ee.ria.xroad.common.message.SoapBuilder;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.common.WsdlRequestData;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static org.apache.commons.lang.StringUtils.isBlank;

@Slf4j
@RequiredArgsConstructor
class WsdlRequestProcessor {
    static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";
    static final String PARAM_MEMBER_CLASS = "memberClass";
    static final String PARAM_MEMBER_CODE = "memberCode";
    static final String PARAM_SUBSYSTEM_CODE = "subsystemCode";
    static final String PARAM_SERVICE_CODE = "serviceCode";
    static final String PARAM_VERSION = "version";

    private static final String GET_WSDL = "getWsdl";
    private static final String USER_ID = "wsdl-request";

    private final HttpServletRequest request;
    private final HttpServletResponse response;

    void process() throws Exception {
        ClientId client = ServerConf.getIdentifier().getOwner();
        ServiceId service = getServiceId();
        log.trace("process({}, {})", client, service);

        SoapMessageImpl message = createMessage(client, service);

        if (log.isTraceEnabled()) {
            log.trace("message:\n{}", message.getXml());
        }

        HttpURLConnection connection = createConnection(message);
        try (InputStream in = connection.getInputStream()) {
            SoapMessageDecoder decoder =
                    new SoapMessageDecoder(connection.getContentType(),
                            new SoapDecoderCallback());
            decoder.parse(in);
        }
    }

    private SoapMessageImpl createMessage(ClientId client,
            final ServiceId service) throws Exception {
        ServiceId implementingService = GlobalConf.getServiceId(service);
        log.debug("Implementing service: {}", implementingService);

        SoapHeader header = new SoapHeader();
        header.setClient(client);
        header.setService(createGetWsdlService(implementingService));
        header.setQueryId(UUID.randomUUID().toString());
        header.setUserId(USER_ID);

        SoapBuilder sb = new SoapBuilder();
        sb.setHeader(header);
        sb.setCreateBodyCallback(new SoapBuilder.SoapBodyCallback() {
            @Override
            public void create(SOAPBody soapBody) throws Exception {
                WsdlRequestData req = new WsdlRequestData();
                req.setServiceCode(implementingService.getServiceCode());
                req.setServiceVersion(implementingService.getServiceVersion());

                Marshaller marshaller =
                        JaxbUtils.createMarshaller(req.getClass());
                marshaller.marshal(req, soapBody);
            }
        });

        return sb.build();
    }

    private ServiceId createGetWsdlService(ServiceId service) {
        return ServiceId.create(service.getClientId(), GET_WSDL,
                service.getServiceVersion());
    }

    ServiceId getServiceId() {
        String instance = request.getParameter(PARAM_INSTANCE_IDENTIFIER);
        if (isBlank(instance)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Must specify instance identifier");
        }

        String serviceCode = request.getParameter(PARAM_SERVICE_CODE);
        if (isBlank(serviceCode)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Must specify service code");
        }

        String memberClass = request.getParameter(PARAM_MEMBER_CLASS);
        String memberCode = request.getParameter(PARAM_MEMBER_CODE);
        String subsystemCode = request.getParameter(PARAM_SUBSYSTEM_CODE);
        String version = request.getParameter(PARAM_VERSION);

        try {
            // Central Service?
            if (isBlank(memberClass) && isBlank(memberCode)) {
                return CentralServiceId.create(instance, serviceCode);
            } else { // Service
                return ServiceId.create(instance, memberClass, memberCode,
                        subsystemCode, serviceCode, version);
            }
        } catch (Exception e) {
            throw new CodedException(X_INVALID_REQUEST, e.getMessage());
        }
    }

    HttpURLConnection createConnection(SoapMessageImpl message)
            throws Exception {
        URL url = new URL("http://127.0.0.1:"
                + SystemProperties.getClientProxyHttpPort());

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoInput(true);
        con.setDoOutput(true);
        con.setRequestMethod("POST");

        con.setRequestProperty(HttpHeaders.CONTENT_TYPE,
                MimeUtils.contentTypeWithCharset(MimeTypes.TEXT_XML,
                        StandardCharsets.UTF_8.name()));

        IOUtils.write(message.getBytes(), con.getOutputStream());

        if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Received HTTP error: "
                    + con.getResponseCode() + " - " + con.getResponseMessage());
        }

        return con;
    }

    private class SoapDecoderCallback implements SoapMessageDecoder.Callback {

        @Override
        public void soap(SoapMessage message) throws Exception {
            // discard
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            log.trace("attachment({})", contentType);

            response.setContentType(contentType);
            IOUtils.copy(content, response.getOutputStream());
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            log.error("Received fault {}", fault.getXml());
            throw fault.toCodedException();
        }

        @Override
        public void onCompleted() {
            log.trace("onCompleted()");
        }

        @Override
        public void onError(Exception t) throws Exception {
            log.error("Error while reading response", t);
            throw t;
        }

    }
}
