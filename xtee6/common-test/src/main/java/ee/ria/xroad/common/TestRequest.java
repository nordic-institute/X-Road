package ee.ria.xroad.common;

import java.util.List;

import lombok.Getter;

import ee.ria.xroad.common.Request.RequestTag;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Encapsulates test request data.
 */
public class TestRequest {
    private ServiceId service;
    private String description;

    @Getter
    private String content;

    @Getter
    private Integer timeoutSec = null;

    /**
     * Constructs a new test request with the given template, data and a boundary
     * to use in case of a multipart template.
     * @param template XML template of this request
     * @param client ID of the client that makes this request
     * @param service ID of the service this request is for
     * @param content list of request tags that should be placed in the body
     * @param boundary boundary to use in case of a multipart template
     * @param description description of this test request
     * @param timeoutSec timeout of this test request
     */
    public TestRequest(String template, ClientId client, ServiceId service,
            List<RequestTag> content, String boundary, String description,
            Integer timeoutSec) {
        this.service = service;

        this.content = new Request(template, client, service, "1234567890",
                content, boundary).toRawContent();

        this.description = description;
        this.timeoutSec = timeoutSec;
    }

    /**
     * @return service code of the test request service
     */
    // TODO May be we should show entire service id instead?
    public String getName() {
        return service.getServiceCode();
    }

    /**
     * @return description of this test request or service code of the test
     * request service if description is not available
     */
    public String getDescription() {
        if (description == null) {
            return getName();
        }

        return description;
    }
}
