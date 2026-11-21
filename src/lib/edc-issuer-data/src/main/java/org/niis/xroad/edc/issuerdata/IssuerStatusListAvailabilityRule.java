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
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.jwt.spi.JwtRegisteredClaimNames;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tells a peer to try again later when the status list of a credential cannot be read now, instead of refusing the
 * credential for good. The rule only reads the status list through the {@link LastGoodSource}; whether the credential
 * is revoked stays with the stock revocation check.
 * <p>
 * The verdict of a credential is: success when every status list has a usable copy, a retryable failure when one has
 * no copy because the issuer does not answer, and a plain failure when one has no copy because the issuer's answer is
 * not valid. A plain failure wins over a retryable one.
 * <p>
 * A credential carries any status list URL its holder chooses, so the rule does nothing for a credential that is not
 * issued by an Issuer Service of this instance: its {@code iss} must be a credential issuer DID of the own instance in
 * globalconf and its {@code kid} must start with {@code iss + "#"}.
 */
@RequiredArgsConstructor
public class IssuerStatusListAvailabilityRule implements TokenValidationRule {

    private static final String VC_CLAIM = "vc";
    private static final String CREDENTIAL_STATUS = "credentialStatus";
    private static final String TYPE = "type";
    private static final String ID = "@id";
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(5);

    private final GlobalConfProvider globalConf;
    private final LastGoodSource<BitstringStatusListCredential> source;

    @Override
    public Result<Void> checkRule(@Nonnull ClaimToken toVerify, @Nullable Map<String, Object> additional) {
        var issuer = toVerify.getStringClaim(JwtRegisteredClaimNames.ISSUER);
        var keyId = toVerify.getKeyId();
        if (issuer == null || keyId == null || !keyId.startsWith(issuer + "#")) {
            return Result.success();
        }
        var trusted = isCredentialIssuerOfOwnInstance(issuer);
        if (trusted.failed()) {
            return trusted.mapEmpty();
        }
        if (!trusted.getContent()) {
            return Result.success();
        }
        return statusListUrls(toVerify).map(this::lookUp).reduce(Result.success(), Result::merge);
    }

    private Result<Boolean> isCredentialIssuerOfOwnInstance(String issuer) {
        try {
            var issuers = globalConf.getCredentialIssuerDids(globalConf.getInstanceIdentifier());
            return Result.success(issuers.contains(issuer));
        } catch (RuntimeException e) {
            return Result.retryableFailure("Credential issuers are not readable from global configuration: " + e.getMessage());
        }
    }

    private Result<Void> lookUp(String statusListUrl) {
        return source.lookup(statusListUrl, EXPIRY_MARGIN).mapEmpty();
    }

    private static Stream<String> statusListUrls(ClaimToken token) {
        return Optional.ofNullable(token.getClaim(VC_CLAIM))
                .filter(Map.class::isInstance)
                .map(vc -> ((Map<?, ?>) vc).get(CREDENTIAL_STATUS))
                .stream()
                .flatMap(IssuerStatusListAvailabilityRule::asEntries)
                .filter(IssuerStatusListAvailabilityRule::isBitstringEntry)
                .map(IssuerStatusListAvailabilityRule::statusListUrl)
                .flatMap(Optional::stream);
    }

    private static Stream<Map<?, ?>> asEntries(Object credentialStatus) {
        if (credentialStatus instanceof Map<?, ?> single) {
            return Stream.of(single);
        }
        if (credentialStatus instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(entry -> (Map<?, ?>) entry);
        }
        return Stream.empty();
    }

    private static boolean isBitstringEntry(Map<?, ?> entry) {
        var type = entry.get(TYPE);
        return BitstringStatusListStatus.TYPE.equals(type)
                || type instanceof List<?> types && types.contains(BitstringStatusListStatus.TYPE);
    }

    private static Optional<String> statusListUrl(Map<?, ?> entry) {
        var url = Optional.ofNullable(entry.get(BitstringStatusListStatus.BITSTRING_STATUS_LIST_CREDENTIAL_LITERAL))
                .or(() -> Optional.ofNullable(entry.get(BitstringStatusListStatus.STATUS_LIST_CREDENTIAL)));
        return url.map(value -> value instanceof Map<?, ?> reference ? reference.get(ID) : value)
                .map(Object::toString);
    }
}
