/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
