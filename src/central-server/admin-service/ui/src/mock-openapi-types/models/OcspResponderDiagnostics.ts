/* istanbul ignore file */
/* tslint:disable */

import type { OcspResponder } from './OcspResponder';

/**
 * Ocsp responder diagnostics
 */
export type OcspResponderDiagnostics = {
  /**
   * CA distinguished name
   */
  readonly distinguished_name: string;
  readonly ocsp_responders: Array<OcspResponder>;
};
