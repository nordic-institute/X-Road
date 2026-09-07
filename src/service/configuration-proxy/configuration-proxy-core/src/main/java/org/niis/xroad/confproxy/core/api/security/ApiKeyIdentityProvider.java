/*
 * The MIT License
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
package org.niis.xroad.confproxy.core.api.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.niis.xroad.confproxy.jpa.service.ApiKeyService;

@ApplicationScoped
@RequiredArgsConstructor
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest> {
    private static final String UPPERCASE_APIKEY_PREFIX = "X-ROAD-APIKEY TOKEN=";

    private final ApiKeyService apiKeyService;

    @Override
    public Class<ApiKeyAuthenticationRequest> getRequestType() {
        return ApiKeyAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ApiKeyAuthenticationRequest request, AuthenticationRequestContext context) {

        return context.runBlocking(() -> {
            try {
                var apiKey = decodeApiKey(request.apiKey());
                var key = apiKeyService.findByKey(apiKey);

                if (key.isEmpty()) {
                    throw new AuthenticationFailedException("Unknown API key");
                }

                var builder = QuarkusSecurityIdentity.builder()
                        .setPrincipal(() -> "api-key-" + apiKey);

                key.get().roles().stream()
                        .map(Enum::name)
                        .forEach(builder::addRole);

                return builder.build();
            } catch (AuthenticationFailedException e) {
                throw e;
            } catch (Exception e) {
                throw new AuthenticationFailedException("Unknown problem when getting API key", e);
            }
        });
    }

    public String decodeApiKey(String authenticationHeader) {
        if (authenticationHeader == null || !Strings.CS.startsWith(authenticationHeader.toUpperCase(), UPPERCASE_APIKEY_PREFIX)) {
            throw new AuthenticationFailedException("Invalid X-Road-Apikey authorization header");
        }
        String apiKey = authenticationHeader.substring(UPPERCASE_APIKEY_PREFIX.length());
        if (StringUtils.isBlank(apiKey)) {
            throw new AuthenticationFailedException("Missing API key from authorization header");
        }
        return apiKey.trim();
    }
}
