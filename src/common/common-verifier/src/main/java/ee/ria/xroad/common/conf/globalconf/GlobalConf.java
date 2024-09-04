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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Global configuration.
 */
@Slf4j
@Deprecated(forRemoval = true)
public final class GlobalConf {

    private static GlobalConfProvider instance;

    private GlobalConf() {
    }

    public static synchronized void initialize(GlobalConfProvider globalConfProvider) {
        //TODO this will be removed.
//        if (instance == null) {
            instance = globalConfProvider;
//        } else {
//            log.warn("GlobalConf is already initialized");
//        }
    }

    /**
     * Returns the singleton instance of the configuration.
     */
    private static GlobalConfProvider getInstance() {
        if (instance == null) {
            throw new IllegalStateException("GlobalConf is not initialized");
        }

        return instance;
    }

    // ------------------------------------------------------------------------

    /**
     * Returns an absolute file name for the current instance.
     *
     * @param fileName the file name
     * @return the absolute path to the file of the current instance
     */
    public static Path getInstanceFile(String fileName) {
        return getFile(getInstanceIdentifier(), fileName);
    }

    /**
     * Returns an absolute file name for the specified instance.
     *
     * @param instanceIdentifier the instance identifier
     * @param fileName           the file name
     * @return the absolute path to the file of the specified instance
     */
    public static Path getFile(String instanceIdentifier, String fileName) {
        return Paths.get(SystemProperties.getConfigurationPath(),
                instanceIdentifier, fileName);
    }

    // ------------------------------------------------------------------------


    /**
     * @return the instance identifier for this configuration source
     */
    public static String getInstanceIdentifier() {
        log.trace("getInstanceIdentifier()");

        return getInstance().getInstanceIdentifier();
    }


}
