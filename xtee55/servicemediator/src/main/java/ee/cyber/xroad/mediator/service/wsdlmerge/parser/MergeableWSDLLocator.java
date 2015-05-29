package ee.cyber.xroad.mediator.service.wsdlmerge.parser;

import javax.wsdl.xml.WSDLLocator;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.InputSource;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

import static ee.ria.xroad.common.util.ResourceUtils.getClasspathResourceStream;

@Slf4j
class MergeableWSDLLocator implements WSDLLocator {
    private static final Map<String, String> SUPPORTED_IMPORTS;

    private InputSource inputSource;
    private InputStream resourceIs;
    private String latestImportUri;

    static {
        SUPPORTED_IMPORTS = new HashMap<>();

        SUPPORTED_IMPORTS.put(
                "http://x-road.eu/xsd/x-road.xsd", "wsdlmerge_x-roadeu.xsd");
        SUPPORTED_IMPORTS.put(
                "http://x-road.ee/xsd/x-road.xsd", "wsdlmerge_x-roadee.xsd");
        SUPPORTED_IMPORTS.put(
                "http://x-rd.net/xsd/xroad.xsd", "wsdlmerge_xroad.xsd");

        SUPPORTED_IMPORTS.put(
                "http://www.w3.org/2009/01/xml.xsd", "wsdlmerge_xml.xsd");
    }

    MergeableWSDLLocator(InputStream wsdlInputStream) {
        this.inputSource = new InputSource(wsdlInputStream);
    }

    @Override
    public InputSource getBaseInputSource() {
        return inputSource;
    }

    @Override
    public InputSource getImportInputSource(
            String parentLocation, String importLocation) {
        latestImportUri = importLocation;

        String importResource = SUPPORTED_IMPORTS.get(importLocation);

        if (StringUtils.isBlank(importResource)) {
            throw new CodedException(
                    ErrorCodes.X_IO_ERROR,
                    "Importing WSDL part from location '" + importLocation
                            + "' is not supported.");
        }

        try {
            resourceIs = getClasspathResourceStream(importResource);
        } catch (Exception e) {
            log.error("Could not get resource to import:",
                    importResource, e);
            close();
        }

        return new InputSource(resourceIs);
    }

    @Override
    public String getBaseURI() {
        return null;
    }

    @Override
    public String getLatestImportURI() {
        return latestImportUri;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(resourceIs);
    }
}
