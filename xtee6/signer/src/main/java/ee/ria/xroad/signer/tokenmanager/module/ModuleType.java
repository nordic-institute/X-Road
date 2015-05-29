package ee.ria.xroad.signer.tokenmanager.module;

/**
 * Describes a module type. Modules can be software or hardware modules.
 */
public interface ModuleType {

    /**
     * @return the type as string
     */
    String getType();

}
