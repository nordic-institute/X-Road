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
package ee.ria.xroad.common.conf.globalconf;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GlobalConfInstance;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.ConfigurationConstants.FILE_NAME_SHARED_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.GlobalConfInitState.FAILURE_INSTANCE_IDENTIFIER_DATA_MISSING;

@Slf4j
public class RemoteGlobalConfDataLoader {

     RemoteGlobalConfSource.GlobalConfData load(GetGlobalConfResp getGlobalConfResp,
                                                       Map<String, PrivateParametersProvider> basePrivateParams,
                                                       Map<String, SharedParametersProvider> baseSharedParams) {

        var defaultGlobalConfVersion = getGlobalConfResp.getInstancesList().stream()
                .filter(i -> i.getInstanceIdentifier().equals(getGlobalConfResp.getInstanceIdentifier()))
                .findFirst().map(GlobalConfInstance::getVersion)
                .orElseThrow(() -> new GlobalConfInitException(FAILURE_INSTANCE_IDENTIFIER_DATA_MISSING));

        return new RemoteGlobalConfSource.GlobalConfData(getGlobalConfResp.getDateRefreshed(),
                getGlobalConfResp.getInstanceIdentifier(),
                defaultGlobalConfVersion,
                loadPrivateParameters(getGlobalConfResp, basePrivateParams),
                loadSharedParameters(getGlobalConfResp, baseSharedParams),
                new ConcurrentHashMap<>());

    }

    private Map<String, PrivateParametersProvider> loadPrivateParameters(GetGlobalConfResp getGlobalConfResp,
                                                                         Map<String, PrivateParametersProvider> basePrivateParams) {
        log.trace("Loading PrivateParameters..");

        Map<String, PrivateParametersProvider> privateParams = new HashMap<>(basePrivateParams);
        for (GlobalConfInstance confInstance : getGlobalConfResp.getInstancesList()) {
            log.trace("LoadingPrivateParameters from {}", confInstance);
            Optional<PrivateParametersProvider> parametersProvider = loadParameters(confInstance, FILE_NAME_PRIVATE_PARAMETERS);
            parametersProvider.ifPresent(p -> privateParams.put(confInstance.getInstanceIdentifier(), p));
        }

        return ImmutableMap.copyOf(privateParams);
    }

    private Map<String, SharedParametersProvider> loadSharedParameters(GetGlobalConfResp getGlobalConfResp,
                                                                       Map<String, SharedParametersProvider> baseSharedParams) {
        Map<String, SharedParametersProvider> sharedParams = new HashMap<>(baseSharedParams);

        for (GlobalConfInstance confInstance : getGlobalConfResp.getInstancesList()) {
            log.trace("LoadingSharedParameters from {}", confInstance);
            Optional<SharedParametersProvider> parametersProvider = loadParameters(confInstance, FILE_NAME_SHARED_PARAMETERS);
            parametersProvider.ifPresent(p -> sharedParams.put(confInstance.getInstanceIdentifier(), p));
        }

        return ImmutableMap.copyOf(sharedParams);
    }

    @SuppressWarnings("unchecked")
    private <T extends ParameterProvider> Optional<T> loadParameters(GlobalConfInstance globalConfInstance, String fileName) {
        if (globalConfInstance.containsFiles(fileName)) {
            var paramsXmlBytes = globalConfInstance.getFilesOrThrow(fileName).getBytes(StandardCharsets.UTF_8);

            try {
                var paramFactory = ParametersProviderFactory
                        .forGlobalConfVersion(globalConfInstance.getVersion());

                var result = switch (fileName) {
                    case FILE_NAME_PRIVATE_PARAMETERS -> paramFactory.privateParametersProvider(paramsXmlBytes);
                    case FILE_NAME_SHARED_PARAMETERS -> paramFactory.sharedParametersProvider(paramsXmlBytes);
                    default -> throw new IllegalArgumentException("Unknown parameter file: " + fileName);
                };

                return Optional.of((T) result);
            } catch (Exception e) {
                log.error("Unable to parameters load from {}", globalConfInstance.getInstanceIdentifier(), e);
            }
        } else {
            log.trace("Not loading parameters from [{}], file does not exist", fileName);
        }
        return Optional.empty();
    }
}
