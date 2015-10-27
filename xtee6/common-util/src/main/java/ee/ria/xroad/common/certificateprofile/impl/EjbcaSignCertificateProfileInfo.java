package ee.ria.xroad.common.certificateprofile.impl;

import java.security.cert.X509Certificate;

import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

/**
 * Default implementation (EJBCA) of SignCertificateProfileInfo.
 */
public class EjbcaSignCertificateProfileInfo
        extends AbstractCertificateProfileInfo
        implements SignCertificateProfileInfo {

    protected final Parameters params;

    /**
     * Constructor.
     * @param params the parameters
     */
    public EjbcaSignCertificateProfileInfo(Parameters params) {
        super(new DnFieldDescription[] {
                // Instance identifier
                new DnFieldDescriptionImpl("C", "Instance Identifier (C)",
                    params.getClientId().getXRoadInstance()
                ).setReadOnly(true),

                // Member class
                new DnFieldDescriptionImpl("O", "Member Class (O)",
                    params.getClientId().getMemberClass()
                ).setReadOnly(true),

                // Member code
                new DnFieldDescriptionImpl("CN", "Member Code (CN)",
                    params.getClientId().getMemberCode()
                ).setReadOnly(true)
            }
        );

        this.params = params;
    }
    @Override
    public ClientId getSubjectIdentifier(X509Certificate certificate) {
        return CertUtils.getSubjectClientId(certificate);
    }

}
