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

package ee.ria.xroad.common.conf.globalconfextension;

import ee.ria.xroad.common.conf.globalconf.GlobalConfSource;
import ee.ria.xroad.common.conf.globalconf.monitoringparameters.MonitoringClientType;
import ee.ria.xroad.common.conf.monitoringconf.MonitoringParameters;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static ee.ria.xroad.common.conf.globalconfextension.OcspFetchInterval.FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconfextension.OcspNextUpdate.FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS;
import static ee.ria.xroad.common.conf.monitoringconf.MonitoringParameters.FILE_NAME_MONITORING_PARAMETERS;

/**
 * Provides access to global configuration extensions
 */
@Slf4j
@RequiredArgsConstructor
public final class GlobalConfExtensions {

    private final GlobalConfExtensionLoader<OcspNextUpdate> ocspNextUpdateLoader;
    private final GlobalConfExtensionLoader<OcspFetchInterval> ocsFetchIntervalLoader;
    private final GlobalConfExtensionLoader<MonitoringParameters> monitoringParametersLoader;

    public GlobalConfExtensions(GlobalConfSource globalConfSource) {
        this.ocspNextUpdateLoader = new GlobalConfExtensionLoader<>(
                globalConfSource, FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS, OcspNextUpdate.class);
        this.ocsFetchIntervalLoader = new GlobalConfExtensionLoader<>(
                globalConfSource, FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS, OcspFetchInterval.class);
        this.monitoringParametersLoader = new GlobalConfExtensionLoader<>(
                globalConfSource, FILE_NAME_MONITORING_PARAMETERS, MonitoringParameters.class);
    }

    /**
     * @return true if ocsp nextUpdate should be verified
     */
    public boolean shouldVerifyOcspNextUpdate() {
        OcspNextUpdate update = ocspNextUpdateLoader.getExtension();
        if (update != null) {
            log.trace("shouldVerifyOcspNextUpdate: {}", update.shouldVerifyOcspNextUpdate());
            return update.shouldVerifyOcspNextUpdate();
        } else {
            log.trace("update is null returning default value {}", OcspNextUpdate.OCSP_NEXT_UPDATE_DEFAULT);
            return OcspNextUpdate.OCSP_NEXT_UPDATE_DEFAULT;
        }
    }

    /**
     * @return OCSP fetch interval in seconds
     */
    public int getOcspFetchInterval() {
        OcspFetchInterval update = ocsFetchIntervalLoader.getExtension();
        if (update != null) {
            log.trace("getOcspFetchInterval: {}", update.getOcspFetchInterval());
            return update.getOcspFetchInterval();
        } else {
            log.trace("update is null, returning default value {}", OcspFetchInterval.OCSP_FETCH_INTERVAL_DEFAULT);
            return OcspFetchInterval.OCSP_FETCH_INTERVAL_DEFAULT;
        }
    }

    /**
     * @return ClientId of the monitoring client - which is allowed
     * to request monitoring data from other security servers
     */
    public ClientId getMonitoringClient() {
        return Optional.ofNullable(monitoringParametersLoader.getExtension())
                .map(MonitoringParameters::getMonitoringClient)
                .map(MonitoringClientType::getMonitoringClientId)
                .orElse(null);
    }
}
