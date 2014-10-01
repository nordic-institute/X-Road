package ee.cyber.sdsb.common;

import java.util.List;

import lombok.Getter;

import ee.cyber.sdsb.common.Request.RequestTag;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public class TestRequest {
    private ServiceId service;
    private String description;

    @Getter
    private String content;

    @Getter
    private Integer timeoutSec = null;


    public TestRequest(String template, ClientId client, ServiceId service,
            List<RequestTag> content, String boundary, String description,
            Integer timeoutSec) {
        this.service = service;

        this.content = new Request(template, client, service, "1234567890",
                content, false, boundary).toRawContent();

        this.description = description;
        this.timeoutSec = timeoutSec;
    }

    // TODO: May be we should show entire service id instead?
    public String getName() {
        return service.getServiceCode();
    }

    public String getDescription() {
        if (description == null) {
            return getName();
        }

        return description;
    }
}
