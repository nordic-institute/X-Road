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
                content, false, boundary).toRawContent();

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
