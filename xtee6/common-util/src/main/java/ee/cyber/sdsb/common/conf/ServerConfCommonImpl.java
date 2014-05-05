package ee.cyber.sdsb.common.conf;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.conf.serverconf.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.ObjectFactory;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfType;
import ee.cyber.sdsb.common.conf.serverconf.ServiceType;
import ee.cyber.sdsb.common.conf.serverconf.WsdlType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.SchemaValidator;

/**
 * This is the base class for server configuration. It provides common
 * functionality and services to all subclasses.
 */
public class ServerConfCommonImpl extends AbstractXmlConf<ServerConfType>
        implements ServerConfCommonProvider {

    protected Map<ServiceId, ServiceType> serviceIdToServiceType =
            new HashMap<>();
    protected Map<ServiceId, WsdlType> serviceIdToWsdlType =
            new HashMap<>();
    protected Map<ClientId, ClientType> clientIdToClientType = new HashMap<>();

    public ServerConfCommonImpl(String confFileName,
            Class<? extends SchemaValidator> schemaValidator) {
        super(ObjectFactory.class, confFileName, schemaValidator);
        try {
            cacheClientData();
        } catch (Exception e) {
            throw ErrorCodes.translateException(e);
        }
    }

    // --------------------------------------------------------------------- //

    @Override
    public SecurityServerId getIdentifier() {
        Object ownerRef = confType.getOwner();
        if (!(ownerRef instanceof ClientType)) {
            throw new RuntimeException("Owner must be client");
        }

        ClientType owner = (ClientType) ownerRef;
        return SecurityServerId.create(owner.getIdentifier(),
                confType.getServerCode());
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        if (serviceIdToServiceType.containsKey(service)) {
            return serviceIdToServiceType.get(service).getUrl();
        }

        return null;
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        if (serviceIdToServiceType.containsKey(service)) {
            return serviceIdToServiceType.get(service).getTimeout();
        }

        return 30; // TODO: Default timeout
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        if (clientIdToClientType.containsKey(client)) {
            String isAuth =
                    clientIdToClientType.get(client).getIsAuthentication();
            if (isAuth == null) {
                return IsAuthentication.NOSSL;
            }

            return IsAuthentication.valueOf(isAuth);
        }

        return null; // client not found
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        if (clientIdToClientType.containsKey(client)) {
            for (byte[] cert: clientIdToClientType.get(client).getIsCert()) {
                certs.add(CryptoUtils.readCertificate(cert));
            }
        }

        return certs;
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        if (confType.getInternalSSLCert() != null) {
            byte[] cert = confType.getInternalSSLCert();
            if (cert == null) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                        "No SSL cert specified");
            }

            String fileName = getConfFileDir() + InternalSSLKey.KEY_FILE_NAME;
            KeyStore ks = CryptoUtils.loadKeyStore("pkcs12", fileName, null);

            PrivateKey key = (PrivateKey) ks.getKey(InternalSSLKey.KEY_ALIAS,
                    InternalSSLKey.KEY_PASSWORD);
            if (key == null) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                        "Could not get key from '%s'", fileName);
            }

            return new InternalSSLKey(key, CryptoUtils.readCertificate(cert));
        }

        return null;
    }

    // --------------------------------------------------------------------- //

    private void cacheClientData() throws Exception {
        for (ClientType clientType : confType.getClient()) {
            clientIdToClientType.put(clientType.getIdentifier(), clientType);
            cacheClientServiceData(clientType);
        }
    }

    private void cacheClientServiceData(ClientType clientType) {
        ClientId clientId = clientType.getIdentifier();
        for (WsdlType wsdl : clientType.getWsdl()) {
            for (ServiceType serviceType : wsdl.getService()) {
                ServiceId serviceId = ServiceId.create(
                        clientId, serviceType.getServiceCode(),
                        serviceType.getServiceVersion());
                serviceIdToServiceType.put(serviceId, serviceType);
                serviceIdToWsdlType.put(serviceId, wsdl);
            }
        }
    }
}
