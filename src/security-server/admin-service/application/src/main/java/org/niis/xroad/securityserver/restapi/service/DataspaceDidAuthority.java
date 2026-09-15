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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * The authority (host:port) embedded in derived participant DIDs, and the identity-hub host it is
 * built from. Single switch point for both the provisioning reader and the binding writer, so a
 * bound DID and a freshly derived one can never disagree because of two derivations.
 *
 * <p>Interim source: the identity-hub host plus its DID-serving port, because that is where DID
 * documents are actually served. Target source, once registered-address DID serving exists: the
 * GlobalConf-registered security server address ({@code GlobalConfProvider#getSecurityServerAddress}),
 * with no port.
 *
 * <p>The port must match the identity hub's own {@code web.http.did.port}. It is part of every DID
 * bound in {@code ds_participant}, so changing it after a member's identity has been bound makes
 * that row fail verification.
 */
@Component
@RequiredArgsConstructor
public class DataspaceDidAuthority {

    private final AdminServiceProperties adminServiceProperties;

    /**
     * @return the authority as {@code host:port}, as embedded in derived DIDs
     */
    public String current() {
        return identityHubHost() + ":" + adminServiceProperties.getDataspace().getIdentityHubDidPort();
    }

    /**
     * @return the identity hub's host, parsed from the configured identity-hub URL
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
}
