/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.metaservice.clientproxy;

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.eclipse.jetty.http.HttpURI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.test.TestSuiteKeyConf;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.proxy.core.util.MessageProcessorBase;
import org.niis.xroad.proxy.core.util.MessageProcessorFactory;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;

/**
 * Unit test for {@link MetadataHandler}
 */
class MetadataHandlerTest {

    private RequestWrapper mockRequest;
    private HttpURI mockHttpUri;
    private ResponseWrapper mockResponse;

    private MessageProcessorFactory messageProcessorFactory;
    private final ProxyProperties proxyProperties = ConfigUtils.defaultConfiguration(ProxyProperties.class);
    private final CommonProperties commonProperties = ConfigUtils.defaultConfiguration(CommonProperties.class);

    /**
     * Init common data for tests
     */
    @BeforeEach
    void init() {
        GlobalConfProvider globalConfProvider = new TestSuiteGlobalConf();
        KeyConfProvider keyConfProvider = new TestSuiteKeyConf(globalConfProvider);
        ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
        ClientAuthenticationService clientAuthenticationService = mock(ClientAuthenticationService.class);
        EncryptionConfigProvider encryptionConfigProvider = mock(EncryptionConfigProvider.class);
        var messageRecordEncryption = mock(org.niis.xroad.common.messagelog.MessageRecordEncryption.class);

        mockRequest = mock(RequestWrapper.class);
        mockResponse = mock(ResponseWrapper.class);
        mockHttpUri = mock(HttpURI.class);

        messageProcessorFactory = new MessageProcessorFactory(null, null, proxyProperties,
                globalConfProvider, serverConfProvider, clientAuthenticationService, keyConfProvider, null,
                new OcspVerifierFactory(), commonProperties, null, null, null,
                null, encryptionConfigProvider, messageRecordEncryption);

        when(mockRequest.getHttpURI()).thenReturn(mockHttpUri);
        when(mockHttpUri.getPath()).thenReturn("/target");
    }

    @Test
    void shouldNotCreateProcessorForPostRequest() {

        when(mockRequest.getMethod()).thenReturn("POST");

        MetadataHandler handlerToTest = new MetadataHandler(messageProcessorFactory);

        MessageProcessorBase returnValue =
                handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertNull(returnValue, "Was expecting a null return value");
    }

    @Test
    void shouldNotCreateProcessorForUnprocessableRequest() {

        when(mockRequest.getMethod()).thenReturn("GET");

        MetadataHandler handlerToTest = new MetadataHandler(messageProcessorFactory);

        MessageProcessorBase returnValue =
                handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertNull(returnValue, "Was expecting a null return value");
    }

    @Test
    void shouldThrowWhenTargetNull() {

        MetadataHandler handlerToTest = new MetadataHandler(messageProcessorFactory);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockHttpUri.getPath()).thenReturn(null);

        var ce = assertThrows(XrdRuntimeException.class, () -> handlerToTest.createRequestProcessor(mockRequest, mockResponse, null));

        assertEquals(INVALID_REQUEST.code(), ce.getErrorCode());
        assertTrue(ce.getMessage().contains("Target must not be null"));
    }

    @Test
    void shouldReturnProcessorWhenAbleToProcess() {

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockHttpUri.getPath()).thenReturn("/listClients");

        MetadataHandler handlerToTest = new MetadataHandler(messageProcessorFactory);

        MessageProcessorBase result = handlerToTest.createRequestProcessor(mockRequest, mockResponse, null);

        assertThat("Message processor is of wrong type", result,
                instanceOf(MetadataClientRequestProcessor.class));
    }

}
