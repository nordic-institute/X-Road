/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CsrFormat } from './CsrFormat';
import type { KeyUsageType } from './KeyUsageType';

/**
 * request to generate a CSR
 */
export type CsrGenerate = {
    key_usage_type: KeyUsageType;
    /**
     * common name of the CA
     */
    ca_name: string;
    csr_format: CsrFormat;
    /**
     * member client id for signing CSRs. <instance_id>:<member_class>:<member_code>
     */
    member_id?: string;
    /**
     * user-provided values for subject DN parameters
     */
    subject_field_values: Record<string, string>;
}
