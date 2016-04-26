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
package ee.ria.xroad.asyncdb;

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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;

/**
 * Utility functions used by multiple tests
 */
public final class AsyncDBTestUtil {
    private AsyncDBTestUtil() {
    }

    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncDBTestUtil.class);

    public static final int LOG_FILE_FIELDS = 10;

    public static final String DB_FILEPATH = "build/asyncdb";
    public static final String LOG_FILEPATH = "build/asynclog";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm.ssZ";

    private static String providerName;
    private static SoapMessageImpl firstSoapMessage;
    private static SoapMessageImpl secondSoapMessage;

    /**
     * Sets up test specific async-db JVM arguments.
     */
    public static void setTestenvProps() {
        System.setProperty(SystemProperties.ASYNC_DB_PATH,
                AsyncDBTestUtil.DB_FILEPATH);
        System.setProperty(SystemProperties.LOG_PATH, LOG_FILEPATH);
    }

    /**
     * Returns path to service provider directory.
     *
     * @return - path to service provider directory.
     * @throws Exception - thrown when cannot get path to provider directory.
     */
    public static String getProviderDirPath() throws Exception {
        return SystemProperties.getAsyncDBPath() + File.separator
                + AsyncDBUtil.getQueueName(getProvider());
    }

    /**
     * Returns path to asynchronous requests log.
     *
     * @return - path to asynchonous requests log.
     */
    public static String getAsyncLogFilePath() {
        return SystemProperties.getLogPath() + File.separator
                + AsyncLogWriter.ASYNC_LOG_FILENAME;
    }

    /**
     * Gets member code of provider from its id.
     *
     * @return - member code of provider.
     */
    public static String getProviderName() {
        if (providerName == null) {
            providerName = serviceId(
                    getFirstSoapMessage().getService()).getMemberCode();
            LOG.info("Using provider name '{}'", providerName);
        }
        return providerName;
    }

    /**
     * Returns id of provider.
     *
     * @return - id of provider.
     */
    public static ClientId getProvider() {
        return serviceId(getFirstSoapMessage().getService()).getClientId();
    }

    /**
     * Returns first SOAP request for testing.
     *
     * @return - first SOAP request for testing.
     */
    public static SoapMessageImpl getFirstSoapRequest() {
        return getFirstSoapMessage();
    }

    /**
     * Returns second SOAP request for testing.
     *
     * @return - second SOAP request for testing.
     *
     * @throws IOException - when getting second SOAP request fails.
     */
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

    /**
     * Returns date specific for async-db tests.
     *
     * @param dateAsString - raw date.
     * @return - parsed Date object.
     * @throws ParseException - when parsing date fails.
     */
    public static Date getDate(String dateAsString) throws ParseException {
        return new SimpleDateFormat(DATE_FORMAT).parse(dateAsString);
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

    private static ServiceId serviceId(ServiceId abstractServiceId) {
        if (abstractServiceId == null) {
            throw new RuntimeException("Unknown service id");
        } else if (abstractServiceId instanceof CentralServiceId) {
            CentralServiceId centralServiceId =
                    (CentralServiceId) abstractServiceId;
            return ServiceId.create(centralServiceId.getXRoadInstance(),
                    "CLASS", "CODE", null, centralServiceId.getServiceCode());
        } else {
            return abstractServiceId;
        }
    }
}
