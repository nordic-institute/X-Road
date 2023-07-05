/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { DiagnosticStatusClass } from './DiagnosticStatusClass';
import type { OcspStatus } from './OcspStatus';

/**
 * OCSP responder diagnostics
 */
export type OcspResponder = {
    /**
     * url of the OCSP responder
     */
    readonly url: string;
    readonly status_class: DiagnosticStatusClass;
    readonly status_code: OcspStatus;
    /**
     * last time updated
     */
    readonly prev_update_at?: string;
    /**
     * next time updated
     */
    readonly next_update_at: string;
}
