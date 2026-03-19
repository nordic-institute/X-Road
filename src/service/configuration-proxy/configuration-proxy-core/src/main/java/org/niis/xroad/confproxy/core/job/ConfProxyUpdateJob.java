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
package org.niis.xroad.confproxy.core.job;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.service.ConfProxyInstanceService;
import org.niis.xroad.confproxy.common.service.InstanceRefresher;

import java.util.List;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@Slf4j
@ApplicationScoped
public class ConfProxyUpdateJob {

    private final ConfProxyInstanceService confProxyInstanceService;
    private final InstanceRefresher instanceRefresher;

    public ConfProxyUpdateJob(ConfProxyInstanceService confProxyInstanceService,
                              InstanceRefresher instanceRefresher,
                              ConfigurationProxyProperties configurationProxyProperties) {
        log.info("Creating configuration proxy update job with update interval: {}", configurationProxyProperties.updateInterval());
        this.confProxyInstanceService = confProxyInstanceService;
        this.instanceRefresher = instanceRefresher;
    }


    @Scheduled(every = "${xroad.configuration-proxy.update-interval}", concurrentExecution = SKIP)
    protected void update() {
        List<String> instancesNames;

        try {
            instancesNames = confProxyInstanceService.availableInstancesNames();
            log.debug("Instances from available instances: {}", instancesNames);
        } catch (Exception e) {
            log.error("Error while trying to get configuration proxy instances", e);
            return;
        }

        log.info("Starting update execution for instances: {}", instancesNames);
        for (var instanceName : instancesNames) {
            try {
                var proxyInstance = confProxyInstanceService.loadInstance(instanceName);
                log.info("Updating '{}' instance...", instanceName);
                instanceRefresher.refresh(proxyInstance);
            } catch (Exception ex) {
                log.error("Error while updating '{}' instance", instanceName, ex);
            }
        }
    }

}

