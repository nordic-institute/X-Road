package ee.ria.xroad.signer.tokenmanager.token;

import java.util.Optional;

public interface TokenWorkerProvider {
    Optional<TokenWorker> getTokenWorker(String tokenId);
}
