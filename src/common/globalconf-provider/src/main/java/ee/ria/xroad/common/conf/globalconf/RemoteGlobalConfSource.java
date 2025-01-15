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

import ee.ria.xroad.common.CustomForkJoinWorkerThreadFactory;
import ee.ria.xroad.common.util.FileSource;
import ee.ria.xroad.common.util.InMemoryFile;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.niis.xroad.confclient.proto.ConfClientRpcClient;
import org.niis.xroad.confclient.proto.GetGlobalConfRespStatus;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Remote GlobalConf source which loads data from confClient over gRPC.
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteGlobalConfSource implements GlobalConfSource {
    private final ConfClientRpcClient client;
    private final RemoteGlobalConfDataLoader dataLoader;

    private GlobalConfInitState lastState = GlobalConfInitState.UNKNOWN;

    @SuppressWarnings("java:S3077")
    private volatile GlobalConfData globalConfData;

    @PostConstruct
    public void afterPropertiesSet() {
        var pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
                new CustomForkJoinWorkerThreadFactory(), null, true);
        CompletableFuture.runAsync(this::load, pool);
    }

    @Override
    public void reload() {
        synchronized (RemoteGlobalConfSource.class) {
            load();
        }
    }

    /**
     * Loads GlobalConf data from a remote source.
     * Must never throw an exception or bean loading would fail.
     */
    private boolean load() {
        var stopWatch = StopWatch.createStarted();
        try {

            var getGlobalConfResp = client.getGlobalConf();
            log.debug("Received GlobalConf data in {}ms.", stopWatch.getTime(MILLISECONDS));

            switch (getGlobalConfResp.getStatus()) {
                case GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_OK:
                    // continue processing
                    break;
                case GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_UNINITIALIZED:
                    log.error("Global conf not initialized.");
                    lastState = GlobalConfInitState.UNINITIALIZED;
                    return false;
                case GetGlobalConfRespStatus.GLOBAL_CONF_STATUS_ERROR:
                    log.error("Error returned while loading global conf");
                    lastState = GlobalConfInitState.FAILURE_MALFORMED;
                    return false;
                default:
                    log.error("Unknown status while loading global conf");
                    lastState = GlobalConfInitState.FAILURE_UNEXPECTED;
                    return false;
            }

            Map<String, PrivateParametersProvider> basePrivateParams;
            Map<String, SharedParametersProvider> baseSharedParams;

            if (globalConfData != null) {
                if (globalConfData.dateRefreshed() == getGlobalConfResp.getData().getDateRefreshed()) {
                    log.debug("GlobalConf data is up to date, not reloading.");
                    return true;
                }

                basePrivateParams = globalConfData.privateParameters();
                baseSharedParams = globalConfData.sharedParameters();
            } else {
                basePrivateParams = new HashMap<>();
                baseSharedParams = new HashMap<>();
            }

            globalConfData = dataLoader.load(getGlobalConfResp.getData(), basePrivateParams, baseSharedParams);

            lastState = GlobalConfInitState.INITIALIZED;
            return true;
        } catch (Exception e) {
            log.error("Error while reloading global conf", e);
            //TODO what happens if it happens on reload and globalconf is still available from prev run?
            lastState = GlobalConfInitState.FAILURE_MALFORMED;
            return false;
        } finally {
            log.debug("GlobalConf reached {} state in {} ms.", lastState, stopWatch.getTime(MILLISECONDS));
        }
    }

    @Override
    public GlobalConfInitState getReadinessState() {
        return lastState;
    }

    @Override
    public Integer getVersion() {
        return getData().getDefaultGlobalConfVersion();
    }

    @Override
    public String getInstanceIdentifier() {
        return getData().defaultInstanceIdentifier();
    }

    @Override
    public Optional<SharedParameters> findShared(String xRoadInstance) {
        var instanceIdentifier = getInstanceIdentifier();
        log.trace("findShared(instance = {})", instanceIdentifier);

        Predicate<SharedParametersProvider> isMainInstance = params ->
                params.getSharedParameters() != null && instanceIdentifier.equals(params.getSharedParameters().getInstanceIdentifier());
        Predicate<SharedParametersProvider> notExpired = params ->
                params.getExpiresOn().isAfter(TimeUtils.offsetDateTimeNow());

        SharedParametersProvider provider = getSharedParameters(getInstanceIdentifier());
        return Optional.ofNullable(provider)
                .filter(isMainInstance.or(notExpired))
                .map(SharedParametersProvider::getSharedParameters);
    }

    @Override
    public Optional<PrivateParameters> findPrivate(String instanceId) {
        log.trace("findPrivate(instance = {})", instanceId);

        PrivateParametersProvider provider = getPrivateParameters(instanceId);
        return Optional.ofNullable(provider)
                .map(PrivateParametersProvider::getPrivateParameters);
    }

    @Override
    public List<SharedParameters> getShared() {
        OffsetDateTime now = TimeUtils.offsetDateTimeNow();
        return getData().sharedParameters().values()
                .stream()
                .filter(p -> p.getSharedParameters() != null
                        && p.getSharedParameters().getInstanceIdentifier().equals(getInstanceIdentifier()) || p.getExpiresOn().isAfter(now)
                )
                .map(SharedParametersProvider::getSharedParameters)
                .toList();
    }

    @Override
    public Optional<SharedParametersCache> findSharedParametersCache(String instanceId) {
        return findShared(instanceId).map(this::getSharedParametersCache);
    }

    @Override
    public List<SharedParametersCache> getSharedParametersCaches() {
        return getShared().stream()
                .map(this::getSharedParametersCache)
                .toList();
    }

    private SharedParametersCache getSharedParametersCache(SharedParameters sharedParams) {
        return getData().sharedParametersCacheMap().computeIfAbsent(sharedParams.getInstanceIdentifier(),
                k -> new SharedParametersCache(sharedParams));
    }

    @Override
    public boolean isExpired() {
        OffsetDateTime now = TimeUtils.offsetDateTimeNow();

        OffsetDateTime privateExpiresOn = getPrivateParameters(getInstanceIdentifier()).getExpiresOn();
        if (now.isAfter(privateExpiresOn)) {
            log.warn("Main privateParameters expired at {}", privateExpiresOn);
            return true;
        }
        OffsetDateTime sharedExpiresOn = getSharedParameters(getInstanceIdentifier()).getExpiresOn();
        if (now.isAfter(sharedExpiresOn)) {
            log.warn("Main sharedParameters expired at {}", sharedExpiresOn);
            return true;
        }
        return false;
    }

    @Override
    public FileSource<?> getFile(String fileName) {
        return new GlobalConfFileSource(this, fileName);
    }

    @RequiredArgsConstructor
    public static class GlobalConfFileSource implements FileSource<InMemoryFile> {
        private final RemoteGlobalConfSource source;
        private final String fileName;

        @Override
        public Optional<InMemoryFile> getFile() {
            var defaultInstanceFiles = source.getData().defaultInstanceFiles();
            if (defaultInstanceFiles.containsKey(fileName)) {
                return Optional.of(defaultInstanceFiles.get(fileName));
            }
            return Optional.empty();
        }
    }

    record GlobalConfData(Long dateRefreshed,
                          String defaultInstanceIdentifier,
                          int defaultGlobalConfVersion,
                          Map<String, PrivateParametersProvider> privateParameters,
                          Map<String, SharedParametersProvider> sharedParameters,
                          Map<String, InMemoryFile> defaultInstanceFiles,
                          ConcurrentHashMap<String, SharedParametersCache> sharedParametersCacheMap) {

        /**
         * Get version of a GlobalConf for current instance identifier.
         */
        int getDefaultGlobalConfVersion() {
            return defaultGlobalConfVersion;
        }
    }

    private GlobalConfData getData() {
        if (globalConfData == null) {
            synchronized (RemoteGlobalConfSource.class) {
                if (globalConfData == null) {
                    log.warn("Configuration source was not loaded. Trying to reload..");
                    if (!load()) {
                        throw new GlobalConfInitException(lastState);
                    }
                }
            }
        }
        return globalConfData;
    }

    private PrivateParametersProvider getPrivateParameters(String instanceId) {
        return getData().privateParameters().get(instanceId);
    }

    private SharedParametersProvider getSharedParameters(String instanceId) {
        return getData().sharedParameters().get(instanceId);
    }

}
