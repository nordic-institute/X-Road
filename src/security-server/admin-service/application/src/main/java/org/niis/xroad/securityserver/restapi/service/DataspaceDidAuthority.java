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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The authority (host:port) embedded in this Security Server's participant DIDs, and the DIDs
 * derived for it. Single switch point for the provisioning reader and the binding writer, so a
 * bound DID and a freshly derived one can never disagree because of two derivations.
 *
 * <p>Source: the GlobalConf-registered security server address at the configured identity hub DID
 * port — the same coordinates counter-parties derive from their own GlobalConf copy
 * ({@link DspConventions#didAuthority(String)}), so the identity hub must serve DID documents on
 * that authority and the configured port only agrees with peers while it is
 * {@link DspConventions#DID_PORT}. The authority is part of every DID bound in
 * {@code ds_participant}, so changing the registered address or the port after a member's identity
 * has been bound makes that row fail verification.
 *
 * <p>Unknown until the server's owner is initialized, GlobalConf has been downloaded and the
 * server's registration has landed in it — normal states before and during registration, reported
 * by {@link #isKnown()} rather than thrown.
 */
@Component
@RequiredArgsConstructor
public class DataspaceDidAuthority {

    private final OwnSecurityServerResolver ownSecurityServerResolver;
    private final AdminServiceProperties adminServiceProperties;

    /**
     * @return whether the registered address the authority derives from is resolvable yet
     */
    public boolean isKnown() {
        return find().isPresent();
    }

    /**
     * @return the authority as {@code host:port}, as embedded in derived DIDs
     * @throws XrdRuntimeException with {@code DSP_PROVISIONING_FAILED} while the registered address is unknown
     */
    public String current() {
        return find().orElseThrow(() -> XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED,
                "this security server's owner or GlobalConf-registered address is not available yet; "
                        + "cannot derive participant DIDs"));
    }

    /**
     * @return the HOST participant context's DID for the current authority
     */
    public Did hostDid() {
        return ParticipantIdentifierScheme.hostDid(current());
    }

    /**
     * @return the MANAGEMENT participant context's DID for the current authority
     */
    public Did managementDid() {
        return ParticipantIdentifierScheme.managementDid(current());
    }

    /**
     * @param member the X-Road member identifier
     * @return the member's freshly derived DID for the current authority
     * @throws XrdRuntimeException with {@code VALIDATION_ERROR} if the member identifier cannot be encoded
     */
    public Did memberDid(ClientId member) {
        return ParticipantIdentifierScheme.memberDid(member, current());
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

    private Optional<String> find() {
        var didPort = adminServiceProperties.getDataspace().getIdentityHubDidPort();
        return ownSecurityServerResolver.registeredAddress().map(address -> DspConventions.didAuthority(address, didPort));
    }
}
