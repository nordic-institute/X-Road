package ee.ria.xroad.proxy.addon;

import akka.actor.ActorSystem;

/**
 * Interface for proxy addons
 */
public interface AddOn {

    /**
     * Initialization hook called during proxy startup
     *
     * @param system    proxy actorsystem
     */
    void init(ActorSystem system);

}
