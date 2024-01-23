package ee.ria.xroad.proxy.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
  import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * TODO: xroad8
 * <p>
 * Upgrade to Jetty 12, use async.
 * Protect this endpoint, no authorization at this momment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetAuthorizationCallbackHandler extends AbstractHandler {
    private final ObjectMapper objectMapper = JacksonJsonLd.createObjectMapper();

    private final AuthorizedAssetRegistry authorizedAssetRegistry;

    @Override
    public void handle(String target, Request request,
                       HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse)
            throws IOException, ServletException {

        if (StringUtils.isNotBlank(target) && target.equals("/asset-authorization-callback")) {
            request.setHandled(true);

            var requestBody = objectMapper.readValue(request.getInputStream(), JsonObject.class);
            log.trace("Received asset callback request: {}", requestBody);
        }
    }
}
