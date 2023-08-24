/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { ConfigurationStatus } from './ConfigurationStatus';
import type { DiagnosticStatusClass } from './DiagnosticStatusClass';

/**
 * global configuration diagnostics
 */
export type GlobalConfDiagnostics = {
  readonly status_class: DiagnosticStatusClass;
  readonly status_code: ConfigurationStatus;
  /**
   * last time updated
   */
  readonly prev_update_at: string;
  /**
   * last time updated
   */
  readonly next_update_at: string;
};
