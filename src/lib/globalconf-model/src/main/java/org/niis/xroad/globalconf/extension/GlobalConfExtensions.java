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

package org.niis.xroad.globalconf.extension;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.monitoringconf.MonitoringParameters;
import org.niis.xroad.globalconf.schema.monitoringparameters.MonitoringClientType;

import java.util.Optional;
import java.util.function.Supplier;

import static org.niis.xroad.globalconf.extension.OcspFetchInterval.FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS;
import static org.niis.xroad.globalconf.extension.OcspNextUpdate.FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS;
import static org.niis.xroad.globalconf.monitoringconf.MonitoringParameters.FILE_NAME_MONITORING_PARAMETERS;

/**
 * Provides access to global configuration extensions
 */
@Slf4j
@RequiredArgsConstructor
public final class GlobalConfExtensions {

    private final Supplier<OcspNextUpdate> ocspNextUpdateLoader;
    private final Supplier<OcspFetchInterval> ocsFetchIntervalLoader;
    private final Supplier<MonitoringParameters> monitoringParametersLoader;

    public GlobalConfExtensions(GlobalConfSource globalConfSource, GlobalConfExtensionFactory factory) {
        this.ocspNextUpdateLoader = () -> factory.createExtension(
                globalConfSource, FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS, OcspNextUpdate.class);
        this.ocsFetchIntervalLoader = () -> factory.createExtension(
                globalConfSource, FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS, OcspFetchInterval.class);
        this.monitoringParametersLoader = () -> factory.createExtension(
                globalConfSource, FILE_NAME_MONITORING_PARAMETERS, MonitoringParameters.class);
    }

    /**
     * @return true if ocsp nextUpdate should be verified
     */
    public boolean shouldVerifyOcspNextUpdate() {
        OcspNextUpdate update = ocspNextUpdateLoader.get();
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
        OcspFetchInterval update = ocsFetchIntervalLoader.get();
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
        return Optional.ofNullable(monitoringParametersLoader.get())
                .map(MonitoringParameters::getMonitoringClient)
                .map(MonitoringClientType::getMonitoringClientId)
                .orElse(null);
    }
}
