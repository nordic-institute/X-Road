/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { DiagnosticStatusClass } from './DiagnosticStatusClass';
import type { OcspStatus } from './OcspStatus';

/**
 * OCSP responder diagnostics
 */
export interface OcspResponder {
    /**
     * url of the OCSP responder
     */
    readonly url: string;
    readonly status_class: any;
    readonly status_code: any;
    /**
     * last time updated
     */
    readonly prev_update_at?: string;
    /**
     * next time updated
     */
    readonly next_update_at: string;
}
