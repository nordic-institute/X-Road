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
package org.niis.xroad.confclient.core.globalconf;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.core.ConfigurationClient;
import org.niis.xroad.confclient.core.ConfigurationClientActionExecutor;
import org.niis.xroad.confclient.proto.AnchorServiceGrpc;
import org.niis.xroad.confclient.proto.ConfigurationAnchorMessage;
import org.niis.xroad.confclient.proto.VerificationResult;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.rpc.common.Empty;

import java.io.FileNotFoundException;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.RETURN_SUCCESS;
import static org.niis.xroad.globalconf.model.ConfigurationConstants.CONTENT_ID_PRIVATE_PARAMETERS;

@RequiredArgsConstructor
@Slf4j
@ApplicationScoped
public class AnchorService extends AnchorServiceGrpc.AnchorServiceImplBase {
    private final ConfigurationClient configurationClient;
    private final ConfigurationClientActionExecutor configurationClientActionExecutor;
    private final GlobalConfRpcCache globalConfRpcCache;
    private final ConfigurationAnchorProvider configurationAnchorProvider;

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
        var configurationAnchor = new ConfigurationAnchor(anchorBytes);
        var paramsValidator = new ConfigurationClientActionExecutor
                .ParamsValidator(CONTENT_ID_PRIVATE_PARAMETERS, ERROR_CODE_MISSING_PRIVATE_PARAMS);
        var result = configurationClientActionExecutor.validate(configurationAnchor, paramsValidator);
        if (result == RETURN_SUCCESS) {
            configurationAnchorProvider.save(anchorBytes);

            configurationClient.execute();
            globalConfRpcCache.refreshCache();
        }
        return VerificationResult.newBuilder()
                .setReturnCode(result)
                .build();
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
        return configurationAnchorProvider.get()
                .map(bytes -> ConfigurationAnchorMessage.newBuilder()
                        .setConfigurationAnchor(ByteString.copyFrom(bytes))
                        .build())
                .orElseThrow(() -> new FileNotFoundException("Configuration anchor does not exist."));
    }

}
