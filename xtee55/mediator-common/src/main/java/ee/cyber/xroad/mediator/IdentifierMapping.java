package ee.cyber.xroad.mediator;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;

/**
 * Provides access to the identifier mapping file.
 */
public final class IdentifierMapping {

    private static final String MAPPING_FILE = "identifiermapping.xml";

    private static IdentifierMappingProvider instance;

    private IdentifierMapping() {
    }

    /**
     * @return current identifier mapping instance
     */
    public static IdentifierMappingProvider getInstance() {
        if (shouldReload()) {
            instance = null;
            instance = loadConf();
        }

        return instance;
    }

    /**
     * @return path to the identifier mapping file
     */
    public static String getIdentifierMappingFile() {
        if (MediatorSystemProperties.getIdentifierMappingFile() != null) {
            return MediatorSystemProperties.getIdentifierMappingFile();
        }

        return GlobalConf.getInstanceFile(MAPPING_FILE).toString();
    }

    private static boolean shouldReload() {
        return instance == null || instance.hasChanged();
    }

    private static IdentifierMappingProvider loadConf() {
        return new IdentifierMappingImpl(getIdentifierMappingFile());
    }

}
