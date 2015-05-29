package ee.cyber.xroad.mediator.service.wsdlmerge.merger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.xroad.mediator.service.wsdlmerge.parser.WSDLParser;
import ee.cyber.xroad.mediator.service.wsdlmerge.structure.WSDL;

/**
 * Takes streams of input WSDL-s and converts them to the stream including
 * merged WSDL bytes.
 */
public class WSDLStreamsMerger {

    private static final Logger LOG = LoggerFactory
            .getLogger(WSDLStreamsMerger.class);

    @Getter
    private InputStream mergedWsdlAsStream;
    private List<InputStream> inputWsdlStreams;
    private String databaseV5Name;

    /**
     * Creates WSDL streams merger
     * @param inputWsdlStreams input streams of WSDLs to be merged.
     * @param databaseV5Name short name for V5 database.
     * @throws Exception thrown when merging streams fails.
     */
    public WSDLStreamsMerger(
            List<InputStream> inputWsdlStreams, String databaseV5Name)
            throws Exception {
        this.inputWsdlStreams = inputWsdlStreams;
        this.databaseV5Name = databaseV5Name;

        merge();
    }

    private void merge() throws Exception {
        List<WSDL> inputWsdls = new ArrayList<>(inputWsdlStreams.size());

        for (int i = 0; i < inputWsdlStreams.size(); i++) {
            try (InputStream each = inputWsdlStreams.get(i)) {
                WSDLParser parser = new WSDLParser(each, i);
                inputWsdls.add(parser.getWSDL());
            }
        }

        WSDLMerger merger = new WSDLMerger(inputWsdls, databaseV5Name);
        WSDL mergedWsdl = merger.getMergedWsdl();

        LOG.trace("Merged WSDL:\n{}", mergedWsdl.getXml());

        mergedWsdlAsStream = IOUtils.toInputStream(
                mergedWsdl.getXml(), StandardCharsets.UTF_8);
    }
}
