/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.asyncdb.messagequeue;

import java.util.Date;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.DateUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ee.ria.xroad.asyncdb.AsyncSenderConf;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Encapsulates data specific to message queue generally
 */
@Slf4j
public class QueueInfo implements Cloneable {
    private static final int NEXT_ATTEMPT_POWER_LIMIT = 30;

    @Getter
    private ClientId name;

    @Getter
    private int requestCount;

    @Getter
    private int firstRequestNo;

    @Getter
    private Date lastSentTime;

    @Getter
    private int firstRequestSendCount;
    private String lastSuccessId;
    private Date lastSuccessTime;
    private String lastSendResult;

    /**
     * Creates new QueueInfo.
     *
     * @param name - provider of the queue
     * @param state - encapsulates current state of the queue.
     */
    public QueueInfo(ClientId name, QueueState state) {
        this.name = name;
        this.requestCount = state.getRequestCount();
        this.firstRequestNo = state.getFirstRequestNo();
        this.lastSentTime = state.getLastSentTime();
        this.firstRequestSendCount = state.getFirstRequestSendCount();
        this.lastSuccessId = state.getLastSuccessId();
        this.lastSuccessTime = state.getLastSuccessTime();
        this.lastSendResult = state.getLastSendResult();
    }

    /**
     * Last successful request ID sent from this queue.
     *
     * @return - ID from request.
     */
    public String getLastSuccessId() {
        return lastSuccessId == null ? "" : lastSuccessId;
    }

    /**
     * Returns time when last successful request from queue was sent.
     *
     * @return - last successful request sending time.
     */
    public Date getLastSuccessTime() {
        return lastSuccessTime;
    }

    /**
     * Returns result of last sent request.
     *
     * @return - result of last request sent.
     */
    public String getLastSendResult() {
        return lastSendResult == null ? "" : lastSendResult;
    }

    /**
     * Returns order number (index) of next request in the queue
     *
     * @return - index of next request in the queue
     */
    public int getNextRequestNo() {
        return requestCount == 0 ? 0 : requestCount + firstRequestNo;
    }

    /**
     * Returns time when next request will be sent.
     *
     * @return - time in the future when next request will be sent.
     */
    public Date getNextAttempt() {

        if (requestCount == 0) {
            return null;
        }
        if (lastSentTime == null) {
            return new Date();
        }

        AsyncSenderConf conf = new AsyncSenderConf();
        int maxDelay = conf.getMaxDelay();

        int delayInSeconds;

        // Number small enough to prevent integer overflow and large enough
        // to enable delays always larger than largest realistic base delay.

        if (firstRequestSendCount == 0) {
            delayInSeconds = 0;
        } else if (firstRequestSendCount <= NEXT_ATTEMPT_POWER_LIMIT) {
            int rawDelay = (int) Math.pow(2, firstRequestSendCount - 1)
                    * conf.getBaseDelay();
            delayInSeconds = rawDelay > maxDelay ? maxDelay : rawDelay;
        } else {
            delayInSeconds = maxDelay;
        }
        return DateUtils.addSeconds(lastSentTime, delayInSeconds);
    }

    /**
     * Turns queue info into JSON format.
     *
     * @return - queue info in JSON.
     */
    public String toJson() {
        return JsonUtils.getSerializer().toJson(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
    // Static factory methods - start

    /**
     * Returns new queue info for the client.
     *
     * @param client - client to get queue info for.
     * @return - initial queue info for particular client.
     */
    public static QueueInfo getNew(ClientId client) {
        return new QueueInfo(
                client, new QueueState(0, 0, null, 0, null, null, null));
    }

    /**
     * Adds request with returning new queue info.
     *
     * @param provider - queue info before adding request.
     * @return - new queue info with added request.
     */
    public static QueueInfo addRequest(QueueInfo provider) {
        QueueInfo result = getCopy(provider);
        result.requestCount = provider.requestCount + 1;
        return result;
    }

    /**
     * Returns new queue info with first request removed from the previous.
     * Last send result not included.
     *
     * @param queueInfo - initial queue info.
     * @param id - last successful request id.
     * @return - new queue info with first request removed.
     */
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

    /**
     * Returns new queue info with first request removed from the previous.
     * Last send result included.
     *
     * @param queueInfo - initial queue info.
     * @param id - last successful request id.
     * @param lastSendResult - result of last sent request.
     * @return - new queue info with first request removed.
     */
    public static QueueInfo removeFirstRequest(QueueInfo queueInfo, String id,
            String lastSendResult) {
        QueueInfo firstRemoved = removeFirstRequest(queueInfo, id);
        firstRemoved.lastSendResult = lastSendResult;
        return firstRemoved;
    }

    /**
     * Returns new queue after corrupt request is removed from the previous one.
     *
     * @param queueInfo - queue info before updating.
     * @return - queue info with corrupt request removed.
     */
    public static QueueInfo removeCorruptRequest(QueueInfo queueInfo) {
        QueueState state = new QueueState(
                queueInfo.getRequestCount() - 1,
                queueInfo.getFirstRequestNo() + 1,
                queueInfo.getLastSentTime(),
                queueInfo.getFirstRequestSendCount(),
                queueInfo.getLastSuccessId(),
                queueInfo.getLastSuccessTime(),
                queueInfo.getLastSendResult()
        );

        return new QueueInfo(queueInfo.getName(), state);
    }

    /**
     * Returns new queue info after request has failed.
     *
     * @param initial - queue info initially.
     * @param lastSendResult - result of last request sending.
     * @return - new queue info after failed request.
     */
    public static QueueInfo handleFailedRequest(QueueInfo initial,
            String lastSendResult) {
        QueueInfo result = getCopy(initial);
        result.lastSentTime = new Date();
        result.firstRequestSendCount = initial.firstRequestSendCount + 1;
        result.lastSendResult = lastSendResult;
        return result;
    }

    /**
     * Returns new queue info with send count reset.
     *
     * @param initial - queue info before setting send count.
     * @return - queue info after send count has been set.
     */
    public static QueueInfo resetSendCount(QueueInfo initial) {
        QueueInfo result = getCopy(initial);
        result.firstRequestSendCount = 0;
        return result;
    }

    /**
     * Parses queue info from respective JSON string.
     *
     * @param json - JSON string to parse queue info from.
     * @return - queue info parsed.
     *
     * @throws CorruptQueueException - when no queue info cannot be parsed.
     */
    public static QueueInfo fromJson(String json) throws CorruptQueueException {
        JsonObject rawQueue;

        try {
            rawQueue = (JsonObject) new JsonParser().parse(json);
        } catch (ClassCastException e) {
            throw new CorruptQueueException("Json string '" + json
                    + "' cannot be parsed into QueueInfo.");
        }

        JsonObject rawName = rawQueue.get("name").getAsJsonObject();
        ClientId name = ClientId.create(
                JsonUtils.getStringPropertyValue(rawName, "xRoadInstance"),
                JsonUtils.getStringPropertyValue(rawName, "memberClass"),
                JsonUtils.getStringPropertyValue(rawName, "memberCode"),
                JsonUtils.getStringPropertyValue(rawName, "subsystemCode"));

        QueueState state = new QueueState(
                JsonUtils.getIntPropertyValue(rawQueue, "requestCount"),
                JsonUtils.getIntPropertyValue(rawQueue, "firstRequestNo"),
                JsonUtils.getDatePropertyValue(rawQueue, "lastSentTime"),
                JsonUtils.getIntPropertyValue(rawQueue, "firstRequestSendCount"),
                JsonUtils.getStringPropertyValue(rawQueue, "lastSuccessId"),
                JsonUtils.getDatePropertyValue(rawQueue, "lastSuccessTime"),
                JsonUtils.getStringPropertyValue(rawQueue, "lastSendResult")
        );

        return new QueueInfo(name, state);
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
