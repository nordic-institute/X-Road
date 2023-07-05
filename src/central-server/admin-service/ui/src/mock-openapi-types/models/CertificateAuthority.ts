/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

import type { CertificateAuthorityOcspResponse } from './CertificateAuthorityOcspResponse';

/**
 * approved certificate authority information. Only for top CAs.
 */
export type CertificateAuthority = {
    /**
     * name of the CA, as defined in global conf. Used also as an identifier
     */
    name: string;
    /**
     * subject distinguished name
     */
    subject_distinguished_name: string;
    /**
     * issuer distinguished name
     */
    issuer_distinguished_name: string;
    ocsp_response: CertificateAuthorityOcspResponse;
    /**
     * certificate authority expires at
     */
    not_after: string;
    /**
     * if the certificate authority is top CA (instead of intermediate)
     */
    top_ca: boolean;
    /**
     * encoded path string from this CA to top CA
     */
    path: string;
    /**
     * if certificate authority is limited for authentication use only
     */
    authentication_only: boolean;
}
