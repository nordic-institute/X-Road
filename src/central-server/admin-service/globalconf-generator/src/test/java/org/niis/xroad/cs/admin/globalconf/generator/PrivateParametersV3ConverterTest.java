package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ConfigurationAnchorType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ConfigurationSourceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.ManagementServiceType;
import ee.ria.xroad.common.conf.globalconf.privateparameters.v3.PrivateParametersType;
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PrivateParametersV3ConverterTest {

    private static final String INSTANCE = "INSTANCE";
    private static final String DOWNLOAD_URL = "http://example.com";
    private static final byte[] VERIFICATION_CERT = "VERIFICATION_CERT".getBytes(UTF_8);
    private static final byte[] AUTH_CERT_REG_SERVICE_CERT = "authCertRegServiceCert".getBytes(UTF_8);
    private static final String OTHER_INSTANCE = "OTHER_INSTANCE";
    private static final ClientId
            SERVICE_PROVIDER_ID = ClientId.Conf.create(INSTANCE, "MEMBER-CLASS", "SERVICE-PROVIDER");
    private static final String AUTH_CERT_REG_SERVICE_ADDRESS = "http://getAuthCertReg.example.com";
    private static final Integer TIMESTAMPING_INTERVAL = 123;

    @Test
    void shouldMapAllProperties() {
        PrivateParameters privateParameters = getPrivateParameters();

        var xmlType = PrivateParametersV3Converter.INSTANCE.convert(privateParameters);

        assertPrivateParameters(xmlType);
    }

    private static PrivateParameters getPrivateParameters() {
        getConfigurationAnchor();

        PrivateParameters privateParameters = new PrivateParameters();
        privateParameters.setInstanceIdentifier(INSTANCE);
        privateParameters.setConfigurationAnchors(List.of(getConfigurationAnchor()));
        privateParameters.setManagementService(getManagementService());
        privateParameters.setTimeStampingIntervalSeconds(TIMESTAMPING_INTERVAL);
        return privateParameters;
    }

    private static void assertPrivateParameters(PrivateParametersType privateParameters) {
        assertAll(
                () -> assertThat(privateParameters).isNotNull(),
                () -> assertThat(privateParameters.getInstanceIdentifier()).isEqualTo(INSTANCE),
                () -> assertThat(privateParameters.getConfigurationAnchor())
                        .satisfiesExactly(PrivateParametersV3ConverterTest::assertConfigurationAnchor),
                () -> assertThat(privateParameters.getManagementService()).satisfies(
                        PrivateParametersV3ConverterTest::assertManagementService),
                () -> assertThat(privateParameters.getTimeStampingIntervalSeconds()).isEqualTo(BigInteger.valueOf(TIMESTAMPING_INTERVAL)),
                () -> assertThat(privateParameters).hasNoNullFieldsOrProperties()
        );
    }

    private static PrivateParameters.ConfigurationAnchor getConfigurationAnchor() {
        PrivateParameters.ConfigurationSource source = getConfigurationSource();
        PrivateParameters.ConfigurationAnchor anchor = new PrivateParameters.ConfigurationAnchor();
        anchor.setGeneratedAt(Instant.EPOCH);
        anchor.setInstanceIdentifier(OTHER_INSTANCE);
        anchor.setSources(List.of(source));
        return anchor;
    }

    private static void assertConfigurationAnchor(ConfigurationAnchorType anchor) {
        assertAll(
                () -> assertThat(anchor.getInstanceIdentifier()).isEqualTo(OTHER_INSTANCE),
                () -> assertThat(anchor.getGeneratedAt().toGregorianCalendar().toInstant()).isEqualTo(Instant.EPOCH),
                () -> assertThat(anchor.getSource())
                        .satisfiesExactly(PrivateParametersV3ConverterTest::assertConfigurationSource),
                () -> assertThat(anchor).hasNoNullFieldsOrProperties()
        );
    }

    private static PrivateParameters.ConfigurationSource getConfigurationSource() {
        var source = new PrivateParameters.ConfigurationSource();
        source.setDownloadURL(DOWNLOAD_URL);
        source.setVerificationCerts(List.of(VERIFICATION_CERT));
        return source;
    }

    private static void assertConfigurationSource(ConfigurationSourceType source) {
        assertAll(
                () -> assertThat(source.getDownloadURL()).isEqualTo(DOWNLOAD_URL),
                () -> assertThat(source.getVerificationCert()).containsExactly(VERIFICATION_CERT),
                () -> assertThat(source).hasNoNullFieldsOrProperties());
    }

    private static PrivateParameters.ManagementService getManagementService() {
        var managementService = new PrivateParameters.ManagementService();
        managementService.setManagementRequestServiceProviderId(SERVICE_PROVIDER_ID);
        managementService.setAuthCertRegServiceAddress(AUTH_CERT_REG_SERVICE_ADDRESS);
        managementService.setAuthCertRegServiceCert(AUTH_CERT_REG_SERVICE_CERT);
        return managementService;
    }

    private static void assertManagementService(ManagementServiceType managementService) {
        assertAll(
                () -> assertThat(managementService.getManagementRequestServiceProviderId()).isEqualTo(SERVICE_PROVIDER_ID),
                () -> assertThat(managementService.getAuthCertRegServiceCert()).isEqualTo(AUTH_CERT_REG_SERVICE_CERT),
                () -> assertThat(managementService.getAuthCertRegServiceAddress()).isEqualTo(AUTH_CERT_REG_SERVICE_ADDRESS),
                () -> assertThat(managementService).hasNoNullFieldsOrProperties());
    }
}
