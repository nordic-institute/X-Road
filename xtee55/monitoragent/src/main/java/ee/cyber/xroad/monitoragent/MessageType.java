package ee.cyber.xroad.monitoragent;

public enum MessageType {
    QUERY_NOTIFICATION(2),
    ALERT(3);

    public final int code;

    private MessageType(int code) {
        this.code = code;
    }
}
