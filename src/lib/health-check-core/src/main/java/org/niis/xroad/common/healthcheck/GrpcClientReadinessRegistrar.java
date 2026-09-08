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
package org.niis.xroad.common.healthcheck;

import io.grpc.ManagedChannel;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.health.api.HealthRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;

/**
 * Discovers all {@link AbstractRpcClient} beans that opt in to health checking
 * (by overriding {@link AbstractRpcClient#getHealthCheckName()}) and registers
 * a {@link GrpcChannelReadinessCheck} for each via SmallRye's {@link HealthRegistry}.
 *
 * <p>This eliminates the need to create a dedicated {@code *ChannelReadinessCheck}
 * class for each RPC client. To enable the check, simply override
 * {@link AbstractRpcClient#getHealthCheckName()} and
 * {@link AbstractRpcClient#getHealthCheckTargetService()} in the client class.
 */
@ApplicationScoped
public class GrpcClientReadinessRegistrar {

    @Inject
    @Readiness
    HealthRegistry readinessRegistry;

    @Inject
    @Any
    Instance<AbstractRpcClient> rpcClients;

    void onStart(@Observes StartupEvent ev) {
        rpcClients.stream()
                .filter(client -> client.getHealthCheckName() != null)
                .forEach(client -> {
                    String name = client.getHealthCheckName();
                    readinessRegistry.register(name, new GrpcChannelReadinessCheck() {
                        @Override
                        protected ManagedChannel getChannel() {
                            return client.getChannel();
                        }

                        @Override
                        protected String getCheckName() {
                            return name;
                        }

                        @Override
                        protected String getTargetService() {
                            return client.getHealthCheckTargetService();
                        }
                    });
                });
    }
}
