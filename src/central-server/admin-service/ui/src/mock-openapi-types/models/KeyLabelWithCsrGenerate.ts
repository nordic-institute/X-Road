/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CsrGenerate } from './CsrGenerate';

export type KeyLabelWithCsrGenerate = {
  /**
   * label for the new key
   */
  key_label: string;
  csr_generate_request: CsrGenerate;
};
