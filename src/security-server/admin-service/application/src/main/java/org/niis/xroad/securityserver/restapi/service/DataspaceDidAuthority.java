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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;

import com.apicatalog.did.Did;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DidAuthority;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Minted;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Reason;
import org.niis.xroad.securityserver.restapi.service.DidMintResult.Rejected;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The authority (host:port) embedded in this Security Server's participant DIDs, and the DIDs
 * derived for it. Single switch point for the provisioning reader and the binding writer, so a
 * bound DID and a freshly derived one can never disagree because of two derivations.
 *
 * <p>Source: the GlobalConf-registered security server address at the stored DID port
 * ({@code xroad.dsp.did-port}), the port the identity hub listens on for DID documents. The authority
 * is part of every DID minted for {@code ds_participant}; a bound DID is stored and used as is, so a later
 * change of the registered address or the port never alters it.
 *
 * <p>Unknown until the server's owner is initialized, GlobalConf has been downloaded and the
 * server's registration has landed in it — normal states before and during registration, reported
 * by {@link #isKnown()} rather than thrown.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataspaceDidAuthority {

    private final OwnSecurityServerResolver ownSecurityServerResolver;
    private final AdminServiceProperties adminServiceProperties;

    /**
     * @return whether the registered address the authority derives from is resolvable yet
     */
    public boolean isKnown() {
        return registeredAddress().isPresent();
    }

    /**
     * Mints the member's DID for the current authority.
     *
     * @param member the X-Road member identifier
     * @return the minted DID, or the rejection when the registered address is not ASCII or not a valid host
     *         name or IPv6 literal, or the DID would be too long
     * @throws XrdRuntimeException with {@code DSP_PROVISIONING_FAILED} while the registered address is unknown
     * @throws XrdRuntimeException with {@code VALIDATION_ERROR} if the member identifier cannot be encoded
     */
    public DidMintResult mintMemberDid(ClientId member) {
        return mint(authority -> ParticipantIdentifierScheme.memberDid(member, authority));
    }

    /**
     * Mints the per-server SYSTEM DID for the current authority.
     *
     * @return the minted DID, or the rejection when the registered address is not ASCII or not a valid host
     *         name or IPv6 literal, or the DID would be too long
     * @throws XrdRuntimeException with {@code DSP_PROVISIONING_FAILED} while the registered address is unknown
     */
    public DidMintResult mintSystemDid() {
        return mint(ParticipantIdentifierScheme::systemDid);
    }

    /**
     * @return the identity hub's host, parsed from the configured identity-hub URL; the internal
     *         reach-the-hub address for credential-service and STS URLs, not part of any DID
     * @throws XrdRuntimeException with {@code VALIDATION_ERROR} if the URL has no resolvable host
     */
    public String identityHubHost() {
        var url = adminServiceProperties.getDataspace().getIdentityHubUrl();
        var host = URI.create(url).getHost();
        if (host == null || host.isBlank()) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "dataspace identity-hub URL '%s' has no resolvable host", url);
        }
        return host;
    }

    /**
     * @return the DSP base URL of this server's control plane, built from the registered address and the
     *         stored DSP port ({@code xroad.dsp.dsp-port}); empty while the registered address is unknown
     * @throws XrdRuntimeException with {@code VALIDATION_ERROR} if the registered address is not ASCII or not a
     *         valid host name or IPv6 literal
     */
    public Optional<String> dspBaseUrl() {
        var dspPort = adminServiceProperties.getDataspace().getDspPort();
        return resolve().map(address -> switch (address) {
            case RegisteredAddress.Valid(var host, var _) -> DspConventions.dspBaseUrl(host, dspPort);
            case RegisteredAddress.Invalid(var rejection) -> throw rejection.toException();
        });
    }

    /**
     * @return the DSP base URL like {@link #dspBaseUrl()}, but empty also while the registered address is invalid,
     *         for callers that treat an address that cannot be used the same as an unknown one
     */
    public Optional<String> dspBaseUrlIfValid() {
        try {
            return dspBaseUrl();
        } catch (XrdRuntimeException e) {
            log.debug("No DSP base URL can be built from the registered address: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private DidMintResult mint(Function<String, Did> derive) {
        return switch (resolve().orElseThrow(this::addressUnknown)) {
            case RegisteredAddress.Invalid(var rejection) -> rejection;
            case RegisteredAddress.Valid(var host, var authority) -> {
                var did = derive.apply(authority);
                var length = did.toString().length();
                yield length > ParticipantIdentifierScheme.MAX_DID_LENGTH
                        ? new Rejected(Reason.DID_TOO_LONG, "DID of %d characters for host '%s' is longer than the limit of %d"
                                .formatted(length, host, ParticipantIdentifierScheme.MAX_DID_LENGTH))
                        : new Minted(did);
            }
        };
    }

    private XrdRuntimeException addressUnknown() {
        return XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED,
                "this security server's owner or GlobalConf-registered address is not available yet; "
                        + "cannot derive participant DIDs");
    }

    private Optional<RegisteredAddress> resolve() {
        return registeredAddress().map(this::validate);
    }

    private RegisteredAddress validate(String address) {
        if (!DidAuthority.isAscii(address)) {
            return new RegisteredAddress.Invalid(new Rejected(Reason.NON_ASCII_HOST,
                    "address '%s' must contain only ASCII characters".formatted(address)));
        }
        try {
            var authority = DspConventions.didAuthority(address, adminServiceProperties.getDataspace().getDidPort());
            return new RegisteredAddress.Valid(address, authority);
        } catch (XrdRuntimeException e) {
            return new RegisteredAddress.Invalid(new Rejected(Reason.INVALID_HOST, e.getDetails(), e));
        }
    }

    private Optional<String> registeredAddress() {
        return ownSecurityServerResolver.address().registeredAddress();
    }

    private sealed interface RegisteredAddress {

        /** An address that carries a DID: the host as registered and the DID authority derived from it. */
        record Valid(String host, String authority) implements RegisteredAddress {
        }

        /** An address that cannot carry a DID. */
        record Invalid(Rejected rejection) implements RegisteredAddress {
        }
    }
}
