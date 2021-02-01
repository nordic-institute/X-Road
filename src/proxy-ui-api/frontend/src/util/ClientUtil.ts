import {
  CertificateOcspStatus,
  Key,
  KeyUsageType,
  Token,
  TokenCertificate,
} from '@/openapi-types';

/**
 * Return true if tokens list contain any certificate with good ocsp response status with memberName matching owner_id
 *
 * @param instanceId
 * @param memberClass
 * @param memberCode
 * @param tokens
 */
export const memberHasValidSignCert = (
  memberName: string,
  tokens: Token[],
): boolean => {
  const filterSignKeys = (key: Key) => key.usage === KeyUsageType.SIGNING;
  return tokens
    .flatMap((token: Token) => token.keys.filter(filterSignKeys))
    .flatMap((key: Key) => key.certificates)
    .some((certificate: TokenCertificate) => {
      return (
        certificate.owner_id === memberName &&
        certificate.ocsp_status === CertificateOcspStatus.OCSP_RESPONSE_GOOD
      );
    });
};
