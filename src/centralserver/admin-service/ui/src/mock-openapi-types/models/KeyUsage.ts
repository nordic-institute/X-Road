/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * certificate key usage
 */
export enum KeyUsage {
    DIGITAL_SIGNATURE = 'DIGITAL_SIGNATURE',
    NON_REPUDIATION = 'NON_REPUDIATION',
    KEY_ENCIPHERMENT = 'KEY_ENCIPHERMENT',
    DATA_ENCIPHERMENT = 'DATA_ENCIPHERMENT',
    KEY_AGREEMENT = 'KEY_AGREEMENT',
    KEY_CERT_SIGN = 'KEY_CERT_SIGN',
    CRL_SIGN = 'CRL_SIGN',
    ENCIPHER_ONLY = 'ENCIPHER_ONLY',
    DECIPHER_ONLY = 'DECIPHER_ONLY',
}