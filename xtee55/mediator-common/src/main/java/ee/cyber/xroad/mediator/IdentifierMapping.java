package ee.cyber.xroad.mediator;

public class IdentifierMapping {

    private static IdentifierMappingProvider instance;

    public static IdentifierMappingProvider getInstance() {
        if (shouldReload()) {
            instance = null;
            instance = loadConf();
        }

        return instance;
    }

    private static boolean shouldReload() {
        return instance == null || instance.hasChanged();
    }

    private static IdentifierMappingProvider loadConf() {
        return new IdentifierMappingImpl();
    }
}
