/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CertificateDetails } from './CertificateDetails';
import type { CertificateOcspStatus } from './CertificateOcspStatus';
import type { CertificateStatus } from './CertificateStatus';
import type { PossibleActions } from './PossibleActions';

/**
 * Certificate that is stored in a Token (auth or sign cert). Also includes the possible actions that can be done to this object, e.g DELETE (only for cert related operations and does not consider user authorization).
 */
export type TokenCertificate = {
    ocsp_status: CertificateOcspStatus;
    /**
     * client id of the owner member, <instance_id>:<member_class>:<member_code>
     */
    readonly owner_id: string;
    /**
     * if the certificate is active
     */
    active: boolean;
    /**
     * if the certificate is saved to configuration
     */
    saved_to_configuration: boolean;
    certificate_details: CertificateDetails;
    status: CertificateStatus;
    possible_actions?: PossibleActions;
}
