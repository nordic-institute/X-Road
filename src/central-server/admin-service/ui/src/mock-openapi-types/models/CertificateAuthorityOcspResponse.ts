/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

/**
 * certificate authority OCSP status
 */
export enum CertificateAuthorityOcspResponse {
  NOT_AVAILABLE = 'NOT_AVAILABLE',
  OCSP_RESPONSE_UNKNOWN = 'OCSP_RESPONSE_UNKNOWN',
  OCSP_RESPONSE_GOOD = 'OCSP_RESPONSE_GOOD',
  OCSP_RESPONSE_SUSPENDED = 'OCSP_RESPONSE_SUSPENDED',
  OCSP_RESPONSE_REVOKED = 'OCSP_RESPONSE_REVOKED',
}
