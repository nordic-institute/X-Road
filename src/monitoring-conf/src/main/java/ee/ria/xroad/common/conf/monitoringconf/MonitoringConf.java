/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.common.conf.monitoringconf;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global configuration for monitoring addon.
 */
@Slf4j
public final class MonitoringConf {

    // Instance per thread, same way as in GlobalGonf/GlobalConfImpl.
    // This way thread safety handling is same as in GlobalConf.
    // Instance methods (getMonitoringClient, getMonitoringParameters) however are not synchronized
    // (in comparison to GlobalGonf/GlobalConfImpl/ConfigurationDirectoryV2)
    // since there is no need to (INSTANCE per thread).
    private static final ThreadLocal<MonitoringConf> INSTANCE = new ThreadLocal<MonitoringConf>() {
        @Override
        protected MonitoringConf initialValue() {
            return new MonitoringConf();
        }
    };

    private MonitoringParameters monitoringParameters;

    public static MonitoringConf getInstance() {
        MonitoringConf configuration = INSTANCE.get();
        return configuration;
    }

    /**
     * @return ClientId of the monitoring client - which is allowed
     * to request monitoring data from other security servers
     */
    public ClientId getMonitoringClient() {
        return getMonitoringParameters() == null ? null
                : (getMonitoringParameters().getMonitoringClient() == null
                ? null : getMonitoringParameters().getMonitoringClient()
                .getMonitoringClientId());
    }

    /**
     * Use getInstance
     */
    private MonitoringConf() throws RuntimeException {
        MonitoringParameters p = getMonitoringParameters(); // load MonitoringParameters for the first time
    }

    /**
     * Returns MonitoringParameters. Reads parameter file as needed. Refreshes previously loaded
     * configuration if it has changed. Returns null if parameter file does not exist.
     *
     */
    private MonitoringParameters getMonitoringParameters() {

        try {
            if (monitoringParameters != null && monitoringParameters.hasChanged()) {
                monitoringParameters.reload();
            } else if (monitoringParameters == null) {
                Path monitoringParametersPath = getMonitoringConfigurationPath();

                if (Files.exists(monitoringParametersPath)) {
                    log.trace("Loading private parameters from {}",
                            monitoringParametersPath);
                    monitoringParameters = new MonitoringParameters();
                    monitoringParameters.load(getMonitoringConfigurationPath().toString());
                } else {
                    log.trace("Not loading monitoring parameters from {}, "
                            + "file does not exist", monitoringParametersPath);
                }
            }
        } catch (Exception e) {
            log.error("Exception while fetching monitoring configuration", e);
            throw new RuntimeException(e);
        }

        return monitoringParameters;
    }

    private Path getMonitoringConfigurationPath() {
        return GlobalConf.getInstanceFile(MonitoringParameters.FILE_NAME_MONITORING_PARAMETERS);
    }

}
