package org.niis.xroad.ss.test.ui.hook;

import com.nortal.test.core.file.ClasspathFileResolver;
import com.nortal.test.core.services.hooks.BeforeSuiteHook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.mock.Expectation;
import org.niis.xroad.ss.test.ui.container.MockServerService;
import org.springframework.stereotype.Component;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockServerBeforeSuiteHook implements BeforeSuiteHook {
    private static final String PREFIX_TESTSERVICES = "/test-services/";

    private static final String GLOBALCONF_DIR = "files/globalconf/V2/";
    private static final String TESTSERVICES_DIR = "files" + PREFIX_TESTSERVICES;
    private static final String CONF_PART_DIR = "20230614171349563665000";

    private final ClasspathFileResolver classpathFileResolver;
    private final MockServerService mockServerService;

    @Override
    public void beforeSuite() {
        mockFileResponse(GLOBALCONF_DIR, "internalconf", "/");
        mockFileResponse(GLOBALCONF_DIR, "externalconf", "/");
        mockFileResponse(GLOBALCONF_DIR, CONF_PART_DIR + "/private-params.xml", "/v2/");
        mockFileResponse(GLOBALCONF_DIR, CONF_PART_DIR + "/shared-params.xml", "/v2/");
        mockFileResponse(GLOBALCONF_DIR, CONF_PART_DIR + "/fetchinterval-params.xml", "/v2/");

        mockFileResponse(TESTSERVICES_DIR, "testopenapi1.yaml", PREFIX_TESTSERVICES);
        mockFileResponse(TESTSERVICES_DIR, "testopenapi11.yaml", PREFIX_TESTSERVICES);
        mockFileResponse(TESTSERVICES_DIR, "testopenapi2.json", PREFIX_TESTSERVICES);
    }

    private void mockFileResponse(String fileDir, String fileName, String urlPrefix) {
        var expectations = mockServerService.client()
                .when(request()
                        .withPath(urlPrefix + fileName))
                .respond(response()
                        .withBody(classpathFileResolver.getFileAsString(fileDir + fileName))
                );

        for (Expectation expectation : expectations) {
            log.info("Registered new mock-service response for request {}", expectation.getHttpRequest().toString());
        }
    }


}
