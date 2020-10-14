/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ConfigurationStatus } from './ConfigurationStatus';
import type { DiagnosticStatusClass } from './DiagnosticStatusClass';

/**
 * global configuration diagnostics
 */
export interface GlobalConfDiagnostics {
    readonly status_class: any;
    readonly status_code: any;
    /**
     * last time updated
     */
    readonly prev_update_at: string;
    /**
     * last time updated
     */
    readonly next_update_at: string;
}
