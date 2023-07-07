/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * an action to change state or edit token, key, cert or csr
 */
export enum PossibleAction {
    DELETE = 'DELETE',
    ACTIVATE = 'ACTIVATE',
    DISABLE = 'DISABLE',
    LOGIN = 'LOGIN',
    LOGOUT = 'LOGOUT',
    REGISTER = 'REGISTER',
    UNREGISTER = 'UNREGISTER',
    IMPORT_FROM_TOKEN = 'IMPORT_FROM_TOKEN',
    GENERATE_KEY = 'GENERATE_KEY',
    EDIT_FRIENDLY_NAME = 'EDIT_FRIENDLY_NAME',
    GENERATE_AUTH_CSR = 'GENERATE_AUTH_CSR',
    GENERATE_SIGN_CSR = 'GENERATE_SIGN_CSR',
    TOKEN_CHANGE_PIN = 'TOKEN_CHANGE_PIN',
}