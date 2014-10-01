package ee.cyber.sdsb.common.conf.serverconf.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GlobalConfDistributorType {

    private Long id;

    private String url;

    private CertificateType verificationCert;
}
