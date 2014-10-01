package ee.cyber.sdsb.asyncdb.messagequeue;

import java.util.Date;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.DateUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ee.cyber.sdsb.asyncdb.AsyncSenderConf;
import ee.cyber.sdsb.asyncdb.JsonUtils;
import ee.cyber.sdsb.common.identifier.ClientId;

/**
 * Encapsulates data specific to message queue generally
 */
public class QueueInfo implements Cloneable {

    private ClientId name;
    private int requestCount;
    private int firstRequestNo;
    private Date lastSentTime;
    private int firstRequestSendCount;
    private String lastSuccessId;
    private Date lastSuccessTime;
    private String lastSendResult;

    public QueueInfo(ClientId name, int requestCount, int firstRequestNo,
            Date lastSentTime, int firstRequestSendCount, String lastSuccessId,
            Date lastSuccessTime, String lastSendResult) {
        this.name = name;
        this.requestCount = requestCount;
        this.firstRequestNo = firstRequestNo;
        this.lastSentTime = lastSentTime;
        this.firstRequestSendCount = firstRequestSendCount;
        this.lastSuccessId = lastSuccessId;
        this.lastSuccessTime = lastSuccessTime;
        this.lastSendResult = lastSendResult;
    }

    public ClientId getName() {
        return name;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getFirstRequestNo() {
        return firstRequestNo;
    }

    public Date getLastSentTime() {
        return lastSentTime;
    }

    public int getFirstRequestSendCount() {
        return firstRequestSendCount;
    }

    public String getLastSuccessId() {
        return lastSuccessId == null ? "" : lastSuccessId;
    }

    public Date getLastSuccessTime() {
        return lastSuccessTime;
    }

    public String getLastSendResult() {
        return lastSendResult == null ? "" : lastSendResult;
    }

    public int getNextRequestNo() {
        return requestCount == 0 ? 0 : requestCount + firstRequestNo;
    }

    public Date getNextAttempt() {

        if (requestCount == 0) {
            return null;
        }
        if (lastSentTime == null) {
            return new Date();
        }

        AsyncSenderConf conf = new AsyncSenderConf();
        int maxDelay = conf.getMaxDelay();

        int delayInSeconds = 0;

        // Number small enough to prevent integer overflow and large enough
        // to enable delays always larger than largest realistic base delay.
        int powerLimit = 30;

        if (firstRequestSendCount == 0) {
            delayInSeconds = 0;
        } else if (firstRequestSendCount <= powerLimit) {
            int rawDelay = (int) Math.pow(2, firstRequestSendCount - 1)
                    * conf.getBaseDelay();
            delayInSeconds = rawDelay > maxDelay ? maxDelay : rawDelay;
        } else {
            delayInSeconds = maxDelay;
        }
        return DateUtils.addSeconds(lastSentTime, delayInSeconds);
    }

    public String toJson() {
        return JsonUtils.getSerializer().toJson(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
    // Static factory methods - start

    public static QueueInfo getNew(ClientId name) {
        return new QueueInfo(name, 0, 0, null, 0, null, null, null);
    }

    public static QueueInfo addRequest(QueueInfo provider) {
        QueueInfo result = getCopy(provider);
        result.requestCount = provider.requestCount + 1;
        return result;
    }

    public static QueueInfo removeFirstRequest(QueueInfo queueInfo, String id) {
        int initialRequestCount = queueInfo.getRequestCount();

        if (initialRequestCount == 0) {
            throw new IllegalArgumentException(
                    "Cannot remove request from queue with no requests.");
        }

        int firstRequestNo = initialRequestCount == 1 ? 0
                : queueInfo.getFirstRequestNo() + 1;

        QueueInfo result = getCopy(queueInfo);
        result.lastSentTime = new Date();
        result.firstRequestSendCount = 0;
        result.requestCount = queueInfo.requestCount - 1;
        result.firstRequestNo = firstRequestNo;
        result.lastSuccessId = id;
        result.lastSuccessTime = new Date();
        return result;
    }

    public static QueueInfo removeFirstRequest(QueueInfo queueInfo, String id,
            String lastSendResult) {
        QueueInfo firstRemoved = removeFirstRequest(queueInfo, id);
        firstRemoved.lastSendResult = lastSendResult;
        return firstRemoved;
    }

    public static QueueInfo removeCorruptRequest(QueueInfo queueInfo) {
        QueueInfo result = new QueueInfo(
                queueInfo.getName(),
                queueInfo.getRequestCount() - 1,
                queueInfo.getFirstRequestNo() + 1,
                queueInfo.getLastSentTime(),
                queueInfo.getFirstRequestSendCount(),
                queueInfo.getLastSuccessId(),
                queueInfo.getLastSuccessTime(),
                queueInfo.getLastSendResult());

        return result;
    }

    public static QueueInfo handleFailedRequest(QueueInfo initial,
            String lastSendResult) {
        QueueInfo result = getCopy(initial);
        result.lastSentTime = new Date();
        result.firstRequestSendCount = initial.firstRequestSendCount + 1;
        result.lastSendResult = lastSendResult;
        return result;
    }

    public static QueueInfo resetSendCount(QueueInfo initial) {
        QueueInfo result = getCopy(initial);
        result.firstRequestSendCount = 0;
        return result;
    }

    public static QueueInfo fromJson(String json) throws CorruptQueueException {
        JsonObject rawQueue = null;

        try {
            rawQueue = (JsonObject) new JsonParser().parse(json);
        } catch (ClassCastException e) {
            throw new CorruptQueueException("Json string '" + json
                    + "' cannot be parsed into QueueInfo.");
        }

        JsonObject rawName = rawQueue.get("name").getAsJsonObject();
        ClientId name = ClientId.create(
                JsonUtils.getStringPropertyValue(rawName, "sdsbInstance"),
                JsonUtils.getStringPropertyValue(rawName, "memberClass"),
                JsonUtils.getStringPropertyValue(rawName, "memberCode"),
                JsonUtils.getStringPropertyValue(rawName, "subsystemCode"));

        return new QueueInfo(
                name,
                JsonUtils.getIntPropertyValue(rawQueue, "requestCount"),
                JsonUtils.getIntPropertyValue(rawQueue, "firstRequestNo"),
                JsonUtils.getDatePropertyValue(rawQueue, "lastSentTime"),
                JsonUtils.getIntPropertyValue(rawQueue, "firstRequestSendCount"),
                JsonUtils.getStringPropertyValue(rawQueue, "lastSuccessId"),
                JsonUtils.getDatePropertyValue(rawQueue, "lastSuccessTime"),
                JsonUtils.getStringPropertyValue(rawQueue, "lastSendResult"));
    }

    // Static factory methods - end

    private static QueueInfo getCopy(QueueInfo queueInfo) {
        try {
            return (QueueInfo) queueInfo.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Cannot clone RequestInfo: ", e);
        }
    }
}
