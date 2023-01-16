/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { KeyUsageType } from './KeyUsageType';
import type { PossibleActions } from './PossibleActions';
import type { TokenCertificate } from './TokenCertificate';
import type { TokenCertificateSigningRequest } from './TokenCertificateSigningRequest';

/**
 * Key for the certificate. Also includes the possible actions that can be done to this object, e.g DELETE (only for key related operations and does not consider user authorization).
 */
export type Key = {
    /**
     * key id
     */
    readonly id: string;
    /**
     * key name
     */
    name: string;
    /**
     * key label
     */
    label: string;
    /**
     * list of certificates for the key
     */
    certificates: Array<TokenCertificate>;
    /**
     * list of CSRs for the key
     */
    certificate_signing_requests: Array<TokenCertificateSigningRequest>;
    usage?: KeyUsageType;
    /**
     * if the key is available
     */
    available?: boolean;
    /**
     * if the key is saved to configuration
     */
    saved_to_configuration?: boolean;
    possible_actions?: PossibleActions;
}
