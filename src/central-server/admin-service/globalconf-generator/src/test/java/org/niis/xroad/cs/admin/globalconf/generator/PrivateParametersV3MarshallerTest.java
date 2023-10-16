package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;

import javax.xml.bind.MarshalException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivateParametersV3MarshallerTest {
    private final ClientId clientId = ClientId.Conf.create("CS", "CLASS", "CODE");

    private final PrivateParametersV3Marshaller marshaller = new PrivateParametersV3Marshaller();

    @Test
    void marshall() {
        final PrivateParameters privateParams = new PrivateParameters();
        privateParams.setInstanceIdentifier("CS");
        privateParams.setManagementService(new PrivateParameters.ManagementService());
        privateParams.getManagementService().setAuthCertRegServiceAddress("https://cs:4001/managementservice/");
        privateParams.getManagementService().setManagementRequestServiceProviderId(clientId);
        privateParams.setTimeStampingIntervalSeconds(60);

        final String result = marshaller.marshall(privateParams);

        assertThat(result).isNotBlank();
    }

    @Test
    void marshallShouldFailWhenInvalid() {
        final PrivateParameters privateParams = new PrivateParameters();
        // missing instance identifier
        privateParams.setManagementService(new PrivateParameters.ManagementService());
        privateParams.getManagementService().setAuthCertRegServiceAddress("https://cs:4001/managementservice/");
        privateParams.getManagementService().setManagementRequestServiceProviderId(clientId);

        assertThrows(MarshalException.class, () -> marshaller.marshall(privateParams));
    }

    @Test
    void marshallShouldFailWhenInvalid2() {
        final PrivateParameters privateParams = new PrivateParameters();
        privateParams.setInstanceIdentifier("CS");
        // missing managementService

        assertThrows(MarshalException.class, () -> marshaller.marshall(privateParams));
    }

    @Test
    void marshallShouldFailWhenInvalid3() {
        final PrivateParameters privateParams = new PrivateParameters();
        privateParams.setInstanceIdentifier("CS");
        privateParams.setManagementService(new PrivateParameters.ManagementService());
        privateParams.getManagementService().setAuthCertRegServiceAddress("https://cs:4001/managementservice/");
        privateParams.getManagementService().setManagementRequestServiceProviderId(clientId);
        // missing TimeStampingIntervalSeconds

        assertThrows(MarshalException.class, () -> marshaller.marshall(privateParams));
    }
}
