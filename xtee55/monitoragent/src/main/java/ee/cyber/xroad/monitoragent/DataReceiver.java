package ee.cyber.xroad.monitoragent;

import java.util.Date;

import akka.actor.UntypedActor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.monitoring.FaultInfo;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MessageInfo.Origin;
import ee.ria.xroad.common.monitoring.ServerProxyFailed;
import ee.ria.xroad.common.monitoring.SuccessfulMessage;

import static ee.cyber.xroad.monitoragent.DataSender.send;
import static ee.cyber.xroad.monitoragent.MessageParam.*;
import static ee.cyber.xroad.monitoragent.MessageType.ALERT;
import static ee.cyber.xroad.monitoragent.MessageType.QUERY_NOTIFICATION;

import static org.apache.commons.lang3.tuple.ImmutablePair.of;

/**
 * Actor that receives message processing outcome information and
 * forwards notifications to appropriate recipients.
 */
@Slf4j
public class DataReceiver extends UntypedActor {

    private static final int QUERY_PARAMETER_COUNT = 6;

    // The v5 monitoring station requires user ID param. Use this dummy
    // value, if v6 message does not have the optional header field 'userId'.
    private static final String DUMMY_USER_ID = "EE00000000000";

    // Value length constraints of the parameters in the v5 monitoring station.
    private static final int QDB_STRING_LENGTH = 100;
    private static final int QDB_USER_ID_LENGTH = 20;

    @Override
    public void onReceive(Object message) {
        if (message instanceof SuccessfulMessage) {
            SuccessfulMessage m = (SuccessfulMessage) message;
            log.debug("Received: success({}, {}, {})",
                    new Object[] {m.getMessage(), m.getStartTime(),
                        m.getEndTime()});

            send(socket(m.getMessage().getOrigin()), QUERY_NOTIFICATION,
                    queryInfo(m.getMessage(), m.getStartTime(),
                            m.getEndTime()));
        } else if (message instanceof ServerProxyFailed) {
            ServerProxyFailed m = (ServerProxyFailed) message;
            log.debug("Received: serverProxyFailed({})", m.getMessage());

            Pair<MessageParam, String>[] queryInfo =
                    queryInfo(m.getMessage(), null, null);
            // Replace the query name with provider.state so that
            // zabbix can filter for it.
            // TODO implement the replacement better.
            queryInfo[2] = of(QUERY_NAME,
                    convertId(m.getMessage().getService().getClientId()) + "."
                            + m.getMessage().getService().getServiceCode());

            send(senderSocket(), QUERY_NOTIFICATION, queryInfo);
        } else if (message instanceof FaultInfo) {
            FaultInfo m = (FaultInfo) message;
            log.debug("Received: failure({}, {}, {})",
                    new Object[] {m.getMessage(), m.getFaultCode(), m.getFaultMessage()});

            // Send the query info with null times to record the failed query.
            if (m.getMessage() != null) {
                send(senderSocket(), QUERY_NOTIFICATION,
                        queryInfo(m.getMessage(), null, null));
            }
            // Send the alert for fault.
            if (m.getFaultCode() != null || m.getFaultMessage() != null) {
                send(senderSocket(), ALERT,
                        faultInfo(m.getFaultCode(), m.getFaultMessage()));
            }
        } else {
            log.error("Received: unknown message {}", message);
        }
    }

    private String socket(Origin origin) {
        switch (origin) {
            case CLIENT_PROXY:
                return localSocket();
            case SERVER_PROXY:
                return senderSocket();
            default: // to satisfy compiler.
                throw new IllegalArgumentException(origin.toString());
        }
    }

    private String localSocket() {
        return System.getProperty(
                "ee.cyber.xroad.monitoragent.localQuerySocket",
                "/usr/xtee/var/run/local_query_socket");
    }

    private String senderSocket() {
        return System.getProperty(
                "ee.cyber.xroad.monitoragent.senderSocket",
                "/usr/xtee/var/run/sender_socket");
    }

    private Pair<MessageParam, String>[] queryInfo(MessageInfo message,
            Date startTime, Date endTime) {
        Pair<MessageParam, String>[] ret = new Pair[QUERY_PARAMETER_COUNT];

        int idx = 0;
        ret[idx++] = of(QUERY_CONSUMER,
                cutIfToLong(convertId(message.getClient()), QDB_STRING_LENGTH));
        ret[idx++] = of(QUERY_USER_ID, StringUtils.isEmpty(message.getUserId())
                ? DUMMY_USER_ID
                : cutIfToLong(message.getUserId(), QDB_USER_ID_LENGTH));
        ret[idx++] = of(QUERY_NAME, cutIfToLong(convertId(
                message.getService().getClientId()), QDB_STRING_LENGTH) + "."
                + cutIfToLong(message.getService().getServiceCode(),
                QDB_STRING_LENGTH));
        ret[idx++] = of(QUERY_ID,
                cutIfToLong(message.getQueryId(), QDB_STRING_LENGTH));
        ret[idx++] = of(QUERY_START_TIME, convertDate(startTime));
        ret[idx] = of(QUERY_END_TIME, convertDate(endTime));

        return ret;
    }

    private Pair<MessageParam, String>[] faultInfo(String faultCode,
            String faultMessage) {
        Pair<MessageParam, String>[] ret = new Pair[2];

        ret[0] = of(ERROR_CODE, faultCode);
        ret[1] = of(ERROR_STRING, faultMessage);

        return ret;
    }

    private static String convertId(ClientId clientId) {
        // TODO perform conversion
        return clientId.getMemberCode();
    }

    private static String convertDate(Date d) {
        if (d == null) {
            return "0";
        } else {
            // Get time in milliseconds and convert to string.
            return String.valueOf(d.getTime());
        }
    }

    @SneakyThrows
    private static String cutIfToLong(String str, int maxBytes) {
        byte[] utf8 = str.getBytes("UTF-8");

        if (utf8.length <= maxBytes) {
            return str;
        }

        // FUTURE: if the utf8 brutal cut causes problems then
        // try to cut between utf8 characters.
        return new String(utf8, 0, maxBytes, "UTF-8");
    }
}
