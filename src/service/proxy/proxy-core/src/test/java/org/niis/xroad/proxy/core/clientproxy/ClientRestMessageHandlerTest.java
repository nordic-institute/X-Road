package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.MessageProcessorFactory;

import java.net.URI;

import static ee.ria.xroad.common.message.RestMessage.PROTOCOL_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_HTTP_METHOD;

@ExtendWith(MockitoExtension.class)
class ClientRestMessageHandlerTest {

    @Mock
    private MessageProcessorFactory messageProcessorFactory;
    @Mock
    private ProxyProperties proxyProperties;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private KeyConfProvider keyConfProvider;
    @Mock
    private OpMonitoringBuffer opMonitoringBuffer;

    @InjectMocks
    private ClientRestMessageHandler clientRestMessageHandler;

    @Test
    void shouldThrowExceptionWhenMethodIsOptions() {
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        HttpURI httpURI = mock(HttpURI.class);

        when(requestWrapper.getMethod()).thenReturn("OPTIONS");
        when(requestWrapper.getHttpURI()).thenReturn(httpURI);
        when(httpURI.getPath()).thenReturn("/r" + PROTOCOL_VERSION + "/foo");

        ResponseWrapper responseWrapper = ResponseWrapper.of(mock(Response.class));

        XrdRuntimeException exception = assertThrows(XrdRuntimeException.class,
                () -> clientRestMessageHandler.createRequestProcessor(requestWrapper, responseWrapper, null));

        assertEquals("client.invalid_http_method", exception.getErrorCode().toString());
    }
}
