package ee.cyber.sdsb.asyncdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.AbstractServiceId;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;

/**
 * Utility functions used by multiple tests
 */
public class AsyncDBTestUtil {

    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncDBTestUtil.class);

    public static final int LOG_FILE_FIELDS = 10;

    public static final String DB_FILEPATH = "build/asyncdb";
    public static final String LOG_FILEPATH = "build/asynclog";
    public static final String SERVER_CONF_FILE = "src/test/resources/serverconf.xml";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm.ss";

    private static String providerName;
    private static SoapMessageImpl firstSoapMessage;
    private static SoapMessageImpl secondSoapMessage;

    public static void setTestenvProps() {
        System.setProperty(SystemProperties.ASYNC_DB_PATH,
                AsyncDBTestUtil.DB_FILEPATH);
        System.setProperty(SystemProperties.LOG_PATH, LOG_FILEPATH);
        System.setProperty(SystemProperties.SERVER_CONFIGURATION_FILE,
                SERVER_CONF_FILE);
    }

    public static String getProviderDirPath() throws Exception {
        return SystemProperties.getAsyncDBPath() + File.separator
                + AsyncDBUtil.getQueueName(getProvider());
    }

    public static String getAsyncLogFilePath() {
        return SystemProperties.getLogPath() + File.separator
                + AsyncLogWriter.ASYNC_LOG_FILENAME;
    }

    /**
     * Gets provider name from
     *
     * @return
     */
    public static String getProviderName() {
        if (providerName == null) {
            providerName = serviceId(
                    getFirstSoapMessage().getService()).getMemberCode();
            LOG.info("Using provider name '{}'", providerName);
        }
        return providerName;
    }

    public static ClientId getProvider() {
        return serviceId(getFirstSoapMessage().getService()).getClientId();
    }

    public static SoapMessageImpl getFirstSoapRequest() {
        return getFirstSoapMessage();
    }

    public static SoapMessageImpl getSecondSoapRequest() throws IOException {
        if (secondSoapMessage == null) {
            try (InputStream is = new FileInputStream(
                    "src/test/resources/soaprequest_SECOND.xml")) {
                secondSoapMessage = soap(is);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Could not get SOAP message: ", e);
            }
        }
        return secondSoapMessage;
    }

    public static Date getDate(String dateAsString) throws ParseException {
        return new SimpleDateFormat(
                DATE_FORMAT).parse(dateAsString);
    }

    private static SoapMessageImpl getFirstSoapMessage() {
        if (firstSoapMessage == null) {
            try (InputStream is = new FileInputStream(
                    "src/test/resources/soaprequest.xml")) {
                firstSoapMessage = soap(is);
            } catch (IOException e) {
                throw new RuntimeException("Could not get SOAP message: ", e);
            }
        }
        return firstSoapMessage;
    }

    private static SoapMessageImpl soap(InputStream is) {
        Soap soap = new SoapParserImpl().parse(is);
        if (soap instanceof SoapMessageImpl) {
            return (SoapMessageImpl) soap;
        }

        throw new RuntimeException("Read unexpected Soap message");
    }

    private static ServiceId serviceId(AbstractServiceId abstractServiceId) {
        if (abstractServiceId instanceof ServiceId) {
            return (ServiceId) abstractServiceId;
        } else if (abstractServiceId instanceof CentralServiceId) {
            CentralServiceId centralServiceId =
                    (CentralServiceId) abstractServiceId;
            return ServiceId.create(centralServiceId.getSdsbInstance(),
                    "CLASS", "CODE", null, centralServiceId.getServiceCode());
        } else {
            throw new RuntimeException("Unknown service id");
        }
    }
}
