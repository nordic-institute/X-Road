package ee.cyber.sdsb.proxyui.combinedwsdl;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.InputSource;

import ee.cyber.sdsb.common.identifier.ClientId;

/**
 * XXX: Specific for V5.5!
 *
 * Checks if WSDLs of client can be merged.
 */
@Slf4j
public final class WSDLCombinationChecker {
    private WSDLCombinationChecker() {
    }

    /**
     * Checks if WSDLs for particular client could be merged.
     *
     * @param client - client to be checked for WSDL-s
     * @throws Exception - thrown, when validation fails.
     */
    public static void check(ClientId client) throws Exception {
        String wsdlUrl = createWsdlUrl(client);
        log.info("Trying to get merged WSDLs from URL '{}'", wsdlUrl);

        URL urlObj = new URL(wsdlUrl);
        URLConnection connection = urlObj.openConnection();

        try (InputStream connectionIs = connection.getInputStream()) {
            if (isResponsePlainText(connection)) {
                String errorMsg = IOUtils.toString(connectionIs);
                log.info("Message: '{}'", errorMsg);

                throw new InvalidWSDLCombinationException(errorMsg);
            }

            readWsdl(connectionIs);
        }
    }

    private static boolean isResponsePlainText(URLConnection connection) {
        String contentTypeField = connection.getHeaderField("Content-Type");
        return StringUtils.contains(contentTypeField, "text/plain");
    }

    private static String createWsdlUrl(ClientId client) {
        String subsystemCode = client.getSubsystemCode();
        String subsystemParam =
                StringUtils.isNotBlank(subsystemCode)
                ? "subsystemCode=" + subsystemCode : "";

        return String.format(
                "http://127.0.0.1:%s/wsdl?"
                + "sdsbInstance=%s&"
                + "memberClass=%s&"
                + "memberCode=%s&"
                + "%s",
                getServiceMediatorPort(),
                client.getSdsbInstance(),
                client.getMemberClass(),
                client.getMemberCode(),
                subsystemParam);
    }

    private static String getServiceMediatorPort() {
        return System.getProperty(
                "ee.cyber.xroad.service-mediator.http-port",
                "8090");
    }

    private static void readWsdl(InputStream wsdlInputStream)
            throws WSDLException {
        InputSource inputSource = new InputSource(wsdlInputStream);
        WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", true);

        wsdlReader.readWSDL(null, inputSource);
    }
}
