package ee.ria.xroad.proxy.edc;

import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.server.Server;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EdcProxy {
    private final AssetAuthorizationCallbackHandler assetAuthorizationCallbackHandler;

    private final Server server = new Server();

}
