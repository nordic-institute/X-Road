/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

package org.niis.xroad.cs.test.glue;

import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.niis.xroad.cs.test.IntTestComposeSetup;
import org.niis.xroad.test.framework.core.asserts.Assertion;
import org.niis.xroad.test.framework.core.asserts.AssertionOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "checkstyle:MagicNumber"})
public class EndpointSecurityStepDefs extends BaseStepDefs {

    @Autowired
    private HttpClient httpClient;
    @Autowired
    private IntTestComposeSetup envSetup;

    @Step("{string} request is sent to url {string}")
    public void getRequestIsSentToUrl(String method, String path) {
        var map = envSetup.getContainerMapping(IntTestComposeSetup.CS, IntTestComposeSetup.Port.API);
        String url = "http://%s:%d/%s".formatted(map.host(), map.port(), path.startsWith("/") ? path.substring(1) : path);

        var req = createRequest(method, url);
        try (var response = (CloseableHttpResponse) httpClient.execute(req)) {
            putStepData(StepDataKey.RESPONSE, response);
        } catch (Exception e) {
            log.error("GET request to url {} failed", url, e);
        }
    }

    private HttpUriRequestBase createRequest(String method, String url) {
        return switch (method.toUpperCase()) {
            case "POST" -> {
                var post = new HttpPost(url);
                post.setEntity(new StringEntity("hello", ContentType.TEXT_PLAIN));
                yield post;
            }
            case "GET" -> new HttpGet(url);
            case "PUT" -> new HttpPut(url);
            case "DELETE" -> new HttpDelete(url);
            case "PATCH" -> new HttpPatch(url);
            case "HEAD" -> new HttpHead(url);
            case "OPTIONS" -> new HttpOptions(url);
            case "TRACE" -> new HttpTrace(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    @Step("Response headers contains no other headers except {string}")
    public void responseHeadersContainsNoOtherHeaders(String headers) {
        Set<String> allowedHeaders = Arrays.stream(StringUtils.split(headers, ","))
                .map(StringUtils::trim)
                .collect(Collectors.toSet());
        HttpResponse response = getRequiredStepData(StepDataKey.RESPONSE);

        for (Header header : response.getHeaders()) {
            validate(header.getName())
                    .assertion(Assertion.builder()
                            .actualValue(header.getName())
                            .expectedValue(allowedHeaders)
                            .operation(AssertionOperation.LIST_CONTAINS_VALUE)
                            .expression(".")

                            .build())
                    .execute();
        }
    }

    @Step("Response with status code {int} is returned")
    public void responseWithStatusCodeIsReturned(int statusCode) {
        HttpResponse response = getRequiredStepData(StepDataKey.RESPONSE);
        validate(response)
                .assertion(equalsStatusCodeAssertion(response.getCode(), HttpStatus.valueOf(statusCode)))
                .execute();
    }

}
