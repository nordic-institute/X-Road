package ee.ria.xroad.common.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "xroad.common.rpc")
public class SpringCommonRpcProperties implements CommonRpcProperties {

    private boolean useTls;
    private CertificateProvisionPropertiesImpl certificateProvisioning;

    @Override
    public boolean useTls() {
        return useTls;
    }

    @Override
    public CertificateProvisionProperties certificateProvisioning() {
        return certificateProvisioning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ConfigurationProperties(prefix = "xroad.common.rpc.certificate-provisioning")
    public static class CertificateProvisionPropertiesImpl
            implements CertificateProvisionProperties {

        private String issuanceRoleName;
        private String commonName;
        private List<String> altNames;
        private List<String> ipSubjectAltNames;
        private int ttlMinutes;
        private int refreshIntervalMinutes;
        private String secretStorePkiPath;

        @Override
        public String issuanceRoleName() {
            return issuanceRoleName;
        }

        @Override
        public String commonName() {
            return commonName;
        }

        @Override
        public List<String> altNames() {
            return altNames;
        }

        @Override
        public List<String> ipSubjectAltNames() {
            return ipSubjectAltNames;
        }

        @Override
        public int ttlMinutes() {
            return ttlMinutes;
        }

        @Override
        public int refreshIntervalMinutes() {
            return refreshIntervalMinutes;
        }

        @Override
        public String secretStorePkiPath() {
            return secretStorePkiPath;
        }
    }
}
