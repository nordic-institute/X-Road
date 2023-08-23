package ee.ria.xroad.signer.protocol;

import akka.actor.ActorSystem;
import akka.pattern.Patterns;

import akka.util.Timeout;

import ee.ria.xroad.signer.tokenmanager.TokenManager;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import scala.concurrent.Await;

import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.signer.tokenmanager.ServiceLocator.getToken;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotAvailable;

@Deprecated
@RequiredArgsConstructor
public class TemporaryAkkaMessenger {
    @Deprecated
    private static final Timeout AKKA_TIMEOUT = new Timeout(10, TimeUnit.SECONDS);

    private final ActorSystem actorSystem;

    public  <T> T tellTokenWithResponse(Object message, String tokenId) {
        return (T) tellToken(message, tokenId);
    }

    @SneakyThrows
    public Object tellToken(Object message, String tokenId) {
        if (!TokenManager.isTokenAvailable(tokenId)) {
            throw tokenNotAvailable(tokenId);
        }

        Object response = Await.result(Patterns.ask(getToken(actorSystem, tokenId), message, AKKA_TIMEOUT),
                AKKA_TIMEOUT.duration());
        if (response instanceof Exception) {
            throw (Throwable) response;
        }
        return response;
    }
}
