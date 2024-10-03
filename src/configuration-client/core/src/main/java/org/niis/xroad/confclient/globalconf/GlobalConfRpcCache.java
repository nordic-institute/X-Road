/*
 * The MIT License
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
package org.niis.xroad.confclient.globalconf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.FSGlobalConfValidator;
import ee.ria.xroad.common.conf.globalconf.GlobalConfInitState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.springframework.beans.factory.InitializingBean;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class GlobalConfRpcCache implements InitializingBean {
    private final FSGlobalConfValidator fsGlobalConfValidator;
    private final GetGlobalConfRespFactory getGlobalConfRespFactory;

    private GetGlobalConfResp cachedGlobalConf = null;

    @Override
    public void afterPropertiesSet() {
        refreshCache();
    }

    public Optional<GetGlobalConfResp> getGlobalConf() {
        return Optional.ofNullable(cachedGlobalConf);
    }

    public void refreshCache() {
        try {
            loadGlobalConf();
        } catch (Exception e) {
            log.error("Failed to initialize cache.", e);
        }
    }

    private synchronized void loadGlobalConf() {
        if (fsGlobalConfValidator.getReadinessState(SystemProperties.getConfigurationPath()) != GlobalConfInitState.READY_TO_INIT) {
            log.warn("GlobalConf is not ready to be initialized. Skipping cache refresh.");
            return;
        }

        var stopWatch = StopWatch.createStarted();
        try {
            log.trace("Refreshing cache");
            cachedGlobalConf = getGlobalConfRespFactory.createGlobalConfResp();
        } finally {
            log.trace("Cache refreshed in {} ms", stopWatch.getTime(TimeUnit.MILLISECONDS));
        }
    }


}
