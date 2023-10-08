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
package ee.ria.xroad.common.conf.globalconfextension;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides access to global configuration extensions
 */
@Slf4j
public final class GlobalConfExtensions {

    // Instance per thread, same way as in GlobalGonf/GlobalConfImpl.
    // This way thread safety handling is same as in GlobalConf.
    private static final ThreadLocal<GlobalConfExtensions> INSTANCE = new ThreadLocal<GlobalConfExtensions>() {
        @Override
        protected GlobalConfExtensions initialValue() {
            return new GlobalConfExtensions();
        }
    };

    private OcspNextUpdate ocspNextUpdate;
    private OcspFetchInterval ocspFetchInterval;

    /**
     * @return instance
     */
    public static GlobalConfExtensions getInstance() {
        GlobalConfExtensions configuration = INSTANCE.get();
        return configuration;
    }

    /**
     * Use getInstance
     */
    private GlobalConfExtensions() throws RuntimeException {
    }

    /**
     * @return true if ocsp nextUpdate should be verified
     */
    public boolean shouldVerifyOcspNextUpdate() {
        OcspNextUpdate update = getOcspNextUpdate();
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
        OcspFetchInterval update = getFetchInterval();
        if (update != null) {
            log.trace("getOcspFetchInterval: {}", update.getOcspFetchInterval());
            return update.getOcspFetchInterval();
        } else {
            log.trace("update is null, returning default value {}", OcspFetchInterval.OCSP_FETCH_INTERVAL_DEFAULT);
            return OcspFetchInterval.OCSP_FETCH_INTERVAL_DEFAULT;
        }
    }

    private OcspNextUpdate getOcspNextUpdate() {
        try {
            if (ocspNextUpdate != null && ocspNextUpdate.hasChanged()) {
                log.trace("reload");
                ocspNextUpdate.reload();
            } else if (ocspNextUpdate == null) {
                Path ocspNextUpdatePath = getOcspNextUpdateConfigurationPath();

                if (Files.exists(ocspNextUpdatePath)) {
                    log.trace("Loading private parameters from {}",
                            ocspNextUpdatePath);
                    ocspNextUpdate = new OcspNextUpdate();
                    ocspNextUpdate.load(getOcspNextUpdateConfigurationPath().toString());
                    log.trace("Parameters were loaded, value: {}", ocspNextUpdate.shouldVerifyOcspNextUpdate());
                } else {
                    log.trace("Not loading ocsp next update from {}, "
                            + "file does not exist", ocspNextUpdatePath);
                }
            }
        } catch (Exception e) {
            log.error("Exception while fetching ocsp nextUpdate configuration", e);
        }
        return ocspNextUpdate;
    }

    private Path getOcspNextUpdateConfigurationPath() {
        return GlobalConf.getInstanceFile(OcspNextUpdate.FILE_NAME_OCSP_NEXT_UPDATE_PARAMETERS);
    }

    private OcspFetchInterval getFetchInterval() {
        try {
            if (ocspFetchInterval != null && ocspFetchInterval.hasChanged()) {
                log.trace("reload");
                ocspFetchInterval.reload();
            } else if (ocspFetchInterval == null) {
                Path ocspFetchIntervalPath = getOcspFetchIntervalConfigurationPath();

                if (Files.exists(ocspFetchIntervalPath)) {
                    log.trace("Loading private parameters from {}",
                                ocspFetchIntervalPath);
                    ocspFetchInterval = new OcspFetchInterval();
                    ocspFetchInterval.load(getOcspFetchIntervalConfigurationPath().toString());
                    log.trace("Parameters were loaded, value: {}", ocspFetchInterval.getOcspFetchInterval());
                } else {
                    log.trace("Not loading ocsp fetch interval from {}, "
                                + "file does not exist", ocspFetchIntervalPath);
                }
            }
        } catch (Exception e) {
            log.error("Exception while fetching ocsp fetch interval configuration", e);
        }
        return ocspFetchInterval;
    }

    private Path getOcspFetchIntervalConfigurationPath() {
        return GlobalConf.getInstanceFile(OcspFetchInterval.FILE_NAME_OCSP_FETCH_INTERVAL_PARAMETERS);
    }
}
