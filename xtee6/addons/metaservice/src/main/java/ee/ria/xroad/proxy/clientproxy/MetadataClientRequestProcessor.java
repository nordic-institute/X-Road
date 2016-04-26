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

import java.io.OutputStream;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.MimeTypes;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.metadata.CentralServiceListType;
import ee.ria.xroad.common.metadata.ClientListType;
import ee.ria.xroad.common.metadata.ClientType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import static ee.ria.xroad.common.metadata.MetadataRequests.*;

@Slf4j
class MetadataClientRequestProcessor extends MessageProcessorBase {

    static final String PARAM_INSTANCE_IDENTIFIER = "xRoadInstance";

    static final JAXBContext JAXB_CTX = initJaxbCtx();
    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    private final String target;

    MetadataClientRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response) {
        super(request, response, null);

        this.target = target;
    }

    public boolean canProcess() {
        switch (target) {
            case LIST_CLIENTS: // $FALL-THROUGH$
            case LIST_CENTRAL_SERVICES: // $FALL-THROUGH$
            case WSDL:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void process() throws Exception {
        switch (target) {
            case LIST_CLIENTS:
                handleListClients();
                return;
            case LIST_CENTRAL_SERVICES:
                handleListCentralServices();
                return;
            case WSDL:
                handleWsdl();
                return;
            default: // to nothing
                break;
        }
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        return null; // nothing to return
    }

    private void handleListClients() throws Exception {
        log.trace("handleListClients()");

        String instanceIdentifier = getInstanceIdentifierFromRequest();

        ClientListType list = OBJECT_FACTORY.createClientListType();
        list.getMember().addAll(
                GlobalConf.getMembers(instanceIdentifier).stream().map(m -> {
                    ClientType client = OBJECT_FACTORY.createClientType();
                    client.setId(m.getId());
                    client.setName(m.getName());
                    return client;
                }).collect(Collectors.toList()));

        writeResponseXml(OBJECT_FACTORY.createClientList(list));
    }

    private void handleListCentralServices() throws Exception {
        log.trace("handleListCentralServices()");

        String instanceIdentifier = getInstanceIdentifierFromRequest();

        CentralServiceListType list =
                OBJECT_FACTORY.createCentralServiceListType();
        list.getCentralService().addAll(
                GlobalConf.getCentralServices(instanceIdentifier));

        writeResponseXml(OBJECT_FACTORY.createCentralServiceList(list));
    }

    private void handleWsdl() throws Exception {
        log.trace("handleWsdl()");

        new WsdlRequestProcessor(servletRequest, servletResponse).process();
    }

    private void writeResponseXml(Object object) throws Exception {
        servletResponse.setContentType(MimeTypes.TEXT_XML_UTF_8);
        marshal(object, servletResponse.getOutputStream());
    }

    private String getInstanceIdentifierFromRequest() {
        String instanceIdentifier =
                servletRequest.getParameter(PARAM_INSTANCE_IDENTIFIER);
        if (StringUtils.isBlank(instanceIdentifier)) {
            instanceIdentifier = GlobalConf.getInstanceIdentifier();
        }

        return instanceIdentifier;
    }

    private static void marshal(Object object, OutputStream out)
            throws Exception {
        Marshaller marshaller = JAXB_CTX.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(object, out);
    }

    private static JAXBContext initJaxbCtx() {
        try {
            return JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}
