package ee.ria.xroad.asyncdb.messagequeue;

import java.util.Date;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;

/**
 * Encapsulates data specific to single request.
 */
public class RequestInfo implements Cloneable {

    @Getter
    private int orderNo;

    @Getter
    private String id;

    @Getter
    private Date receivedTime;

    @Getter
    private Date removedTime;

    @Getter
    private ClientId sender;

    private String user;

    @Getter
    private ServiceId service;

    @Getter
    private boolean sending;

    /**
     * Constructor for request info.
     *
     * @param orderNo - order of request in queue.
     * @param id - id from request message.
     * @param receivedTime - time when request was received.
     * @param removedTime - time when request was removed from the queue.
     * @param sender - id of client who sent request.
     * @param user - user ID from request message.
     * @param service - service to be consumed.
     */
    public RequestInfo(int orderNo, String id, Date receivedTime,
            Date removedTime, ClientId sender, String user, ServiceId service) {
        this.orderNo = orderNo;
        this.id = id;
        this.receivedTime = receivedTime;
        this.removedTime = removedTime;
        this.sender = sender;
        this.user = user;
        this.service = service;
    }

    /**
     * Returns user ID from request.
     *
     * @return user ID or empty string.
     */
    public String getUser() {
        return user == null ? "" : user;
    }

    /**
     * Returns JSON representation of request info.
     *
     * @return - request info as JSON.
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
     * Creates new request info from SOAP message.
     *
     * @param orderNo - order of the request.
     * @param message - SOAP message received.
     * @return - brand new request info.
     */
    public static RequestInfo getNew(int orderNo, SoapMessageImpl message) {
        return new RequestInfo(
                orderNo,
                message.getQueryId(),
                new Date(),
                null,
                message.getClient(),
                message.getUserId(),
                message.getService());
    }

    /**
     * Creates new request info that is marked as sending.
     *
     * @param requestInfo - original request info.
     * @return - same as original request info, but marked as sending.
     */
    public static RequestInfo markSending(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.sending = true;
        return result;
    }


    /**
     * Creates new request info that is unmarked as sending.
     *
     * @param requestInfo - original request info.
     * @return - same as original request info, but unmarked as sending.
     */
    public static RequestInfo unmarkSending(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.sending = false;
        return result;
    }

    /**
     * Creates new request info that is marked as removed.
     *
     * @param requestInfo - original request info.
     * @return - same as original request info, but marked as removed.
     */
    public static RequestInfo markAsRemoved(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.removedTime = new Date();
        return result;
    }

    /**
     * Creates new request info that is restored.
     *
     * @param requestInfo - original request info.
     * @return - same as original request info, but restored.
     */
    public static RequestInfo restore(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.removedTime = null;
        return result;
    }

    /**
     * Parses request info out of its JSON representation.
     *
     * @param json - JSON representation of request info.
     * @return - Request info object.
     *
     * @throws CorruptQueueException - if queue includes malformed requests.
     */
    public static RequestInfo fromJson(String json)
            throws CorruptQueueException {
        JsonObject rawRequest;

        try {
            rawRequest = (JsonObject) new JsonParser().parse(json);
        } catch (ClassCastException e) {
            throw new CorruptQueueException("Json string '" + json
                    + "' cannot be parsed into RequestInfo.");
        }

        JsonObject rawSender = rawRequest.getAsJsonObject("sender");
        ClientId sender = ClientId.create(
                JsonUtils.getStringPropertyValue(rawSender, "xRoadInstance"),
                JsonUtils.getStringPropertyValue(rawSender, "memberClass"),
                JsonUtils.getStringPropertyValue(rawSender, "memberCode"),
                JsonUtils.getStringPropertyValue(rawSender, "subsystemCode"));

        JsonObject rawService = rawRequest.getAsJsonObject("service");

        ServiceId service = createServiceId(rawService);

        RequestInfo result = new RequestInfo(
                JsonUtils.getIntPropertyValue(rawRequest, "orderNo"),
                JsonUtils.getStringPropertyValue(rawRequest, "id"),
                JsonUtils.getDatePropertyValue(rawRequest, "receivedTime"),
                JsonUtils.getDatePropertyValue(rawRequest, "removedTime"),
                sender,
                JsonUtils.getStringPropertyValue(rawRequest, "user"),
                service);

        result.sending = JsonUtils.getBooleanPropertyValue(rawRequest,
                "sending");

        return result;
    }

    private static ServiceId createServiceId(JsonObject rawService) {
        String instance =
                JsonUtils.getStringPropertyValue(rawService, "xRoadInstance");
        String memberClass =
                JsonUtils.getStringPropertyValue(rawService, "memberClass");
        String memberCode =
                JsonUtils.getStringPropertyValue(rawService, "memberCode");
        String subsystemCode =
                JsonUtils.getStringPropertyValue(rawService, "subsystemCode");
        String serviceCode =
                JsonUtils.getStringPropertyValue(rawService, "serviceCode");

        if (memberClass == null && memberCode == null
                && subsystemCode == null) {
            return CentralServiceId.create(instance, serviceCode);
        } else {
            return ServiceId.create(instance, memberClass, memberCode,
                    subsystemCode, serviceCode);
        }
    }

    // Static factory methods - end

    private static RequestInfo getCopy(RequestInfo requestInfo) {
        try {
            return (RequestInfo) requestInfo.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Cannot clone RequestInfo: ", e);
        }
    }
}
