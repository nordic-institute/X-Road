package ee.cyber.xroad.monitoragent;

/**
 * Enumeration that identifies message parameters.
 */
public enum MessageParam {
    // Parameters for query notification
    QUERY_CONSUMER(21),
    QUERY_USER_ID(22),
    QUERY_NAME(23),
    QUERY_START_TIME(24),
    QUERY_END_TIME(25),
    QUERY_ID(26),

    // Parameters for fault notification
    ERROR_CODE(30),
    ERROR_STRING(31);

    public final int code;

    private MessageParam(int code) {
        this.code = code;
    }
}
