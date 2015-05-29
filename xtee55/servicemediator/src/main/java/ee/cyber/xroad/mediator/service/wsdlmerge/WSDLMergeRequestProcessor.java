package ee.cyber.xroad.mediator.service.wsdlmerge;

import java.io.InputStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.identifier.ClientId;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;
import ee.cyber.xroad.mediator.service.wsdlmerge.merger.WSDLProvider;
import ee.cyber.xroad.mediator.service.wsdlmerge.merger.WSDLsMerger;

/**
 * Handles WSDL merge request sending back merged WSDL.
 */
public class WSDLMergeRequestProcessor implements MediatorMessageProcessor {
    private static final Logger LOG = LoggerFactory
            .getLogger(WSDLMergeRequestProcessor.class);

    private ClientId clientId;

    /**
     * Creates processor for WSDL merge request.
     *
     * @param request request to be processed.
     */
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
                wsdlUrls, new WSDLProvider(), clientId);

        try (InputStream is = merger.getMergedWsdlAsStream()) {
            IOUtils.copy(is, response.getOutputStream());
        }
    }

    private static ClientId getClientId(HttpServletRequest request) {
        String subsystemParamValue = request.getParameter("subsystemCode");
        String subsystemCode = StringUtils.isNotBlank(subsystemParamValue)
                ? subsystemParamValue : null;

        return ClientId.create(
                request.getParameter("xRoadInstance"),
                request.getParameter("memberClass"),
                request.getParameter("memberCode"),
                subsystemCode);
    }
}
