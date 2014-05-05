package ee.cyber.sdsb.common.monitoring;

import java.util.Date;

public interface MonitorAgentProvider {

    void success(MessageInfo messageInfo, Date startTime, Date endTime);

    void serverProxyFailed(MessageInfo messageInfo);

    void failure(MessageInfo messageInfo, String faultCode,
            String faultMessage);
}
