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

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link ManagementRequestSender} for this security server. Not guarded by method security,
 * so threads without an authentication, such as the scheduled jobs, can use it. Does not check the
 * global configuration validity; that is the caller's concern.
 */
@Component
@RequiredArgsConstructor
public class ManagementRequestSenderFactory {
    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final GlobalConfProvider globalConfProvider;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final VaultKeyProvider vaultKeyProvider;
    private final AdminServiceProperties adminServiceProperties;

    /**
     * Creates the sender for this security server's owner.
     */
    public ManagementRequestSender create() {
        return createFor(currentSecurityServerId.getServerId().getOwner());
    }

    /**
     * Creates the sender for the given sender identity.
     */
    public ManagementRequestSender createFor(ClientId sender) {
        ClientId receiver = globalConfProvider.getManagementRequestService();
        return new ManagementRequestSender(vaultKeyProvider, globalConfProvider, signerRpcClient,
                signerSignClient, sender, receiver, adminServiceProperties.getProxyServerUrl(),
                DigestAlgorithm.ofName(adminServiceProperties.getAuthCertRegSignatureDigestAlgorithmId()),
                adminServiceProperties.getProxyServerConnectTimeout(),
                adminServiceProperties.getProxyServerSocketTimeout(),
                adminServiceProperties.isProxyServerEnableConnectionReuse());
    }
}
