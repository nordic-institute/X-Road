package ee.cyber.xroad.mediator.service;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey;
import ee.cyber.sdsb.common.conf.serverconf.IsAuthentication;
import ee.cyber.sdsb.common.conf.serverconf.model.GlobalConfDistributorType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.xroad.mediator.MediatorServerConfProvider;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey.KEY_ALIAS;
import static ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey.KEY_PASSWORD;
import static ee.cyber.sdsb.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

class IntegrationTestServerConfImpl implements
        MediatorServerConfProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(IntegrationTestServerConfImpl.class);

    @Override
    public SecurityServerId getIdentifier() {
        return SecurityServerId.create(
                "EE", "BUSINESS", "producer_nossl", "server");
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return false; // Not used
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return null; // Not used
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return 10;
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return null; // Not used
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client)
            throws Exception {
        LOG.trace("Getting IS certs for client '{}'", client);
        // TODO: Verify if works!

        if (getClientProducerSslnoauth().equals(client)
                || getClientProducerSslauth().equals(client)) {
            return getIsCerts(client.getMemberCode());
        }

        return new ArrayList<X509Certificate>();
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        // TODO: Verify if works!
        File keyFile = new File("src/test/resources/sslkey.p12");

        KeyStore ks = loadPkcs12KeyStore(keyFile, KEY_PASSWORD);

        PrivateKey key = (PrivateKey) ks
                .getKey(KEY_ALIAS, KEY_PASSWORD);
        if (key == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get key from '%s'", keyFile);
        }

        X509Certificate cert =
                (X509Certificate) ks.getCertificate(KEY_ALIAS);
        if (cert == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get certificate from '%s'", keyFile);
        }
        return new InternalSSLKey(key, cert);
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        if (getServiceProducerSslauthTestQuery().equals(service)
                || getServiceProducerSslauthHttpTestQuery()
                        .equals(service)) {
            return true;
        }

        return false;
    }

    @Override
    public List<ClientId> getMembers() throws Exception {
        return null; // Not used
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return false; // Not used
    }

    @Override
    public Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId service) {
        return null; // Not used
    }

    @Override
    public List<GlobalConfDistributorType> getFileDistributors() {
        return null; // Not used
    }

    @Override
    public List<String> getTspUrl() {
        return null; // Not used
    }

    @Override
    public boolean isSdsbService(ServiceId serviceId) {

        if (getServiceProducerNosslGetRandom().equals(serviceId)
                || getServiceLiiklusregisterGetRandom().equals(
                        serviceId)
                || getServiceFooriregisterGetRandom().equals(serviceId)) {
            return false;
        }

        return true;
    }

    @Override
    public String getBackendURL(ServiceId serviceId) {
        if (getServiceLiiklusregisterGetRandom().equals(serviceId)) {
            return "http://127.0.0.1:8060/xrddl-andmekogu";
        } else if (getServiceFooriregisterGetRandom().equals(serviceId)) {
            return "http://127.0.0.1:8060/xrdrpc-andmekogu";
        } else if (getServiceKiiruskaameraregisterGetRandom().equals(
                serviceId)) {
            return "http://127.0.0.1:6669";
        } else if (getServiceProducerTestQuery().equals(serviceId)) {
            return "http://localhost:8060";
        } else if (getServiceProducerNosslTestQuery().equals(serviceId)) {
            return "http://localhost:8060";
        } else if (getServiceProducerNosslTestQuery().equals(serviceId)) {
            return "http://localhost:8160";
        } else if (getServiceProducerSslNoauthTestQuery()
                .equals(serviceId)) {
            return "https://localhost:8061";
        } else if (getServiceProducerSslauthTestQuery().equals(serviceId)) {
            return "https://localhost:8061";
        } else if (getServiceProducerSslauthHttpTestQuery()
                .equals(serviceId)) {
            return "http://localhost:8060";
        }

        return null;
    }

    @Override
    public String getBackendURL(ClientId clientId) {
        if (getClientLiiklusregister().equals(clientId)) {
            return "http://127.0.0.1:8060/xrddl-andmekogu";
        } else if (getClientFooriregister().equals(clientId)) {
            return "http://127.0.0.1:8060/xrdrpc-andmekogu";
        } else if (getClientKiiruskaameraregister().equals(clientId)) {
            return "http://127.0.0.1:6669";
        } else if (getClientProducer().equals(clientId)) {
            return "http://localhost:8060";
        } else if (getClientProducerNossl().equals(clientId)) {
            return "http://localhost:8060";
        } else if (getClientProducerSslnoauth().equals(clientId)) {
            return "https://localhost:8061";
        } else if (getClientProducerSslauth().equals(clientId)) {
            return "https://localhost:8061";
        } else if (getClientProducerSslauthHttp().equals(clientId)) {
            return "http://localhost:8060";
        }

        return null;
    }

    @Override
    public List<String> getAdapterWSDLUrls(ClientId clientId) {
        List<String> result = new ArrayList<String>();

        if (getClientLiiklusregister().equals(clientId)) {
            result.add("http://192.168.74.203:55555/foo");
        } else if (getClientFooriregister().equals(clientId)) {
            result.add("http://192.168.74.203:55555/foo");
        } else if (getClientProducerNossl().equals(clientId)) {
            result.add("http://iks2-testhost:8080/testservice-0.1/xrddl");
            result.add(
                    "http://iks2-testhost:8080/testservice-0.1/transpordiamet");
        }

        return result;
    }

    // -- Clients - start ---

    private ClientId getClientLiiklusregister() {
        return ClientId.create("EE", "riigiasutus", "liiklusregister");
    }

    public ClientId getClientFooriregister() {
        return ClientId.create("EE", "riigiasutus", "fooriregister");
    }

    public ClientId getClientKiiruskaameraregister() {
        return ClientId
                .create("EE", "riigiasutus", "kiiruskaameraregister");
    }

    public ClientId getClientProducer() {
        return ClientId.create("EE", "BUSINESS", "producer");
    }

    public ClientId getClientProducerNossl() {
        return ClientId.create("EE", "BUSINESS", "producer_nossl");
    }

    public ClientId getClientProducerSslnoauth() {
        return ClientId.create("EE", "BUSINESS", "producer_sslnoauth");
    }

    public ClientId getClientProducerSslauth() {
        return ClientId.create("EE", "BUSINESS", "producer_sslauth");
    }

    public ClientId getClientProducerSslauthHttp() {
        return ClientId.create("EE", "BUSINESS", "producer_sslauth_http");
    }

    // -- Clients - end ---

    // -- Services - start ---

    private ServiceId getServiceLiiklusregisterGetRandom() {
        return ServiceId.create(
                getClientLiiklusregister(), "xrddlGetRandom.v1");
    }

    private ServiceId getServiceFooriregisterGetRandom() {
        return ServiceId.create(
                getClientFooriregister(), "xrdrpcGetRandom.v1");
    }

    private ServiceId getServiceKiiruskaameraregisterGetRandom() {
        return ServiceId.create(
                getClientKiiruskaameraregister(), "sdsbGetRandom");
    }

    private ServiceId getServiceProducerTestQuery() {
        return ServiceId.create(getClientProducer(), "testQuery");
    }

    private ServiceId getServiceProducerNosslTestQuery() {
        return ServiceId.create(getClientProducerNossl(), "testQuery");
    }

    private ServiceId getServiceProducerNosslGetRandom() {
        return ServiceId.create(getClientProducerNossl(), "xrddlGetRandom");
    }

    private ServiceId getServiceProducerSslNoauthTestQuery() {
        return ServiceId.create(getClientProducerSslnoauth(), "testQuery");
    }

    private ServiceId getServiceProducerSslauthTestQuery() {
        return ServiceId.create(getClientProducerSslauth(), "testQuery");
    }

    private ServiceId getServiceProducerSslauthHttpTestQuery() {
        return ServiceId.create(
                getClientProducerSslauthHttp(), "testQuery");
    }

    // -- Services - end ---

    private List<X509Certificate> getIsCerts(String clientCode)
            throws Exception {
        String isCertBase64 = FileUtils.readFileToString(new File(
                "src/test/resources/iscert-" + clientCode + ".base64"));
        return Collections.singletonList(readCertificate(isCertBase64));
    }

    @Override
    public List<ServiceId> getAllServices(ClientId serviceProvider) {
        return null;
    }

    @Override
    public List<ServiceId> getAllowedServices(ClientId serviceProvider,
            ClientId client) {
        return null;
    }
}
