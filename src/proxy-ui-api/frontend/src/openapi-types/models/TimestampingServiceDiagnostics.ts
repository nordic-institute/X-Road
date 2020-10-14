/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DiagnosticStatusClass } from './DiagnosticStatusClass';
import type { TimestampingStatus } from './TimestampingStatus';

/**
 * timestamping service diagnostics
 */
export interface TimestampingServiceDiagnostics {
    /**
     * url of the time stamping service
     */
    readonly url: string;
    readonly status_class: any;
    readonly status_code: any;
    /**
     * last time updated
     */
    readonly prev_update_at: string;
}
