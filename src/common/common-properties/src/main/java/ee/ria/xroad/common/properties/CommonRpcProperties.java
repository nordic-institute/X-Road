package ee.ria.xroad.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

@ConfigurationProperties(prefix = "xroad.common.rpc")
public record CommonRpcProperties(
        boolean useTls,
        @NestedConfigurationProperty
        CertificateProvisionProperties certificateProvisioning
) {
    @ConfigurationProperties(prefix = "xroad.common.rpc.certificate-provisioning")
    public record CertificateProvisionProperties(
            String issuanceRoleName,
            String commonName,
            List<String> altNames,
            List<String> ipSubjectAltNames,
            int ttlMinutes,
            int refreshIntervalMinutes
    ) {
    }
}
