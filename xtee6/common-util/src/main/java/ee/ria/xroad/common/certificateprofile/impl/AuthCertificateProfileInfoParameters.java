package ee.ria.xroad.common.certificateprofile.impl;

import lombok.Data;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.identifier.SecurityServerId;

/**
 * Default implementation of {@link AuthCertificateProfileInfo.Parameters}.
 */
@Data
public class AuthCertificateProfileInfoParameters
        implements AuthCertificateProfileInfo.Parameters {

    private final SecurityServerId serverId;
    private final String memberName;

}
