package ee.ria.xroad.common;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ee.ria.xroad.common.util.JsonUtils;

/**
 * Audit log.
 */
@Slf4j
public final class AuditLogger {

    public static final String XROAD_USER = "xroad";
    private static final String SYSTEM_USER = "system";

    private static final String EVENT_PARAM = "event";
    private static final String USER_PARAM = "user";
    private static final String REASON_PARAM = "reason";
    private static final String DATA_PARAM = "data";

    private static final String FAILURE_SUFFIX = " failed";

    private static final Marker AUDIT_MARKER = MarkerFactory.getMarker("AUDIT");

    private AuditLogger() {
    }

    /**
     * Log an event in JSON format.
     * @param jsonMessage message in JSON format
     */
    public static void log(String jsonMessage) {
        log.info(AUDIT_MARKER, jsonMessage);
    }

    /**
     * Log an event with data where the user is 'system'.
     * @param event logged event
     * @param data relevant details of the event
     */
    public static void log(String event, Map<String, Object> data) {
        log(event, SYSTEM_USER, data);
    }

    /**
     * Log an event with data for a user.
     * @param event logged event
     * @param user the user who initiated the event
     * @param data relevant details of the event
     */
    public static void log(String event, String user, Map<String, Object> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(EVENT_PARAM, event);
        message.put(USER_PARAM, user);
        message.put(DATA_PARAM, data);

        log(JsonUtils.getSerializer().toJson(message));
    }

    /**
     * Log a failure event with data for a user.
     * @param event logged event (suffix " failed" is added to the event)
     * @param user the user who initiated the event
     * @param reason the reason of the failure
     * @param data relevant details of the event
     */
    public static void log(String event, String user, String reason,
            Map<String, Object> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(EVENT_PARAM, event + FAILURE_SUFFIX);
        message.put(USER_PARAM, user);
        message.put(REASON_PARAM, reason);
        message.put(DATA_PARAM, data);

        log(JsonUtils.getSerializer().toJson(message));
    }
}
