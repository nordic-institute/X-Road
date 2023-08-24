/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CodeWithDetails } from './CodeWithDetails';

/**
 * object returned in error cases
 */
export type ErrorInfo = {
  /**
   * http status code
   */
  status: number;
  error?: CodeWithDetails;
  /**
   * warnings that could be ignored
   */
  warnings?: Array<CodeWithDetails>;
};
