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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import io.grpc.ManagedChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.common.healthcheck.GrpcChannelReadinessCheck;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;

/**
 * Readiness check for the gRPC channel to the Configuration-Client service.
 * Verifies that the channel is in an acceptable connectivity state.
 */
@Readiness
@ApplicationScoped
public class ConfClientChannelReadinessCheck extends GrpcChannelReadinessCheck {

    @Inject
    ConfClientRpcClient confClientRpcClient;

    @Override
    protected ManagedChannel getChannel() {
        return confClientRpcClient.getChannel();
    }

    @Override
    protected String getCheckName() {
        return "PROXY_CONFCLIENT_CHANNEL_READINESS_CHECK";
    }

    @Override
    protected String getTargetService() {
        return "configuration-client";
    }
}