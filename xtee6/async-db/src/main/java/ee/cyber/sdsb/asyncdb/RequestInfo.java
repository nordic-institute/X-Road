package ee.cyber.sdsb.asyncdb;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ee.cyber.sdsb.common.identifier.AbstractServiceId;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.message.SoapMessageImpl;

/**
 * Encapsulates data specific to single request.
 */
public class RequestInfo implements Cloneable {

    private int orderNo;
    private String id;
    private Date receivedTime;
    private Date removedTime;
    private ClientId sender;
    private String user;
    private AbstractServiceId service;
    private boolean sending;

    public RequestInfo(int orderNo, String id, Date receivedTime,
            Date removedTime, ClientId sender, String user,
            AbstractServiceId service) {
        this.orderNo = orderNo;
        this.id = id;
        this.receivedTime = receivedTime;
        this.removedTime = removedTime;
        this.sender = sender;
        this.user = user;
        this.service = service;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public Date getReceivedTime() {
        return receivedTime;
    }

    public Date getRemovedTime() {
        return removedTime;
    }

    public ClientId getSender() {
        return sender;
    }

    public String getUser() {
        return user == null ? "" : user;
    }

    public AbstractServiceId getService() {
        return service;
    }

    public boolean isSending() {
        return sending;
    }

    public String toJson() {
        return JsonUtils.getSerializer().toJson(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }

    public void validateHeaderField(String name, String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Header field '" + name
                    + "' must not be blank");
        }
    }

    // Static factory methods - start

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

    public static RequestInfo markSending(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.sending = true;
        return result;
    }

    public static RequestInfo unmarkSending(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.sending = false;
        return result;
    }

    public static RequestInfo markAsRemoved(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.removedTime = new Date();
        return result;
    }

    public static RequestInfo restore(RequestInfo requestInfo) {
        RequestInfo result = getCopy(requestInfo);
        result.removedTime = null;
        return result;
    }

    public static RequestInfo fromJson(String json) {
        JsonObject rawRequest = (JsonObject) new JsonParser().parse(json);

        JsonObject rawSender = rawRequest.getAsJsonObject("sender");
        ClientId sender = ClientId.create(
                JsonUtils.getStringPropertyValue(rawSender, "sdsbInstance"),
                JsonUtils.getStringPropertyValue(rawSender, "memberClass"),
                JsonUtils.getStringPropertyValue(rawSender, "memberCode"),
                JsonUtils.getStringPropertyValue(rawSender, "subsystemCode"));

        JsonObject rawService = rawRequest.getAsJsonObject("service");

        AbstractServiceId service = createServiceId(rawService);

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

    private static AbstractServiceId createServiceId(JsonObject rawService) {
        String instance =
                JsonUtils.getStringPropertyValue(rawService, "sdsbInstance");
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
            RequestInfo result = (RequestInfo) requestInfo.clone();
            return result;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Cannot clone RequestInfo: ", e);
        }
    }
}
