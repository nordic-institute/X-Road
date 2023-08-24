/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

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
