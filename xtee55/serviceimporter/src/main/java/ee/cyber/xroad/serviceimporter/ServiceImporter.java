package ee.cyber.xroad.serviceimporter;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.*;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.LocalGroupId;
import ee.cyber.sdsb.common.identifier.SdsbId;
import ee.cyber.sdsb.common.identifier.SdsbObjectType;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.request.ManagementRequestSender;
import ee.cyber.xroad.mediator.BackendTypes;
import ee.cyber.xroad.mediator.IdentifierMappingImpl;
import ee.cyber.xroad.mediator.MediatorSystemProperties;

import static ee.cyber.xroad.serviceimporter.Helper.getConf;
import static ee.cyber.xroad.serviceimporter.Helper.getIdentifier;
import static ee.cyber.xroad.serviceimporter.Helper.urlEncode;

public class ServiceImporter {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServiceImporter.class);

    private static final String STATE_SAVED = "saved";

    private static final String[] XROADV5_META_SERVICES = {
        "getProducerACL", "getServiceACL"
    };

    private IdentifierMappingImpl identifierMapping;
    private GlobalConf globalConf;
    private ServerConfType serverConf;
    private XConf xConf;
    private Date now;

    public ServiceImporter() {
        this.identifierMapping = new IdentifierMappingImpl();
        this.globalConf = new GlobalConf();
        this.xConf = new XConf();
        this.now = new Date();
    }

    public void doImport() throws Exception {
        LOG.info("Importing clients...");

        serverConf = getConf(); // will throw exception if server conf not initalized

        xConf.readLock();

        try {
            Set<ClientId> importedClients = new HashSet<>();

            for (XConf.Consumer consumer : xConf.getConsumers()) {
                ClientType client = importClient(consumer);

                if (client != null) {
                    String peerType = xConf.getPeerType(consumer);

                    if ("https".equals(peerType)) {
                        client.setIsAuthentication("SSLAUTH");
                    } else if ("https noauth".equals(peerType)) {
                        client.setIsAuthentication("SSLNOAUTH");
                    } else {
                        client.setIsAuthentication("NOSSL");
                    }

                    importInternalSSLCerts(client, consumer, false);

                    importedClients.add(client.getIdentifier());
                }
            }

            for (XConf.Producer producer : xConf.getProducers()) {
                ClientType client = importClient(producer);
                if (client != null) {
                    importInternalSSLCerts(client, producer,
                        importedClients.contains(client.getIdentifier()));

                    importServices(client, producer);

                    importedClients.add(client.getIdentifier());
                }
            }

            Set<ClientId> mappedClients = identifierMapping.getClientIds();

            SecurityServerId serverId = getSecurityServerId();
            ManagementRequestSender sender = getManagementRequestSender();

            Iterator<ClientType> it = getConf().getClient().iterator();
            while (it.hasNext()) {
                ClientType client = it.next();
                ClientId clientId = client.getIdentifier();
                String status = client.getClientStatus();

                // Delete the clients who are in the mapping but were
                // not imported.
                if (mappedClients.contains(clientId) &&
                        !importedClients.contains(clientId)) {

                    if (ClientType.STATUS_REGINPROG.equals(status) ||
                        ClientType.STATUS_REGISTERED.equals(status)) {

                        LOG.info("Sending deletion request for client '{}'",
                                 clientId);
                        sender.sendClientDeletionRequest(serverId, clientId);
                    }

                    LOG.info("Deleting client '{}'", clientId);
                    it.remove();
                }
            }
        } finally {
            xConf.unlock();
        }
    }

    private ClientType importClient(XConf.Org org) throws Exception {
        ClientId mappedId = identifierMapping.getClientId(org.getShortName());

        if (mappedId == null) {
            LOG.warn("Could not find identifier mapping " +
                     "for client '{}', skipping", org.getShortName());
            return null;
        }

        ClientType mappedClient = ClientDAOImpl.getInstance().getClient(
                HibernateUtil.getSession(), mappedId);

        if (mappedClient == null) {
            LOG.info("Importing client '{}'", org.getShortName());

            mappedClient = new ClientType();
            mappedClient.setConf(getConf());
            mappedClient.setIdentifier(getIdentifier(mappedId));
            mappedClient.setClientStatus(STATE_SAVED);

            getConf().getClient().add(mappedClient);
        } else {
            LOG.info("Client '{}' already exists in SDSB", org.getShortName());
        }

        // delete everything to make sure the confs are in sync

        LOG.info("Deleting client WSDLs and ACLs");

        for (WsdlType wsdl : mappedClient.getWsdl()) {
            wsdl.setClient(null);
        }

        mappedClient.getWsdl().clear();
        mappedClient.getAcl().clear();

        return mappedClient;
    }

    private void importInternalSSLCerts(ClientType client, XConf.Org org,
            boolean keepOld) throws Exception {
        LOG.info("Importing internal SSL certs for '{}'", org.getShortName());

        if (!keepOld) {
            client.getIsCert().clear();
        }

        List<CertificateType> newCerts = new ArrayList<>();

        certs:
        for (byte[] cert : xConf.getInternalSSLCerts(org)) {
            for (CertificateType existingCert : client.getIsCert()) {
                if (Arrays.equals(cert, existingCert.getData())) {
                    continue certs;
                }
            }

            CertificateType certType = new CertificateType();
            certType.setData(cert);
            newCerts.add(certType);
        }

        client.getIsCert().addAll(newCerts);
    }

    private void importServices(ClientType client, XConf.Producer producer)
            throws Exception {
        LOG.info("Importing services for '{}'", producer.getShortName());

        if (!xConf.adapterExists(producer)) {
            LOG.info("Adapter not configured, skipping");
            return;
        }

        int serviceTimeout = xConf.getServiceTimeout(producer);

        Map<String, List<String>> authorizedClients =
            xConf.getServiceAuthorizedClients(producer);

        Map<String, List<String>> authorizedGroups =
            xConf.getServiceAuthorizedGroups(producer);

        String adapterURL = xConf.getAdapterURL(producer);
        boolean sslNoAuth = xConf.getPeerType(producer).equals("https noauth");

        WsdlType newWsdl = new WsdlType();
        newWsdl.setUrl(xConf.getWsdlURL(producer));
        newWsdl.setClient(client);
        newWsdl.setBackendURL(adapterURL);
        newWsdl.setBackend(BackendTypes.XROADV5);
        newWsdl.setRefreshedDate(now);

        for (String serviceName : xConf.getServices(producer)) {
            LOG.info("Importing service '{}'", serviceName);

            String[] splitService = serviceName.split("\\.");

            ServiceType service = new ServiceType();
            service.setWsdl(newWsdl);
            service.setServiceCode(splitService[1]);
            if (splitService.length == 3) {
                service.setServiceVersion(splitService[2]);
            }
            service.setUrl(getServiceMediatorURL(client.getIdentifier()));
            service.setTimeout(serviceTimeout);
            service.setSslAuthentication(!sslNoAuth);

            newWsdl.getService().add(service);

            importServiceAcl(getAcl(client, service.getServiceCode()),
                             authorizedClients.get(service.getServiceCode()),
                             authorizedGroups.get(service.getServiceCode()));
        }

        // for meta services, import only acl
        for (String serviceCode : XROADV5_META_SERVICES) {
            importServiceAcl(getAcl(client, serviceCode),
                             authorizedClients.get(serviceCode),
                             authorizedGroups.get(serviceCode));
        }

        client.getWsdl().add(newWsdl);
    }

    private void importServiceAcl(AclType acl, List<String> authorizedClients,
            List<String> authorizedGroups) throws Exception {

        LOG.info("Importing ACL for service '{}'", acl.getServiceCode());

        if (authorizedClients != null) {
            // add those authorized clients for which we have
            // identifier mapping
            for (String subjectShortName : authorizedClients) {
                ClientId subjectId =
                    identifierMapping.getClientId(subjectShortName);

                if (subjectId != null) {
                    AuthorizedSubjectType authorizedSubject =
                        new AuthorizedSubjectType();
                    authorizedSubject.setSubjectId(getIdentifier(subjectId));
                    authorizedSubject.setRightsGiven(now);

                    acl.getAuthorizedSubject().add(authorizedSubject);
                } else {
                    LOG.warn("Could not find identifier mapping for " +
                             "authorized subject '{}', skipping",
                             subjectShortName);
                }
            }
        }

        if (authorizedGroups != null) {
            String instanceIdentifier =
                globalConf.getConfType().getInstanceIdentifier();

            for (String subjectShortName : authorizedGroups) {
                GlobalGroupId groupId = GlobalGroupId.create(
                    instanceIdentifier, subjectShortName);

                AuthorizedSubjectType authorizedSubject =
                    new AuthorizedSubjectType();
                authorizedSubject.setSubjectId(getIdentifier(groupId));
                authorizedSubject.setRightsGiven(now);

                acl.getAuthorizedSubject().add(authorizedSubject);
            }
        }
    }

    private String getServiceMediatorURL(ClientId clientId) throws Exception {
        return "http://"
            + MediatorSystemProperties.getServiceMediatorConnectorHost()
            + ":" + MediatorSystemProperties.getServiceMediatorHttpPort()
            + "/?" + getClientIdAsQueryString(clientId);
    }

    private String getClientIdAsQueryString(ClientId clientId)
            throws Exception {
        StringBuilder str = new StringBuilder();

        str.append("sdsbInstance=");
        str.append(urlEncode(clientId.getSdsbInstance()));
        str.append("&memberClass=");
        str.append(urlEncode(clientId.getMemberClass()));
        str.append("&memberCode=");
        str.append(urlEncode(clientId.getMemberCode()));

        if (clientId.getSubsystemCode() != null) {
            str.append("&subsystemCode=");
            str.append(urlEncode(clientId.getSubsystemCode()));
        }

        return str.toString();
    }

    private AclType getAcl(ClientType client, String serviceCode) {
        for (AclType acl : client.getAcl()) {
            if (acl.getServiceCode().equals(serviceCode)) {
                return acl;
            }
        }

        AclType acl = new AclType();
        acl.setServiceCode(serviceCode);
        client.getAcl().add(acl);

        return acl;
    }

    private SecurityServerId getSecurityServerId() {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        String serverCode = serverConf.getServerCode();

        return SecurityServerId.create(
            ownerId.getSdsbInstance(), ownerId.getMemberClass(),
            ownerId.getMemberCode(), serverCode);
    }

    private ManagementRequestSender getManagementRequestSender() {
        ClientId receiver = globalConf.getManagementRequestServiceId();
        ClientId sender = serverConf.getOwner().getIdentifier();

        return new ManagementRequestSender(
            "serviceImporter", receiver, sender);
    }

    public void doExport() throws Exception {
        LOG.info("Exporting clients...");

        getConf(); // will throw exception if server conf not initalized

        xConf.writeLock();

        try {
            Set<String> exportedConsumers = new HashSet<>();
            Set<String> exportedProducers = new HashSet<>();

            for (ClientType client : getConf().getClient()) {
                String shortName =
                    identifierMapping.getShortName(client.getIdentifier());

                if (shortName == null) {
                    LOG.warn("Could not find identifier mapping for " +
                             "client '{}', skipping", client.getIdentifier());
                    continue;
                }

                String fullName = globalConf.getMemberName(client.getIdentifier());

                XConf.Consumer consumer = new XConf.Consumer(shortName);
                XConf.Producer producer = new XConf.Producer(shortName);

                if (xConf.createOrg(consumer, fullName)) {
                    LOG.info("Exporting client '{}' as consumer",
                             client.getIdentifier());
                }

                if (xConf.createOrg(producer, fullName)) {
                    LOG.info("Exporting client '{}' as producer",
                             client.getIdentifier());
                }

                exportInternalSSLConf(consumer, client);
                exportInternalSSLConf(producer, client);

                exportedConsumers.add(shortName);
                exportedProducers.add(shortName);

                exportServices(producer, client);
            }

            Set<String> mappedClients = identifierMapping.getShortNames();

            Iterator<XConf.Consumer> itc = xConf.getConsumers().iterator();
            while (itc.hasNext()) {
                XConf.Consumer consumer = itc.next();
                String shortName = consumer.getShortName();

                // Delete the consumers who are in the mapping but were
                // not exported.
                if (mappedClients.contains(shortName) &&
                        !exportedConsumers.contains(shortName)) {
                    LOG.info("Deleting consumer '{}'", shortName);

                    xConf.deleteOrg(consumer);
                }
            }

            Iterator<XConf.Producer> itp = xConf.getProducers().iterator();
            while (itp.hasNext()) {
                XConf.Producer producer = itp.next();
                String shortName = producer.getShortName();

                // Delete the producers who are in the mapping but were
                // not exported.
                if (mappedClients.contains(shortName) &&
                        !exportedProducers.contains(shortName)) {
                    LOG.info("Deleting producer '{}'", shortName);

                    xConf.deleteOrg(producer);
                }
            }
        } finally {
            xConf.unlock();
        }
    }

    private void exportServices(XConf.Producer producer, ClientType client)
            throws Exception {
        LOG.info("Exporting services for '{}'", client.getIdentifier());

        if (client.getWsdl().isEmpty()) {
            LOG.info("No services found, deleting adapter conf and ACL");
            xConf.deleteAdapterConf(producer);
            xConf.deleteAcl(producer);
            return;
        }

        xConf.setPeerIP(producer, "127.0.0.1");
        xConf.setPeerPort(producer,
            MediatorSystemProperties.getServiceMediatorHttpPort());
        xConf.setAdapterURI(producer,
            "/?" + getClientIdAsQueryString(client.getIdentifier()));
        xConf.setSchemaURI(producer,
            "/wsdl?" + getClientIdAsQueryString(client.getIdentifier()));

        XConfAcl xconfAcl = new XConfAcl();

        // export localgroups as lists of clients
        for (LocalGroupType localGroup : client.getLocalGroup()) {
            xconfAcl.localGroups.put(localGroup.getGroupCode(),
                localGroup.getGroupMember());
        }

        int timeout = 0;
        for (WsdlType wsdl : client.getWsdl()) {
            LOG.debug("Exporting services from WSDL '{}'", wsdl.getUrl());

            for (ServiceType service : wsdl.getService()) {
                exportService(producer, service, xconfAcl);

                timeout = Math.max(timeout, service.getTimeout());
            }
        }

        for (AclType aclType : client.getAcl()) {
            exportAcl(producer, aclType, xconfAcl);
        }

        xConf.setServiceTimeout(producer, timeout);

        xConf.saveQueries(producer, xconfAcl.queries);
        xConf.saveConsumers(producer, xconfAcl.consumers);
        xConf.saveGroups(producer, xconfAcl.groups);
        xConf.saveACL(producer, xconfAcl.cacl);
        xConf.saveGACL(producer, xconfAcl.gacl);
    }

    private void exportService(XConf.Producer producer, ServiceType service,
            XConfAcl xconfAcl) throws Exception {
        LOG.debug("Exporting service '{}'", service.getServiceCode());

        String query = producer.getShortName() + "." + service.getServiceCode();
        String queryWithVersion = service.getServiceVersion() != null
            ? query + "." + service.getServiceVersion() : query;

        xconfAcl.queries.add(queryWithVersion);
     }

    private void exportAcl(XConf.Producer producer, AclType aclType,
            XConfAcl xconfAcl) {
        LOG.debug("Exporting acl for '{}'", aclType.getServiceCode());

        String query = producer.getShortName() + "." + aclType.getServiceCode();

        for (AuthorizedSubjectType subject : aclType.getAuthorizedSubject()) {
            SdsbId subjectId = subject.getSubjectId();

            if (subjectId.getObjectType() == SdsbObjectType.GLOBALGROUP) {
                String group = ((GlobalGroupId) subjectId).getGroupCode();

                xconfAcl.groups.add(group);

                if (!xconfAcl.gacl.containsKey(query)) {
                    xconfAcl.gacl.put(query, new HashSet<String>());
                }

                xconfAcl.gacl.get(query).add(group);

            } else if (subjectId.getObjectType() == SdsbObjectType.LOCALGROUP) {
                List<GroupMemberType> localGroupMembers =
                        xconfAcl.localGroups.get(
                                ((LocalGroupId) subjectId).getGroupCode());

                for (GroupMemberType groupMember : localGroupMembers) {
                    ClientId clientId = groupMember.getGroupMemberId();

                    String consumerShortName =
                            identifierMapping.getShortName(clientId);

                    if (consumerShortName != null) {
                        xconfAcl.consumers.add(consumerShortName);

                        if (!xconfAcl.cacl.containsKey(query)) {
                            xconfAcl.cacl.put(query, new HashSet<String>());
                        }

                        xconfAcl.cacl.get(query).add(consumerShortName);

                    } else {
                        LOG.warn("Could not find identifier mapping for " +
                                 " authorizedSubject '{}', skipping", clientId);
                    }
                }
            } else {
                String consumerShortName =
                        identifierMapping.getShortName((ClientId) subjectId);

                if (consumerShortName != null) {
                    xconfAcl.consumers.add(consumerShortName);

                    if (!xconfAcl.cacl.containsKey(query)) {
                        xconfAcl.cacl.put(query, new HashSet<String>());
                    }

                    xconfAcl.cacl.get(query).add(consumerShortName);
                } else {
                    LOG.warn("Could not find identifier mapping for " +
                             " authorizedSubject '{}', skipping", subjectId);
                }
            }
        }
    }

    private void exportInternalSSLConf(XConf.Org org, ClientType client)
            throws Exception {
        LOG.info("Exporting internal SSL conf for '{}'", org.getShortName());

        // both, consumer and producer, will from now on disable ssl for
        // internal connections to allow access for mediators
        xConf.setPeerType(org, "http");

        // these are actually irrelevant, as ssl is disabled
        List<byte[]> isCerts = new ArrayList<>();
        for (CertificateType ct : client.getIsCert()) {
            isCerts.add(ct.getData());
        }

        xConf.setInternalSSLCerts(org, isCerts);
    }

    static class XConfAcl {
        Map<String, List<GroupMemberType>> localGroups = new HashMap<>();

        // all known queries of this producer
        Set<String> queries = new HashSet<>();
        // all known consumers of this producer's services
        Set<String> consumers = new HashSet<>();
        // all known groups of consumers of this producer's services
        Set<String> groups = new HashSet<>();

        // queries mapped to allowed consumers
        Map<String, Set<String>> cacl = new HashMap<>();
        // queries mapped to allowed groups
        Map<String, Set<String>> gacl = new HashMap<>();
    }
}
