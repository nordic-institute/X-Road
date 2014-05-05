package ee.cyber.xroad.monitoragent;

import java.util.Date;

import akka.actor.UntypedActor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.monitoring.FaultInfo;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.ServerProxyFailed;
import ee.cyber.sdsb.common.monitoring.SuccessfulMessage;

import static ee.cyber.sdsb.common.monitoring.MessageInfo.Origin;
import static ee.cyber.xroad.monitoragent.DataSender.send;
import static ee.cyber.xroad.monitoragent.MessageParam.*;
import static ee.cyber.xroad.monitoragent.MessageType.ALERT;
import static ee.cyber.xroad.monitoragent.MessageType.QUERY_NOTIFICATION;
import static org.apache.commons.lang3.tuple.ImmutablePair.of;

public class DataReceiver extends UntypedActor {
    private static final Logger LOG = LoggerFactory.getLogger(
            DataReceiver.class);

    @Override
    public void onReceive(Object message) {
        if (message instanceof SuccessfulMessage) {
            SuccessfulMessage m = (SuccessfulMessage) message;
            LOG.debug("Received: success({}, {}, {})",
                    new Object[] {m.message, m.startTime, m.endTime});

            send(socket(m.message.origin), QUERY_NOTIFICATION,
                    queryInfo(m.message, m.startTime, m.endTime));
        } else if (message instanceof ServerProxyFailed) {
            ServerProxyFailed m = (ServerProxyFailed) message;
            LOG.debug("Received: serverProxyFailed({})", m.message);

            Pair<MessageParam, String>[] queryInfo =
                    queryInfo(m.message, null, null);
            // Replace the query name with provider.state so that
            // zabbix can filter for it.
            // TODO: implement the replacement better.
            queryInfo[2] = of(QUERY_NAME,
                    convertId(m.message.service.getClientId()) + "."
                            + m.message.service.getServiceCode());

            send(senderSocket(), QUERY_NOTIFICATION, queryInfo);
        } else if (message instanceof FaultInfo) {
            FaultInfo m = (FaultInfo) message;
            LOG.debug("Received: failure({}, {}, {})",
                    new Object[] {m.message, m.faultCode, m.faultMessage});

            // Send the query info with null times to record the failed query.
            if (m.message != null) {
                send(senderSocket(), QUERY_NOTIFICATION,
                        queryInfo(m.message, null, null));
            }
            // Send the alert for fault.
            if (m.faultCode != null || m.faultMessage != null) {
                send(senderSocket(), ALERT,
                        faultInfo(m.faultCode, m.faultMessage));
            }
        } else {
            LOG.error("Received: unknown message {}", message);
        }
    }

    private String socket(Origin origin) {
        // TODO: extract the constants to some class.
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
        Pair<MessageParam, String>[] ret = new Pair[6];

        ret[0] = of(QUERY_CONSUMER, convertId(message.client));
        ret[1] = of(QUERY_USER_ID, message.userId);
        ret[2] = of(QUERY_NAME,
                convertId(message.service.getClientId()) + "."
                        + message.service.getServiceCode());
        ret[3] = of(QUERY_ID, message.queryId);
        ret[4] = of(QUERY_START_TIME, convertDate(startTime));
        ret[5] = of(QUERY_END_TIME, convertDate(endTime));

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
        // TODO: perform conversion
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
}
