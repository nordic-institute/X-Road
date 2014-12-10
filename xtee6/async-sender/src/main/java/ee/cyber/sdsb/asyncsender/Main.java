package ee.cyber.sdsb.asyncsender;

import ee.cyber.sdsb.common.SystemPropertiesLoader;

import static ee.cyber.sdsb.common.SystemProperties.CONF_FILE_PROXY;

/**
 * Main class for AsyncSender.
 */
public final class Main {

    private Main() {
    }

    static {
        new SystemPropertiesLoader() {
            @Override
            protected void loadWithCommonAndLocal() {
                load(CONF_FILE_PROXY, "async-db");
            }
        };
    }

    /**
     * Main entry point for AsyncSender
     * @param args program arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        new AsyncSender().startUp();
    }

}
