/*
 * The MIT License
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
package org.niis.xroad.opmonitor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.credentials.ChannelTrust;
import org.niis.xroad.common.vault.VaultClient;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.OP_MONITOR_TRUST_FAILURE;

/**
 * Chooses whom the op-monitor channels trust: with RPC TLS on, only the imported op-monitor CA when one exists; the
 * shared trust otherwise. With RPC TLS off the imported CA is not read.
 */
@Slf4j
@RequiredArgsConstructor
public class OpMonitorChannelTrustResolver {
    private static final String TRUST_FAILURE_DETAILS = "The op-monitor channel trust could not be established";

    private final RpcProperties rpcProperties;
    private final VaultClient vaultClient;

    /**
     * @return the trust for a new op-monitor channel
     * @throws XrdRuntimeException when the imported op-monitor CA cannot be read or used; no trust is returned
     */
    public ChannelTrust resolve() {
        if (!rpcProperties.useTls()) {
            return ChannelTrust.shared();
        }
        final Optional<X509Certificate> opMonitorCa;
        try {
            opMonitorCa = vaultClient.getOpMonitorCa();
        } catch (Exception e) {
            throw trustFailure(e);
        }
        if (opMonitorCa.isEmpty()) {
            return ChannelTrust.shared();
        }
        log.info("Trusting only the imported op-monitor CA on the op-monitor channel");
        return ChannelTrust.onlyCa(opMonitorCa.get());
    }

    static XrdRuntimeException trustFailure(Exception cause) {
        return XrdRuntimeException.systemException(OP_MONITOR_TRUST_FAILURE)
                .cause(cause)
                .details(TRUST_FAILURE_DETAILS)
                .build();
    }
}
