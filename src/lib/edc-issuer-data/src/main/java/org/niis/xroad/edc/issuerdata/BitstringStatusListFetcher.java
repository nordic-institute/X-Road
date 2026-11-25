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

package org.niis.xroad.edc.issuerdata;

import jakarta.annotation.Nonnull;
import okhttp3.Request;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.keys.spi.PublicKeyResolver;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Bitstring statuslist credentials, downloaded and read the way the stock revocation service does it.
 */
public class BitstringStatusListFetcher implements IssuerDataFetcher<BitstringStatusListCredential> {

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String VC_CLAIM = "vc";

    private final ObjectMapper mapper;
    private final String acceptHeader;
    private final IssuerDataDownload download;
    private final TokenValidationService tokenValidationService;
    private final DidPublicKeyResolver didPublicKeyResolver;

    public BitstringStatusListFetcher(ObjectMapper mapper, Collection<String> acceptedContentTypes, EdcHttpClient httpClient,
                                      TokenValidationService tokenValidationService,
                                      DidPublicKeyResolver didPublicKeyResolver) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.acceptHeader = String.join(",", acceptedContentTypes);
        this.download = new IssuerDataDownload(httpClient);
        this.tokenValidationService = tokenValidationService;
        this.didPublicKeyResolver = didPublicKeyResolver;
    }

    @Override
    public IssuerDataKind kind() {
        return IssuerDataKind.STATUSLIST;
    }

    @Override
    @Nonnull
    public Result<Download> download(String key) {
        Request request;
        try {
            request = new Request.Builder().url(key).header("Accept", acceptHeader).get().build();
        } catch (IllegalArgumentException _) {
            return Result.failure("Statuslist credential URL is not valid: " + key);
        }
        return download.get(request, "the statuslist credential from " + key);
    }

    @Override
    @Nonnull
    public Result<BitstringStatusListCredential> parse(String key, String payload) {
        return read(key, payload, didPublicKeyResolver);
    }

    @Override
    @Nonnull
    public Result<BitstringStatusListCredential> parseStored(String key, String payload) {
        return read(key, payload, TokenValidationService.NO_SIGNATURE_VERIFICATION);
    }

    private Result<BitstringStatusListCredential> read(String key, String payload, PublicKeyResolver keys) {
        try {
            var credential = JSON_CONTENT_TYPE.equals(acceptHeader) ? readJson(payload) : readJwt(payload, keys);
            if (credential.failed()) {
                return credential;
            }
            if (credential.getContent() == null || credential.getContent().getCredentialSubject().isEmpty()) {
                return Result.failure("Statuslist credential from %s contains no credentialSubject".formatted(key));
            }
            return credential;
        } catch (JacksonException e) {
            return Result.failure("Statuslist credential from %s is malformed: %s".formatted(key, e.getOriginalMessage()));
        } catch (IllegalArgumentException e) {
            return Result.failure("Statuslist credential from %s is malformed: %s".formatted(key, e.getMessage()));
        }
    }

    @Override
    @Nonnull
    public Optional<Instant> ownExpiry(BitstringStatusListCredential parsed) {
        return Optional.ofNullable(parsed.validUntil());
    }

    private Result<BitstringStatusListCredential> readJson(String payload) {
        return Result.success(mapper.readValue(payload, BitstringStatusListCredential.class));
    }

    private Result<BitstringStatusListCredential> readJwt(String payload, PublicKeyResolver keys) {
        var validated = tokenValidationService.validate(payload, keys, List.of());
        if (validated.failed()) {
            return validated.mapFailure();
        }
        var claims = validated.getContent();
        var vc = Optional.ofNullable(claims.getClaim(VC_CLAIM)).orElseGet(claims::getClaims);
        return Result.success(mapper.convertValue(vc, BitstringStatusListCredential.class));
    }
}
