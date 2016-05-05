/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.WsdlDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.db.TransactionCallback;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XroadId;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Server conf implementation.
 */
public class ServerConfImpl implements ServerConfProvider {

    // default service connection timeout in seconds
    private static final int DEFAULT_SERVICE_TIMEOUT = 30;

    private static SecurityServerId identifier;

    // ------------------------------------------------------------------------

    @Override
    public SecurityServerId getIdentifier() {
        synchronized (this) {
            return tx(session -> {
                if (identifier == null) {
                    ServerConfType confType = getConf();

                    ClientType owner = confType.getOwner();
                    if (owner == null) {
                        throw new CodedException(X_MALFORMED_SERVERCONF,
                                "Owner is not set");
                    }

                    identifier = SecurityServerId.create(owner.getIdentifier(),
                            confType.getServerCode());
                }

                return identifier;
            });
        }
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return tx(session -> new ServiceDAOImpl().serviceExists(session,
                service));
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getUrl();
            }

            return null;
        });
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getTimeout();
            }

            return DEFAULT_SERVICE_TIMEOUT;
        });
    }

    @Override
    public List<ServiceId> getAllServices(ClientId serviceProvider) {
        return tx(session -> new ServiceDAOImpl().getServices(session,
                serviceProvider));
    }

    @Override
    public List<ServiceId> getAllowedServices(ClientId serviceProvider, ClientId client) {
        return tx(session -> {
            List<ServiceId> allServices =
                    new ServiceDAOImpl().getServices(session, serviceProvider);
            return allServices.stream()
                    .filter(s -> internalIsQueryAllowed(session, client, s))
                    .collect(Collectors.toList());
        });
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return ObjectUtils.defaultIfNull(
                        serviceType.getSslAuthentication(), true);
            }

            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Service '%s' not found", service);
        });
    }

    @Override
    public List<ClientId> getMembers() {
        return tx(session -> getConf().getClient().stream()
                .map(c -> c.getIdentifier())
                .collect(Collectors.toList()));
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return tx(session -> {
            ClientType client = getClient(session, memberId);
            if (client != null) {
                return client.getClientStatus();
            } else {
                return null;
            }
        });
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return tx(session -> {
            ClientType clientType = getClient(session, client);
            if (clientType != null) {
                String isAuth = clientType.getIsAuthentication();
                if (isAuth == null) {
                    return IsAuthentication.NOSSL;
                }

                return IsAuthentication.valueOf(isAuth);
            }

            return null; // client not found
        });
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return tx(session -> new ClientDAOImpl().getIsCerts(session,
                client).stream().map(c -> readCertificate(c.getData()))
                .collect(Collectors.toList()));
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return tx(session -> {
            WsdlType wsdlType = getWsdl(session, service);
            if (wsdlType != null && wsdlType.isDisabled()) {
                if (wsdlType.getDisabledNotice() == null) {
                    return String.format("Service '%s' is disabled", service);
                }

                return wsdlType.getDisabledNotice();
            }

            return null;
        });
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return InternalSSLKey.load();
    }

    @Override
    public boolean isQueryAllowed(ClientId client, ServiceId service) {
        return tx(session -> internalIsQueryAllowed(session, client, service));
    }

    @Override
    public List<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getRequiredSecurityCategory();
            }

            return new ArrayList<SecurityCategoryId>();
        });
    }

    @Override
    public List<String> getTspUrl() {
        return tx(session -> getConf().getTsp().stream()
                .map(tsp -> tsp.getUrl())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList()));
    }

    // ------------------------------------------------------------------------

    protected ServerConfType getConf() {
        return new ServerConfDAOImpl().getConf();
    }

    protected ClientType getClient(Session session, ClientId c) {
        return new ClientDAOImpl().getClient(session, c);
    }

    protected ServiceType getService(Session session, ServiceId s) {
        return new ServiceDAOImpl().getService(session, s);
    }

    protected WsdlType getWsdl(Session session, ServiceId service) {
        return new WsdlDAOImpl().getWsdl(session, service);
    }

    private boolean internalIsQueryAllowed(Session session, ClientId client, ServiceId service) {

        if (client == null) {
            return false;
        }

        ClientType clientType = getClient(session, service.getClientId());
        if (clientType == null) {
            return false;
        }

        for (AccessRightType accessRight : clientType.getAcl()) {
            if (!StringUtils.equals(service.getServiceCode(),
                    accessRight.getServiceCode())) {
                continue;
            }

            XroadId subjectId = accessRight.getSubjectId();

            if (subjectId instanceof GlobalGroupId) {
                if (GlobalConf.isSubjectInGlobalGroup(client,
                        (GlobalGroupId) subjectId)) {
                    return true;
                }
            } else if (subjectId instanceof LocalGroupId) {
                if (isMemberInLocalGroup(session, client,
                        (LocalGroupId) subjectId, service)) {
                    return true;
                }
            } else if (subjectId instanceof ClientId) {
                if (client.equals(subjectId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isMemberInLocalGroup(Session session, ClientId member,
            LocalGroupId groupId, ServiceId serviceId) {
        LocalGroupType group = findLocalGroup(session, groupId.getGroupCode(),
                serviceId.getClientId());
        if (group == null) {
            return false;
        }

        return group.getGroupMember().stream()
                .filter(g -> g.getGroupMemberId().equals(member))
                .findFirst().isPresent();
    }

    private LocalGroupType findLocalGroup(Session session, String groupCode, ClientId groupOwnerId) {
        // No need to check for null because we already know the service
        // (and therefore the owner) exists.
        return getClient(session, groupOwnerId).getLocalGroup().stream()
                .filter(g -> StringUtils.equals(groupCode, g.getGroupCode()))
                .findFirst().orElse(null);
    }

    protected static <T> T tx(TransactionCallback<T> t) {
        try {
            return doInTransaction(t);
        } catch (Exception e) {
            throw translateException(e);
        }
    }
}
