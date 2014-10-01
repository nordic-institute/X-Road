package ee.cyber.sdsb.signer.tokenmanager;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import akka.actor.ActorSelection;
import akka.actor.UntypedActorContext;

import static ee.cyber.sdsb.signer.protocol.ComponentNames.*;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.tokenNotFound;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceLocator {

    public static ActorSelection getOcspResponseManager(
            UntypedActorContext context) {
        return context.actorSelection("/user/" + OCSP_RESPONSE_MANAGER);
    }

    public static ActorSelection getToken(UntypedActorContext context,
            String tokenId) {
        String path = String.format("/user/%s/%s/%s", MODULE_MANAGER,
                getModuleId(tokenId), tokenId);
        return context.actorSelection(path);
    }

    public static ActorSelection getTokenWorker(UntypedActorContext context,
            String tokenId) {
        String path = String.format("/user/%s/%s/%s/%s", MODULE_MANAGER,
                getModuleId(tokenId), tokenId, TOKEN_WORKER);
        return context.actorSelection(path);
    }

    public static ActorSelection getTokenSigner(UntypedActorContext context,
            String tokenId) {
        String path = String.format("/user/%s/%s/%s/%s", MODULE_MANAGER,
                getModuleId(tokenId), tokenId, TOKEN_SIGNER);
        return context.actorSelection(path);
    }

    private static String getModuleId(String tokenId) {
        String moduleId = TokenManager.getModuleId(tokenId);
        if (moduleId == null) {
            throw tokenNotFound(tokenId);
        }

        return moduleId;
    }
}
