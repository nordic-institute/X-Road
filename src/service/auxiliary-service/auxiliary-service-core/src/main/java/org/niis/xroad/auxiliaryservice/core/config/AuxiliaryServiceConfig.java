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

package org.niis.xroad.auxiliaryservice.core.config;

import ee.ria.xroad.common.util.BackupMetadataHandler;
import ee.ria.xroad.common.util.process.BlockingProcessRunner;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.auxiliaryservice.proto.AuxiliaryServiceRpcChannelProperties;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.XRoadRpcProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;

import java.nio.file.Path;

public class AuxiliaryServiceConfig {

    private static final String EXPECTED_BACKUP_SERVER_TYPE = "security";

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(AuxiliaryServiceConfigKeys.instance())
                .register(HealthCheckConfigKeys.instance())
                .deploymentMode(deploymentMode())
                .dbOverrides(appName)
                .build();
    }

    @ApplicationScoped
    RpcProperties rpcProperties(XRoadConfig xRoadConfig) {
        return new XRoadRpcProperties(xRoadConfig);
    }

    @ApplicationScoped
    AuxiliaryServiceRpcChannelProperties auxiliaryServiceRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new AuxiliaryServiceRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfClientRpcChannelProperties confClientRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new ConfClientRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    BackupProperties backupProperties(XRoadConfig xRoadConfig) {
        return new BackupProperties(xRoadConfig);
    }

    @ApplicationScoped
    MessageLogJobsProperties messageLogJobsProperties(XRoadConfig xRoadConfig) {
        return new MessageLogJobsProperties(xRoadConfig);
    }

    @ApplicationScoped
    ExternalProcessRunner externalProcessRunner() {
        return new ExternalProcessRunner();
    }

    @ApplicationScoped
    @Typed(BlockingProcessRunner.class)
    BlockingProcessRunner blockingProcessRunner() {
        return new BlockingProcessRunner();
    }

    private static DeploymentMode deploymentMode() {
        var profiles = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles();
        return profiles.contains("containerized") ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
    }

    @ApplicationScoped
    BackupMetadataHandler backupMetadataHandler(ExternalProcessRunner externalProcessRunner, BackupProperties backupProperties) {
        return new BackupMetadataHandler(
                externalProcessRunner,
                backupProperties.backupFormatVersionFilePath(),
                backupProperties.createBackupMetadataPath(),
                Path.of(backupProperties.location()),
                EXPECTED_BACKUP_SERVER_TYPE);
    }
}
