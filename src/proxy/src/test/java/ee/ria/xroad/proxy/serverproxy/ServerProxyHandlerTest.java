package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.MimeUtils;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ServerProxyHandlerTest {
    private static final String CLIENT_VERSION_6_26_3 = "6.26.3";
    private static final String VERSION_7_1_3 = "7.1.3";
    private static final String MIN_SUPPORTED_CLIENT_VERSION = "xroad.proxy.server-min-supported-client-version";

    @Test
    public void shouldNotPassClientProxyVersionCheck() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final Request baseRequest = mock(Request.class);
        when(request.getHeader(MimeUtils.HEADER_PROXY_VERSION)).thenReturn(CLIENT_VERSION_6_26_3);
        System.setProperty(MIN_SUPPORTED_CLIENT_VERSION, VERSION_7_1_3);
        mockData(request, response);
        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(mock(HttpClient.class), mock(HttpClient.class));

        try (MockedStatic<GlobalConf> mock = Mockito.mockStatic(GlobalConf.class)) {
            mock.when(GlobalConf::verifyValidity).then(invocationOnMock -> null);

            serverProxyHandler.handle("target", baseRequest, request, response);
        }

        verify(baseRequest, times(0)).getHttpChannel();
    }

    private void mockData(HttpServletRequest request, HttpServletResponse response) throws IOException {
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        when(request.getRemoteAddr()).thenReturn("remoteAddr");
        when(request.getMethod()).thenReturn("POST");
    }
}
