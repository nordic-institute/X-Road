/*
 * The MIT License
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
package org.niis.xroad.ss.test.ui.hook;

import com.nortal.test.core.file.ClasspathFileResolver;
import com.nortal.test.core.services.hooks.BeforeSuiteHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Parameter;
import org.niis.xroad.ss.test.ui.container.MockServerService;
import org.springframework.stereotype.Component;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockServerBeforeSuiteHook implements BeforeSuiteHook {
    private static final String PREFIX_TESTSERVICES = "/test-services/";

    private static final String GLOBALCONF_DIR = "files/globalconf/V3/";
    private static final String TESTSERVICES_DIR = "files" + PREFIX_TESTSERVICES;
    private static final String CONF_PART_DIR = "20231113171950380797000";

    private final ClasspathFileResolver classpathFileResolver;
    private final MockServerService mockServerService;

    @Override
    public void beforeSuite() {
        mockFileResponse(GLOBALCONF_DIR, "/", "internalconf", new Parameter("version", "3"));
        mockFileResponse(GLOBALCONF_DIR, "/", "externalconf", new Parameter("version", "3"));
        mockFileResponse(GLOBALCONF_DIR, "/V3/", CONF_PART_DIR + "/private-params.xml");
        mockFileResponse(GLOBALCONF_DIR, "/V3/", CONF_PART_DIR + "/shared-params.xml");
        mockFileResponse(GLOBALCONF_DIR, "/V3/", CONF_PART_DIR + "/fetchinterval-params.xml");

        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testopenapi1.yaml");
        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testopenapi11.yaml");
        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testopenapi2.json");

        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testservice1.wsdl");
        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testservice2.wsdl");
        mockFileResponse(TESTSERVICES_DIR, PREFIX_TESTSERVICES, "testservice3.wsdl");

//        mockManagementRequest();
    }

    private void mockFileResponse(String fileDir, String pathPrefix, String path) {
        mockFileResponse(fileDir, pathPrefix, path, null);
    }

    private void mockFileResponse(String fileDir, String pathPrefix, String path, Parameter queryParam) {
        var expectations = mockServerService.client()
                .when(request()
                        .withPath(pathPrefix + path)
                        .withQueryStringParameter(queryParam)
                )
                .respond(response()
                        .withBody(classpathFileResolver.getFileAsString(fileDir + path))
                );

        for (Expectation expectation : expectations) {
            log.info("Registered new mock-service response for request {}", expectation.getHttpRequest().toString());
        }
    }


    private void mockManagementRequest() {
        var expectations = mockServerService.client()
                .when(request()
                        .withMethod("POST")
                        .withPath("/managementservice/")
                )
                .respond(response()
                        .withBody(classpathFileResolver.getFileAsString("files/soap-responses/management-response-1.xml"))
                );

        for (Expectation expectation : expectations) {
            log.info("Registered new mock-service response for request {}", expectation.getHttpRequest().toString());
        }


    }
}
