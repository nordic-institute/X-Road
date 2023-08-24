/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * certificate status
 */
export enum CertificateOcspStatus {
  DISABLED = 'DISABLED',
  EXPIRED = 'EXPIRED',
  OCSP_RESPONSE_UNKNOWN = 'OCSP_RESPONSE_UNKNOWN',
  OCSP_RESPONSE_GOOD = 'OCSP_RESPONSE_GOOD',
  OCSP_RESPONSE_SUSPENDED = 'OCSP_RESPONSE_SUSPENDED',
  OCSP_RESPONSE_REVOKED = 'OCSP_RESPONSE_REVOKED',
}
