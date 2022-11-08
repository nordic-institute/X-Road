/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { PossibleActions } from './PossibleActions';

/**
 * CSR for certificate that is stored in a Token. Also includes the possible actions that can be done to this object, e.g DELETE (only for csr related operations and does not consider user authorization).
 */
export type TokenCertificateSigningRequest = {
    /**
     * CSR id
     */
    readonly id: string;
    /**
     * client id of the owner member, <instance_id>:<member_class>:<member_code>
     */
    readonly owner_id: string;
    possible_actions: PossibleActions;
}
