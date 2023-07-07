/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.test.glue;

import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.TokenDto;
import org.niis.xroad.cs.openapi.model.TokenPasswordDto;
import org.niis.xroad.cs.test.api.FeignTokensApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class TokensApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignTokensApi tokensApi;

    private ResponseEntity<TokenDto> response;
    private String requestTokenId;

    @Step("User can login token {string} with password {string}")
    public void userTriesToLoginTokenWithPassword(String tokenId, String password) {
        try {
            response = tokensApi.loginToken(tokenId, new TokenPasswordDto().password(password));
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
        }

        this.requestTokenId = tokenId;
    }

    @Step("Token login is successful")
    public void loginRequestIsValidated() {
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(requestTokenId, "body.id", "Token should be returned"))
                .execute();
    }

    @Step("Signer.getTokens response is mocked")
    public void signerGetTokensResponseIsMocked() {
        var tokenListString = "[" + getTokenString("0", true) + "," + getTokenString("1", true) + "]";

        mockServerService.client()
                .when(request()
                        .withPath("/getTokens"))
                .respond(response()
                        .withBody(tokenListString).withContentType(APPLICATION_JSON));
    }

    @Step("Signer.getToken response is mocked for token {string}")
    public void signerGetTokenResponseIsMockedForTokenTokenId(String tokenId) {
        mockGetTokenResponse(tokenId, false);
    }

    @Step("Signer.getToken response is mocked for active token {string}")
    public void signerGetTokenResponseIsMockedForActiveTokenTokenId(String tokenId) {
        mockGetTokenResponse(tokenId, true);
    }

    private void mockGetTokenResponse(String tokenId, boolean active) {
        var tokenString = getTokenString(tokenId, active);

        mockServerService.client()
                .when(request()
                        .withPath("/getToken/" + tokenId))
                .respond(response()
                        .withBody(tokenString).withContentType(APPLICATION_JSON));
    }

    private String getTokenString(String id, boolean active) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"active\":" + active + ","
                + "\"type\":\"type\","
                + "\"friendlyName\":\"friendlyName\","
                + "\"readOnly\":false,\"available\":true,"
                + "\"serialNumber\":\"serialNumber\","
                + "\"label\":\"label\",\"slotIndex\":13,\"status\":\"OK\",\"keyInfo\":[],\"tokenInfo\":{},"
                + "\"savedToConfiguration\":false}";
    }

    @Step("Signer.activateToken is mocked for token {string}")
    public void signerActivateTokenIsMockedForTokenTokenId(String tokenId) {
        mockServerService.client()
                .when(request()
                        .withMethod("PUT")
                        .withPath("/activateToken/" + tokenId))
                .respond(response().withStatusCode(NO_CONTENT.value()));
    }

    @Step("Signer.deactivateToken is mocked for token {string}")
    public void signerDeactivateTokenIsMockedForTokenTokenId(String tokenId) {
        mockServerService.client()
                .when(request()
                        .withMethod("PUT")
                        .withPath("/deactivateToken/" + tokenId))
                .respond(response().withStatusCode(NO_CONTENT.value()));
    }

    @Step("User can logout token {string}")
    public void userCanLogoutTokenTokenId(String tokenId) {
        try {
            response = tokensApi.logoutToken(tokenId);
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
        }
        this.requestTokenId = tokenId;
    }


    @Step("Token logout token is successful")
    public void logoutRequestIsValidated() {
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(requestTokenId, "body.id", "Token should be returned"))
                .execute();
    }
}
