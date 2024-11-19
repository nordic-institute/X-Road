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

package org.niis.xroad.confclient.globalconf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClient;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClientValidateActionExecutor;
import ee.ria.xroad.common.util.AtomicSave;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.confclient.config.ConfigurationClientProperties;
import org.niis.xroad.confclient.proto.AnchorServiceGrpc;
import org.niis.xroad.confclient.proto.ConfigurationAnchorMessage;
import org.niis.xroad.confclient.proto.VerificationResult;
import org.niis.xroad.rpc.common.Empty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.RETURN_SUCCESS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;

@RequiredArgsConstructor
@Slf4j
public class AnchorService extends AnchorServiceGrpc.AnchorServiceImplBase {
    private final ConfigurationClientProperties confClientProperties;
    private final ConfigurationClient configurationClient;
    private final ConfigurationClientValidateActionExecutor validateActionExecutor;
    private final GlobalConfRpcCache globalConfRpcCache;

    @Override
    public void verifyAndSaveConfigurationAnchor(ConfigurationAnchorMessage request, StreamObserver<VerificationResult> responseObserver) {
        try {
            responseObserver.onNext(verifyAndSaveConfigurationAnchor(request.getConfigurationAnchor().toByteArray()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private VerificationResult verifyAndSaveConfigurationAnchor(byte[] anchorBytes) throws Exception {
        File anchorTempFile = null;
        try {
            anchorTempFile = createTemporaryAnchorFile(anchorBytes);
            var configurationAnchor = new ConfigurationAnchor(anchorTempFile.getAbsolutePath());
            var paramsValidator = new ConfigurationClientValidateActionExecutor
                    .ParamsValidator(CONTENT_ID_PRIVATE_PARAMETERS, ERROR_CODE_MISSING_PRIVATE_PARAMS);
            var result = validateActionExecutor.validate(configurationAnchor, paramsValidator);
            if (result == RETURN_SUCCESS) {
                AtomicSave.moveBetweenFilesystems(anchorTempFile.getAbsolutePath(), confClientProperties.configurationAnchorFile());
                configurationClient.execute();
                globalConfRpcCache.refreshCache();
            }
            return VerificationResult.newBuilder()
                    .setReturnCode(result)
                    .build();
        } finally {
            FileUtils.deleteQuietly(anchorTempFile);
        }
    }

    @Override
    public void getConfigurationAnchor(Empty request, StreamObserver<ConfigurationAnchorMessage> responseObserver) {
        try {
            responseObserver.onNext(getConfigurationAnchorFromFile());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private ConfigurationAnchorMessage getConfigurationAnchorFromFile() throws Exception {
        Path anchorPath = Paths.get(confClientProperties.configurationAnchorFile());
        if (!Files.exists(anchorPath)) {
            log.error("Configuration anchor file {} does not exist.", anchorPath);
            throw new FileNotFoundException(anchorPath.toAbsolutePath().toString());
        }
        byte[] anchorBytes = Files.readAllBytes(anchorPath);
        return ConfigurationAnchorMessage.newBuilder()
                .setConfigurationAnchor(ByteString.copyFrom(anchorBytes))
                .build();
    }

    private File createTemporaryAnchorFile(byte[] anchorBytes) throws IOException {
        String tempFilesPath = SystemProperties.getTempFilesPath();
        try {
            String tempAnchorPrefix = "temp-internal-anchor-";
            String tempAnchorSuffix = ".xml";
            File tempDirectory = tempFilesPath != null ? new File(tempFilesPath) : null;
            File tempAnchor = File.createTempFile(tempAnchorPrefix, tempAnchorSuffix, tempDirectory);
            FileUtils.writeByteArrayToFile(tempAnchor, anchorBytes);
            return tempAnchor;
        } catch (Exception e) {
            log.error("Creating temporary anchor file failed", e);
            throw e;
        }
    }

}
