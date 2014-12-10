package ee.cyber.sdsb.signer.tokenmanager;

import akka.actor.ActorSelection;
import akka.actor.UntypedActorContext;

import static ee.cyber.sdsb.signer.protocol.ComponentNames.*;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.tokenNotFound;

/**
 * Utility class for getting specific actor paths in Signer.
 */
public final class ServiceLocator {

    private ServiceLocator() {
    }

    /**
     * @param context the actor context
     * @return the request processor actor
     */
    public static ActorSelection getRequestProcessor(
            UntypedActorContext context) {
        return context.actorSelection("/user/" + REQUEST_PROCESSOR);
    }

    /**
     * @param context the actor context
     * @return the OCSP response manager actor
     */
    public static ActorSelection getOcspResponseManager(
            UntypedActorContext context) {
        return context.actorSelection("/user/" + OCSP_RESPONSE_MANAGER);
    }

    /**
     * @param context the actor context
     * @param tokenId the token id
     * @return the token actor
     */
    public static ActorSelection getToken(UntypedActorContext context,
            String tokenId) {
        String path = String.format("/user/%s/%s/%s", MODULE_MANAGER,
                getModuleId(tokenId), tokenId);
        return context.actorSelection(path);
    }

    /**
     * @param context the actor context
     * @param tokenId the token id
     * @return the token worker actor
     */
    public static ActorSelection getTokenWorker(UntypedActorContext context,
            String tokenId) {
        String path = String.format("/user/%s/%s/%s/%s", MODULE_MANAGER,
                getModuleId(tokenId), tokenId, TOKEN_WORKER);
        return context.actorSelection(path);
    }

    /**
     * @param context the actor context
     * @param tokenId the token id
     * @return the token signer actor
     */
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
