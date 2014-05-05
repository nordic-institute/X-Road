package ee.cyber.sdsb.proxy.testsuite;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.proxy.conf.KeyConf;

public class TestServerConf extends EmptyServerConf {
    private static final Logger LOG = LoggerFactory.getLogger(
            TestServerConf.class);


    Map<BigInteger, OCSPResp> ocspResponses = new HashMap<>();

    @Override
    public String getServiceAddress(ServiceId service) {
        String serviceAddress = currentTestCase().getServiceAddress(service);
        if (serviceAddress != null) {
            return serviceAddress;
        }

        return "127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service.getServiceCode() : "");
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return currentTestCase().serviceExists(service);
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return currentTestCase().isQueryAllowed(sender, service);
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return currentTestCase().getDisabledNotice(service);
    }

    @Override
    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return currentTestCase().getRequiredCategories(service);
    }

    @Override
    public boolean isCachedOcspResponse(String certHash) {
        return true;
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) {
        if (!ocspResponses.containsKey(cert.getSerialNumber())) {
            try {
                Date thisUpdate = new DateTime().plusDays(1).toDate();
                OCSPResp resp = OcspTestUtils.createOCSPResponse(cert,
                        GlobalConf.getCaCert(cert), KeyConf.getOcspSignerCert(),
                        KeyConf.getOcspRequestKey(null), CertificateStatus.GOOD,
                        thisUpdate, null);
                OcspVerifier.verify(resp, cert, GlobalConf.getCaCert(cert));
                ocspResponses.put(cert.getSerialNumber(), resp);
            } catch (Exception e) {
                LOG.error("Error when creating OCSP response", e);
            }
        }
        return ocspResponses.get(cert.getSerialNumber());
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
