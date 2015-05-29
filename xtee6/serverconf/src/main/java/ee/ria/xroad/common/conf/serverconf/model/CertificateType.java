package ee.ria.xroad.common.conf.serverconf.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Certificate.
 */
@Getter
@Setter
public class CertificateType {

    private Long id;

    private byte[] data;
}
