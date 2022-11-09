/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * whether a token has been initialized or not – if the software token init status cannot be resolved (e.g. signer module is offline), the value is UNKNOWN
 */
export enum TokenInitStatus {
    INITIALIZED = 'INITIALIZED',
    NOT_INITIALIZED = 'NOT_INITIALIZED',
    UNKNOWN = 'UNKNOWN',
}