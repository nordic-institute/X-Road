/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { Key } from './Key';
import type { KeyValuePair } from './KeyValuePair';
import type { PossibleActions } from './PossibleActions';
import type { TokenStatus } from './TokenStatus';
import type { TokenType } from './TokenType';

/**
 * Token. Also includes the possible actions that can be done to this object, e.g DELETE (only for token related operations and does not consider user authorization).
 */
export type Token = {
    /**
     * token id
     */
    readonly id: string;
    /**
     * token name
     */
    name: string;
    type: TokenType;
    /**
     * token keys
     */
    keys: Array<Key>;
    status: TokenStatus;
    /**
     * if the token has been logged in to
     */
    logged_in: boolean;
    /**
     * if the token is available
     */
    available: boolean;
    /**
     * if the token is saved to configuration
     */
    saved_to_configuration: boolean;
    /**
     * if the token is read-only
     */
    read_only: boolean;
    /**
     * serial number of the token
     */
    serial_number?: string;
    /**
     * Contains label-value pairs of information
     */
    token_infos?: Array<KeyValuePair>;
    possible_actions?: PossibleActions;
}
