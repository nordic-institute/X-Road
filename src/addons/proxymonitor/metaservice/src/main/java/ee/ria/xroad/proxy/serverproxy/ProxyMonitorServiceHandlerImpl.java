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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.conf.monitoringconf.MonitoringConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SimpleSoapEncoder;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.XmlUtils;
import ee.ria.xroad.proxy.ProxyMain;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxymonitor.ProxyMonitor;
import ee.ria.xroad.proxymonitor.message.GetSecurityServerMetricsResponse;
import ee.ria.xroad.proxymonitor.message.MetricSetType;
import ee.ria.xroad.proxymonitor.message.ObjectFactory;
import ee.ria.xroad.proxymonitor.message.StringMetricType;
import ee.ria.xroad.proxymonitor.util.MonitorClient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service handler for proxy monitoring
 */
@Slf4j
public class ProxyMonitorServiceHandlerImpl implements ServiceHandler {

    public static final String SERVICE_CODE = "getSecurityServerMetrics";
    public static final String MONITOR_REQ_PARAM_NODE_NAME = "outputField";
    public static final String NS_MONITORING = "http://x-road.eu/xsd/monitoring";

    private ProxyMessage requestMessage;
    private static final JAXBContext JAXB_CTX;

    private final ByteArrayOutputStream responseOut =
            new ByteArrayOutputStream();

    private SoapMessageEncoder responseEncoder;

    @Override
    public boolean shouldVerifyAccess() {
        //override default access check
        verifyAccess();
        return false;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    public boolean canHandle(ServiceId requestServiceId, ProxyMessage requestProxyMessage) {
        final ServiceId.Conf serviceId = ServiceId.Conf.create(ServerConf.getIdentifier().getOwner(), SERVICE_CODE);

        if (serviceId.equals(requestServiceId)) {
            requestMessage = requestProxyMessage;
            return true;
        }

        return false;
    }

    @Override
    public void startHandling(HttpServletRequest servletRequest,
                              ProxyMessage proxyRequestMessage, HttpClient opMonitorClient,
                              OpMonitoringData opMonitoringData) throws Exception {

        // It's required that in case of proxy monitor service (where SOAP
        // message is not forwarded) the requestOutTs must be equal with the
        // requestInTs and the responseInTs must be equal with the
        // responseOutTs.
        opMonitoringData.setRequestOutTs(opMonitoringData.getRequestInTs());
        opMonitoringData.setAssignResponseOutTsToResponseInTs(true);

        //mock implementation
        responseEncoder = new SimpleSoapEncoder(responseOut);

        final MonitorClient client = ProxyMonitor.getClient();

        final GetSecurityServerMetricsResponse metricsResponse = new GetSecurityServerMetricsResponse();
        final MetricSetType root = new MetricSetType();
        root.setName(ServerConf.getIdentifier().toString());
        metricsResponse.setMetricSet(root);

        final StringMetricType version = new StringMetricType();
        version.setName("proxyVersion");
        version.setValue(ProxyMain.readProxyVersion());
        root.getMetrics().add(version);

        if (client != null) {
            root.getMetrics().add(client.getMetrics(getMetricNames(proxyRequestMessage), isOwner()));
        }

        SoapMessageImpl result = createResponse(requestMessage.getSoap(), metricsResponse);
        responseEncoder.soap(result, Collections.emptyMap());
    }

    /**
     * Read requested monitoring parameter names from SOAP body. Returns empty list if no explicit metric names defined.
     *
     * @param proxyRequestMessage
     * @return
     */
    private List<String> getMetricNames(ProxyMessage proxyRequestMessage) throws Exception {
        List<String> metricNames = new ArrayList<>();

        Document doc = parse(proxyRequestMessage);
        NodeList nl = doc.getElementsByTagNameNS(NS_MONITORING, MONITOR_REQ_PARAM_NODE_NAME);

        for (int i = 0; i < nl.getLength(); i++) {
            metricNames.add(nl.item(i).getFirstChild().getNodeValue());
        }
        return metricNames;
    }

    /**
     * Create XML DOM representation from input stream.
     *
     * @param proxyRequestMessage
     * @return
     * @throws Exception
     */
    private Document parse(ProxyMessage proxyRequestMessage) throws Exception {
        byte[] bytes = proxyRequestMessage.getSoap().getBytes();
        return XmlUtils.parseDocument(new ByteArrayInputStream(bytes), true);
    }

    @Override
    public void finishHandling() throws Exception {
        // nothing to do
    }

    @Override
    public String getResponseContentType() {
        return responseEncoder.getContentType();
    }

    @Override
    public InputStream getResponseContent() throws Exception {
        return new ByteArrayInputStream(responseOut.toByteArray());
    }

    private boolean isOwner() {
        final ClientId owner = ServerConf.getIdentifier().getOwner();
        final ClientId client = requestMessage.getSoap().getClient();
        return owner.equals(client);
    }

    private void verifyAccess() {
        final ClientId owner = ServerConf.getIdentifier().getOwner();
        final ClientId client = requestMessage.getSoap().getClient();

        if (owner.equals(client)) {
            return;
        }

        // Grant access for configured monitoring client (if any)
        ClientId monitoringClient = MonitoringConf.getInstance()
                .getMonitoringClient();

        if (monitoringClient != null && monitoringClient.equals(client)) {
            return;
        }

        throw new CodedException(ErrorCodes.X_ACCESS_DENIED,
                "Request is not allowed: %s",
                requestMessage.getSoap().getService());
    }

    private static SoapMessageImpl createResponse(SoapMessageImpl requestMessage, Object response) throws Exception {
        SoapMessageImpl responseMessage = SoapUtils.toResponse(requestMessage,
                soap -> {
                    soap.getSOAPBody().removeContents();
                    marshal(response, soap.getSOAPBody());
                });
        return responseMessage;
    }

    private static void marshal(Object object, Node out) throws Exception {
        Marshaller marshaller = JAXB_CTX.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(object, out);
    }

    static {
        try {
            JAXB_CTX = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}
