package org.niis.xroad.cs.test.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.openapi.model.InitialServerConfDto;
import org.niis.xroad.cs.test.api.FeignInitializationApi;
import org.niis.xroad.cs.test.container.service.MockServerService;
import org.springframework.stereotype.Component;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.niis.xroad.cs.test.glue.CommonStepDefs.TokenType.SYSTEM_ADMINISTRATOR;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityServerInitializer {
    private final MockServerService mockServerService;
    private final FeignInitializationApi initializationApi;

    public void initializeWithDefaults() {
        log.info("Initializing CentralServer with default configuration");

        mockSignerInit();
        var authHeader = SYSTEM_ADMINISTRATOR.getHeaderToken();
        var request = new InitialServerConfDto();
        request.setInstanceIdentifier("CS");
        request.setCentralServerAddress("cs");
        request.setSoftwareTokenPin("1234-VALID");

        initializationApi.initCentralServerWithHeader(request, authHeader);
    }

    private void mockSignerInit() {
        mockServerService.client()
                .when(request()
                        .withMethod("PUT")
                        .withPath("/initSoftwareToken/"))
                .respond(response().withStatusCode(NO_CONTENT.value()));
    }
}
