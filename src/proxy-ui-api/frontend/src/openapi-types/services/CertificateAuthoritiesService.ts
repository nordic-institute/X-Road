/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { CertificateAuthority } from '../models/CertificateAuthority';
import type { CsrSubjectFieldDescription } from '../models/CsrSubjectFieldDescription';
import type { KeyUsageType } from '../models/KeyUsageType';
import { request as __request } from '../core/request';

export class CertificateAuthoritiesService {

    /**
     * view the approved certificate authorities
     * Administrator views the approved certificate authorities.
     * @param keyUsageType return only CAs suitable for this type of key usage
     * @param includeIntermediateCas if true, include also intermediate CAs. Otherwise only top CAs are included. Default value is "false".
     * @result CertificateAuthority list of approved certificate authorities
     * @throws ApiError
     */
    public static async getApprovedCertificateAuthorities(
        keyUsageType?: KeyUsageType,
        includeIntermediateCas: boolean = false,
    ): Promise<Array<CertificateAuthority>> {
        const result = await __request({
            method: 'GET',
            path: `/certificate-authorities`,
            query: {
                'key_usage_type': keyUsageType,
                'include_intermediate_cas': includeIntermediateCas,
            },
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

    /**
     * get description of subject DN fields for CSR
     * list DN field descriptions to collect CSR parameters
     * @param caName common name of the CA
     * @param keyUsageType which usage type this CSR is for
     * @param keyId id of the key. If provided, used only for validating correct key usage
     * @param memberId member client id for signing CSRs. <instance_id>:<member_class>:<member_code>
     * @param isNewMember whether or not the member in the member_id parameter is a new member
     * @result CsrSubjectFieldDescription csr subject field objects
     * @throws ApiError
     */
    public static async getSubjectFieldDescriptions(
        caName: string,
        keyUsageType: KeyUsageType,
        keyId?: string,
        memberId?: string,
        isNewMember: boolean = false,
    ): Promise<Array<CsrSubjectFieldDescription>> {
        const result = await __request({
            method: 'GET',
            path: `/certificate-authorities/${caName}/csr-subject-fields`,
            query: {
                'key_usage_type': keyUsageType,
                'key_id': keyId,
                'member_id': memberId,
                'is_new_member': isNewMember,
            },
            errors: {
                400: `request was invalid`,
                401: `authentication credentials are missing`,
                403: `request has been refused`,
                404: `resource requested does not exists`,
                406: `request specified an invalid format`,
                500: `internal server error`,
            },
        });
        return result.body;
    }

}