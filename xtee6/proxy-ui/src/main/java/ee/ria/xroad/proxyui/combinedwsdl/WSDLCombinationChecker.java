package ee.ria.xroad.proxyui.combinedwsdl;

import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.InputSource;

import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.common.SystemProperties.getServiceMediatorAddress;

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
        } catch (InvalidWSDLCombinationException | WSDLException e) {
            handleWsdlProcessingError(e);
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
                "%s/wsdl?"
                + "xRoadInstance=%s&"
                + "memberClass=%s&"
                + "memberCode=%s&"
                + "%s",
                getServiceMediatorAddress(),
                client.getXRoadInstance(),
                client.getMemberClass(),
                client.getMemberCode(),
                subsystemParam);
    }

    private static void readWsdl(InputStream wsdlInputStream)
            throws WSDLException {
        InputSource inputSource = new InputSource(wsdlInputStream);
        WSDLReader wsdlReader = WSDLFactory.newInstance().newWSDLReader();
        wsdlReader.setFeature("javax.wsdl.importDocuments", false);
        wsdlReader.setFeature("com.ibm.wsdl.parseXMLSchemas", false);

        wsdlReader.readWSDL(null, inputSource);
    }

    private static void handleWsdlProcessingError(Exception ex)
            throws Exception {
        log.error("Check of WSDL file failed, root cause message: '{}'.",
                ExceptionUtils.getRootCauseMessage(ex), ex);

        if (!(ex instanceof WSDLException)) {
            throw ex;
        }

        WSDLException wsdlException = (WSDLException) ex;
        Throwable targetException = wsdlException.getTargetException();

        if (targetException instanceof UnknownHostException) {
            throw new UnknownHostException(
                    "WSDL parsing failed as host '"
                    + targetException.getMessage() + "' cannot be resolved.");
        }

        throw wsdlException;
    }
}
