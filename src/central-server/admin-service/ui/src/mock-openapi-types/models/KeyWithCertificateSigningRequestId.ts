/* istanbul ignore file */
/* tslint:disable */

import type { Key } from './Key';

/**
 * Key and TokenCertificateSigningRequest id
 */
export type KeyWithCertificateSigningRequestId = {
  key: Key;
  /**
   * CSR id
   */
  csr_id: string;
};
