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
package org.niis.xroad.confclient.core.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Provider;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.confclient.core.ConfigurationClient;
import org.niis.xroad.confclient.core.ConfigurationDownloader;
import org.niis.xroad.confclient.core.GlobalConfSourceLocationRepository;
import org.niis.xroad.confclient.core.GlobalConfSourceLocationRepositoryImpl;
import org.niis.xroad.confclient.core.GlobalConfSourceLocationRepositoryNoopImpl;
import org.niis.xroad.confclient.core.HttpUrlConnectionConfigurer;
import org.niis.xroad.confclient.core.globalconf.ConfigurationAnchorProvider;
import org.niis.xroad.confclient.core.globalconf.DBBasedProvider;
import org.niis.xroad.confclient.core.globalconf.FileBasedProvider;
import org.niis.xroad.globalconf.util.FSGlobalConfValidator;

import javax.sql.DataSource;

public class ConfClientRootConfig {

    @ApplicationScoped
    ConfigurationAnchorProvider configurationAnchorProvider(ConfigurationClientProperties configurationClientProperties,
                                                            CommonProperties commonProperties,
                                                            Provider<DataSource> dataSource) {
        return switch (configurationClientProperties.configurationAnchorStorage()) {
            case FILE -> new FileBasedProvider(configurationClientProperties.configurationAnchorFile(),
                    commonProperties.tempFilesPath());
            case DB -> new DBBasedProvider(dataSource.get());
        };
    }

    @ApplicationScoped
    GlobalConfSourceLocationRepository globalConfSourceLocationRepository(Provider<DataSource> dataSource) {
        if (dataSource.get() != null) {
            return new GlobalConfSourceLocationRepositoryImpl(dataSource.get());
        }
        return new GlobalConfSourceLocationRepositoryNoopImpl();
    }

    @ApplicationScoped
    ConfigurationClient configurationClient(ConfigurationClientProperties configurationClientProperties,
                                            ConfigurationAnchorProvider configurationAnchorProvider,
                                            HttpUrlConnectionConfigurer connectionConfigurer,
                                            GlobalConfSourceLocationRepository globalConfSourceLocationRepository) {
        var downloader = new ConfigurationDownloader(connectionConfigurer, globalConfSourceLocationRepository,
                configurationClientProperties.globalConfDir());
        return new ConfigurationClient(
                configurationAnchorProvider,
                configurationClientProperties.globalConfDir(), downloader, configurationClientProperties.allowedFederations());
    }

    @ApplicationScoped
    FSGlobalConfValidator fsGlobalConfValidator() {
        return new FSGlobalConfValidator();
    }

}
