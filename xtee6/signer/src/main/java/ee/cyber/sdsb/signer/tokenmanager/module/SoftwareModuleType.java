package ee.cyber.sdsb.signer.tokenmanager.module;

/**
 * Software module type.
 */
public class SoftwareModuleType implements ModuleType {

    public static final String TYPE = "softToken";

    @Override
    public String getType() {
        return TYPE;
    }

}
