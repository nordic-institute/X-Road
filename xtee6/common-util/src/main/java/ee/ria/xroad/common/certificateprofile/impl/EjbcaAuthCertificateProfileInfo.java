package ee.ria.xroad.common.certificateprofile.impl;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;

/**
 * Default implementation (EJBCA) of AuthCertificateProfileInfo.
 */
public class EjbcaAuthCertificateProfileInfo
        extends AbstractCertificateProfileInfo
        implements AuthCertificateProfileInfo {

    /**
     * Constructor.
     * @param params the parameters
     */
    public EjbcaAuthCertificateProfileInfo(Parameters params) {
        super(new DnFieldDescription[] {
                // Instance identifier
                new DnFieldDescriptionImpl("C", "Instance Identifier (C)",
                    params.getServerId().getXRoadInstance()
                ).setReadOnly(true),

                // Server code
                new DnFieldDescriptionImpl("CN", "Server Code (CN)",
                    params.getServerId().getServerCode()
                ).setReadOnly(true)
            }
        );
    }
}
