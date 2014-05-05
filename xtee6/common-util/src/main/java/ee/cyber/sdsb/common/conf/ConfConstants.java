package ee.cyber.sdsb.common.conf;

/**
 * This class contains constant values used in configuration implementations.
 */
public class ConfConstants {

    /** The type used to identify software keys in key configuration. */
    public static final String SOFTKEY_TYPE = "softToken";

    /** Number of minutes the conf is allowed to be older than current time. */
    public static final int CONF_FRESHNESS_TIME_MINUTES = 10;

    private ConfConstants() {
    }
}
