package ee.ria.xroad.common.conf.globalconf;

/**
 * Test globalconf implementation.
 */
public class TestGlobalConfImpl extends GlobalConfImpl {

    /**
     * Constructs a new test globalconf.
     * @param reloadIfChanged whether globalconf is reloaded when changes are made
     */
    public TestGlobalConfImpl(boolean reloadIfChanged) {
        super(reloadIfChanged);
    }

}
