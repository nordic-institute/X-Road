/**
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
package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.hibernate.Session;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;

/**
 * Caching implementation for ServerConf
 * The long lasting and frequently used operations are cached
 */
@Slf4j
public class CachingServerConfImpl extends ServerConfImpl {

    public static final String TSP_URL = "tsp_url";

    private final int expireSeconds;
    private volatile SecurityServerId.Conf serverId;
    private final Cache<Object, List<String>> tspCache;
    private final Cache<ServiceId, Optional<ServiceType>> serviceCache;
    private final Cache<AclCacheKey, List<EndpointType>> aclCache;
    private final Cache<ClientId, Optional<ClientType>> clientCache;
    private final Cache<String, InternalSSLKey> internalKeyCache;

    /**
     * Constructor, creates time based object cache with expireSeconds (or internalKeyExpireSeconds
     * with internal key cache)
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public CachingServerConfImpl() {
        super();
        expireSeconds = SystemProperties.getServerConfCachePeriod();

        internalKeyCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build();

        tspCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .build();

        clientCache = CacheBuilder.newBuilder()
                .maximumSize(SystemProperties.getServerConfClientCacheSize())
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();

        serviceCache = CacheBuilder.newBuilder()
                .maximumSize(SystemProperties.getServerConfServiceCacheSize())
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();

        aclCache = CacheBuilder.newBuilder()
                .weigher((AclCacheKey k, List<EndpointType> v) -> v.size() + 1)
                .maximumWeight(SystemProperties.getServerConfAclCacheSize())
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();

    }

    @Override
    public InternalSSLKey getSSLKey() {
        try {
            return internalKeyCache.get(InternalSSLKey.KEY_ALIAS, super::getSSLKey);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CodedException) {
                throw (CodedException) e.getCause();
            }
            log.debug("Failed to get InternalSSLKey", e);
            return null;
        }
    }

    @Override
    public SecurityServerId.Conf getIdentifier() {
        SecurityServerId.Conf id = serverId;
        if (id == null) {
            return getAndCacheServerId(null);
        } else {
            if (GlobalConf.getServerOwner(id) == null) {
                // Globalconf and the cached value disagree on server owner (maybe changed)
                return getAndCacheServerId(id);
            } else {
                return id;
            }
        }
    }

    @SuppressWarnings("checkstyle:innerassignment")
    private synchronized SecurityServerId.Conf getAndCacheServerId(final SecurityServerId current) {
        SecurityServerId.Conf id = serverId;
        if (id == current) { //intentional reference equality test (for double-checked locking)
            serverId = id = super.getIdentifier();
        }
        return id;
    }

    @Override
    public List<String> getTspUrl() {
        try {
            return tspCache.get(TSP_URL, super::getTspUrl);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CodedException) {
                throw (CodedException) e.getCause();
            }
            log.debug("Failed to resolve tsp url", e);
            return Collections.emptyList();
        }
    }

    @Override
    public String getMemberStatus(ClientId clientId) {
        return getClient(clientId).map(ClientType::getClientStatus).orElse(null);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId clientId) {
        return getClient(clientId).map(c -> c.getIsAuthentication() == null ? IsAuthentication.NOSSL
                : IsAuthentication.valueOf(c.getIsAuthentication())).orElse(null);
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return getService(service).isPresent();
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return getService(service).map(ServiceType::getUrl).orElse(null);
    }

    @Override
    public String getServiceDescriptionURL(ServiceId service) {
        return getService(service).map(it -> it.getServiceDescription().getUrl()).orElse(null);
    }

    @Override
    public DescriptionType getDescriptionType(ServiceId service) {
        return getService(service).map(it -> it.getServiceDescription().getType()).orElse(null);
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        Optional<ServiceType> serviceTypeOptional = getService(service);
        if (!serviceTypeOptional.isPresent()) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Service '%s' not found", service);
        }
        ServiceType serviceType = serviceTypeOptional.get();
        return (boolean) ObjectUtils.defaultIfNull(serviceType.getSslAuthentication(), true);
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return getService(service).map(it ->
                it.getServiceDescription().isDisabled() ? it.getServiceDescription().getDisabledNotice() : null
        ).orElse(null);
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return getService(service).map(ServiceType::getTimeout).orElse(DEFAULT_SERVICE_TIMEOUT);
    }

    @Override
    protected List<EndpointType> getAclEndpoints(Session session, ClientId client, ServiceId service) {
        final AclCacheKey key = new AclCacheKey(client, service);
        try {
            /*
             * Implementation note. It seems that the loader function is executed in the same thread, in which case the
             * transaction simply joins the current one. However, this is not explicitly promised by the API,
             * so we start a transaction if necessary.
             */
            return aclCache.get(key, () -> tx(s -> super.getAclEndpoints(s, client, service)));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CodedException) {
                throw (CodedException) e.getCause();
            }
            log.debug("Failed get list of endpoints", e);
            return Collections.emptyList();
        }
    }

    private Optional<ServiceType> getService(ServiceId serviceId) {
        try {
            return serviceCache
                    .get(serviceId, () -> tx(session -> Optional.ofNullable(super.getService(session, serviceId))));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CodedException) {
                throw (CodedException) e.getCause();
            }
            log.debug("Failed to get service", e);
            return Optional.empty();
        }
    }

    private Optional<ClientType> getClient(ClientId clientId) {
        try {
            return clientCache.get(clientId,
                    () -> tx(session -> Optional.ofNullable(super.getClient(session, clientId))));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CodedException) {
                throw (CodedException) e.getCause();
            }
            log.debug("Failed to get client", e);
            return Optional.empty();
        }
    }

    @Override
    public void logStatistics() {
        if (log.isTraceEnabled()) {
            log.trace("ServerConf.clientCache : entries: {}, stats: {}", clientCache.size(),
                    clientCache.stats().toString());
            log.trace("ServerConf.serviceCache: entries: {}, stats: {}", serviceCache.size(),
                    serviceCache.stats().toString());
            log.trace("ServerConf.aclCache    : entries: {}, stats: {}", aclCache.size(),
                    aclCache.stats().toString());
        }
    }

    @Override
    public void clearCache() {
        log.info("Clearing configuration cache");
        internalKeyCache.invalidateAll();
    }

    @Value
    private static class AclCacheKey {
        final ClientId client;
        final ServiceId serviceId;
    }
}
