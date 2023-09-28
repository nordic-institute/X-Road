/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { DiagnosticStatusClass } from './DiagnosticStatusClass';
import type { TimestampingStatus } from './TimestampingStatus';

/**
 * timestamping service diagnostics
 */
export type TimestampingServiceDiagnostics = {
  /**
   * url of the time stamping service
   */
  readonly url: string;
  readonly status_class: DiagnosticStatusClass;
  readonly status_code: TimestampingStatus;
  /**
   * last time updated
   */
  readonly prev_update_at: string;
};
