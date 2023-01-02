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

import io.cucumber.java.en.Step;
import org.niis.xroad.centralserver.openapi.model.TokenDto;
import org.niis.xroad.centralserver.openapi.model.TokenPasswordDto;
import org.niis.xroad.cs.test.api.FeignTokensApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

public class TokensApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignTokensApi tokensApi;

    @Step("User can login token {string} with password {string}")
    public void userTriesToLoginTokenWithPassword(String tokenId, String password) {
        final ResponseEntity<TokenDto> response = tokensApi.loginToken(tokenId, new TokenPasswordDto().password(password));

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(tokenId, "body.id", "Token should be returned"))
                .execute();
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
        final ResponseEntity<TokenDto> response = tokensApi.logoutToken(tokenId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(tokenId, "body.id", "Token should be returned"))
                .execute();
    }


}
