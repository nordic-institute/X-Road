package ee.ria.xroad.common.certificateprofile.impl;

import lombok.Data;

import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Default implementation of {@link SignCertificateProfileInfo.Parameters}.
 */
@Data
public class SignCertificateProfileInfoParameters
        implements SignCertificateProfileInfo.Parameters {

    private final ClientId clientId;
    private final String memberName;

}
