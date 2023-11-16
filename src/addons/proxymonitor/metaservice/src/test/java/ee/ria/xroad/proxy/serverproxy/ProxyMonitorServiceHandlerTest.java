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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapHeader;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.testsuite.TestSuiteGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteKeyConf;
import ee.ria.xroad.proxy.testsuite.TestSuiteServerConf;
import ee.ria.xroad.proxymonitor.message.GetSecurityServerMetricsResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.rules.ExpectedException;

import java.io.IOException;

import static ee.ria.xroad.proxy.util.MetaserviceTestUtil.CodedExceptionMatcher.faultCodeEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProxyMonitorServiceHandlerImpl}
 */
public class ProxyMonitorServiceHandlerTest {


    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId.Conf DEFAULT_OWNER_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT");

    private static final SecurityServerId.Conf DEFAULT_OWNER_SERVER =
            SecurityServerId.Conf.create(DEFAULT_OWNER_CLIENT, "ownerServer");

    private static final ServiceId.Conf MONITOR_SERVICE_ID = ServiceId.Conf.create(DEFAULT_OWNER_CLIENT,
            ProxyMonitorServiceHandlerImpl.SERVICE_CODE);

    private static Unmarshaller unmarshaller;
    private static MessageFactory messageFactory;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public final ProvideSystemProperty hibernatePropertiesProperty
            = new ProvideSystemProperty(SystemProperties.DATABASE_PROPERTIES,
            "src/test/resources/hibernate.properties");

    @Rule
    public final ProvideSystemProperty configurationPathProperty
            = new ProvideSystemProperty(SystemProperties.CONFIGURATION_PATH,
            "src/test/resources/");

    private HttpServletRequest mockRequest;
    private ProxyMessage mockProxyMessage;

    /**
     * Init class-wide test instances
     */
    @BeforeClass
    public static void initCommon() throws JAXBException, SOAPException {
        unmarshaller = JAXBContext.newInstance(ObjectFactory.class, SoapHeader.class,
                GetSecurityServerMetricsResponse.class)
                .createUnmarshaller();
        messageFactory = MessageFactory.newInstance();
    }

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {

        GlobalConf.reload(new TestSuiteGlobalConf() {
            @Override
            public String getInstanceIdentifier() {
                return EXPECTED_XR_INSTANCE;
            }
        });
        KeyConf.reload(new TestSuiteKeyConf());
        ServerConf.reload(new TestSuiteServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return DEFAULT_OWNER_SERVER;
            }
        });

        mockRequest = mock(HttpServletRequest.class);
        mockProxyMessage = mock(ProxyMessage.class);

        when(mockProxyMessage.getSoapContentType()).thenReturn(MimeTypes.TEXT_XML_UTF8);
    }

    @Test
    public void shouldNotBeAbleToHandleWithIncorrectServiceCode() throws Exception {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl();

        final ServiceId.Conf requestedService = ServiceId.Conf.create(DEFAULT_OWNER_CLIENT, "theWrongService");

        // execution & verification
        assertThat("Should not be able to handle wrong service",
                handlerToTest.canHandle(requestedService, mockProxyMessage), is(false));
    }

    @Test
    public void shouldBeAbleToHandleWithCorrectServiceCode() throws Exception {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl();

        // execution & verification
        assertThat("Should be able to handle the right service",
                handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage), is(true));
    }

    @Test
    public void shouldNotNeedToVerifyWhenOwnerCalling() throws Exception {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl();

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(DEFAULT_OWNER_CLIENT);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        // execution & verification
        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        assertThat("Should not need a verification", handlerToTest.shouldVerifyAccess(), is(false));
    }

    @Test
    public void shouldNotNeedToVerifyWhenAllowedMonitoringClientCalling() throws Exception {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl();

        // the allowed monitoring client from test resources monitoring metricNames
        final ClientId.Conf allowedClient = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                "producer");

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(allowedClient);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        // execution & verification
        assertThat("Should not need a verification", handlerToTest.shouldVerifyAccess(), is(false));
    }

    @Test
    public void shouldThrowWhenAccessNotAllowed() throws Exception {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl();

        // the allowed monitoring client from test resources monitoring metricNames
        final ClientId.Conf nonAllowedClient = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "COM",
                "justSomeClient");

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(nonAllowedClient);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        thrown.expect(CodedException.class);
        thrown.expect(faultCodeEquals(ErrorCodes.X_ACCESS_DENIED));
        thrown.expectMessage(containsString("Request is not allowed"));

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        // execution
        handlerToTest.shouldVerifyAccess();

        // expecting an exception..
    }

}
