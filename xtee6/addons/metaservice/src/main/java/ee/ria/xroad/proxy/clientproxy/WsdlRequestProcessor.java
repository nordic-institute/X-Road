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
        String instance = request.getParameter("instance");
        if (isBlank(instance)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Must specify instance identifier");
        }

        String serviceCode = request.getParameter("serviceCode");
        if (isBlank(serviceCode)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Must specify service code");
        }

        String memberClass = request.getParameter("memberClass");
        String memberCode = request.getParameter("memberCode");
        String subsystemCode = request.getParameter("subsystemCode");
        String version = request.getParameter("version");

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
