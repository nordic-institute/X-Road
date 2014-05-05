package ee.cyber.xroad.mediator.service.wsdlmerge;

import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;
import ee.cyber.xroad.mediator.service.wsdlmerge.merger.WSDLProvider;
import ee.cyber.xroad.mediator.service.wsdlmerge.merger.WSDLStreamsMerger;
import ee.cyber.xroad.mediator.service.wsdlmerge.merger.WSDLsMerger;

/**
 * Handles WSDL merge request sending back merged WSDL.
 */
public class WSDLMergeRequestProcessor implements MediatorMessageProcessor {
    private static final Logger LOG = LoggerFactory
            .getLogger(WSDLMergeRequestProcessor.class);

    private ClientId clientId;

    public WSDLMergeRequestProcessor(HttpServletRequest request) {
        this.clientId = getClientId(request);

        LOG.trace("WSDL-s will be merged for client '{}'", clientId);
    }

    @Override
    public void process(MediatorRequest request, MediatorResponse response)
            throws Exception {
        List<String> wsdlUrls =
                MediatorServerConf.getAdapterWSDLUrls(clientId);

        LOG.info("WSDL urls for client '{}': '{}'", clientId, wsdlUrls);

        WSDLsMerger merger = new WSDLsMerger(
                wsdlUrls, new WSDLProvider(), clientId, new WSDLStreamsMerger());

        try (InputStream is = merger.getMergedWsdlAsStream()) {
            IOUtils.copy(is, response.getOutputStream());
        }
    }

    private static ClientId getClientId(HttpServletRequest request) {
        return ClientId.create(
                request.getParameter("sdsbInstance"),
                request.getParameter("memberClass"),
                request.getParameter("memberCode"),
                request.getParameter("subsystemCode"));
    }
}
