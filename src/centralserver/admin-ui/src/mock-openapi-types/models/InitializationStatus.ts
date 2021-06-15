/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { TokenInitStatus } from './TokenInitStatus';

/**
 * Initialization status of the Security Server
 */
export type InitializationStatus = {
    /**
     * whether a configuration anchor has been imported or not
     */
    is_anchor_imported: boolean;
    /**
     * whether the server code of the security server has been initialized or not
     */
    is_server_code_initialized: boolean;
    /**
     * whether the server owner of the security server has been initialized or not
     */
    is_server_owner_initialized: boolean;
    software_token_init_status: TokenInitStatus;
}
