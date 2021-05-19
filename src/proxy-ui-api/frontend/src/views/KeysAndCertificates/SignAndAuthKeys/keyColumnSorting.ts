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
  Key,
  TokenCertificate,
  TokenCertificateSigningRequest,
} from '@/openapi-types';

// Keys sort columns for keys and certificates view
export enum KeysSortColumn {
  NAME = 'NAME',
  ID = 'ID',
  OCSP = 'OCSP',
  EXPIRATION = 'EXPIRATION',
  STATUS = 'STATUS',
}

/**
 * Main sorting function for keys (with certificates and csr:s)
 */
export const keyArraySort = (
  keys: Key[],
  sortType: string,
  sortDirection: boolean,
): Key[] => {
  switch (sortType) {
    case KeysSortColumn.NAME:
      return sortDirection ? sortKeysByNameAsc(keys) : sortKeysByNameDesc(keys);
    case KeysSortColumn.ID:
      return sortKeysById(keys, sortDirection);
    case KeysSortColumn.OCSP:
      return sortCertificatesForKeys(
        keys,
        sortDirection,
        sortCertsByOcspAsc,
        sortCertsByOcspDesc,
      );
    case KeysSortColumn.EXPIRATION:
      return sortKeysByDate(keys, sortDirection);
    case KeysSortColumn.STATUS:
      return sortCertificatesForKeys(
        keys,
        sortDirection,
        sortCertsByStatusAsc,
        sortCertsByStatusDesc,
      );
    default:
      break;
  }
  return keys;
};

/**
 * Sort keys by name Ascending
 */
export const sortKeysByNameAsc = (keys: Key[]): Key[] => {
  const temp = keys.sort((a: Key, b: Key) => {
    let aComparable;
    let bComparable;

    if (a.name && a.name !== '') {
      aComparable = a.name;
    } else {
      aComparable = a.id;
    }

    if (b.name && b.name !== '') {
      bComparable = b.name;
    } else {
      bComparable = b.id;
    }

    return aComparable.localeCompare(bComparable);
  });

  temp.forEach((key: Key) => {
    if (key.certificates) {
      key.certificates = sortCertsByNameAsc(key.certificates);
    }
  });

  return temp;
};

/**
 * Sort keys by name Descending
 */
export const sortKeysByNameDesc = (keys: Key[]): Key[] => {
  const temp = keys.sort((b: Key, a: Key) => {
    let aComparable;
    let bComparable;

    if (a.name && a.name !== '') {
      aComparable = a.name;
    } else {
      aComparable = a.id;
    }

    if (b.name && b.name !== '') {
      bComparable = b.name;
    } else {
      bComparable = b.id;
    }

    return aComparable.localeCompare(bComparable);
  });

  temp.forEach((key: Key) => {
    if (key.certificates) {
      key.certificates = sortCertsByNameDesc(key.certificates);
    }
  });

  return temp;
};

/**
 * Sort certificates by name Ascending
 */
export const sortCertsByNameAsc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return (
      a.certificate_details.issuer_common_name + a.certificate_details.serial
    ).localeCompare(
      b.certificate_details.issuer_common_name + b.certificate_details.serial,
    );
  });
};

/**
 * Sort certificates by name Descending
 */
export const sortCertsByNameDesc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((b: TokenCertificate, a: TokenCertificate) => {
    return (
      a.certificate_details.issuer_common_name + a.certificate_details.serial
    ).localeCompare(
      b.certificate_details.issuer_common_name + b.certificate_details.serial,
    );
  });
};

// Type for certificate sorting functions
type CertificateSortFnType = (keys: TokenCertificate[]) => TokenCertificate[];

/**
 * Sort certificates using given functions
 */
export const sortCertificatesForKeys = (
  keys: Key[],
  sortDirection: boolean,
  sortAsc: CertificateSortFnType,
  sortDesc: CertificateSortFnType,
): Key[] => {
  const temp = keys;

  temp.forEach((key: Key) => {
    if (key.certificates) {
      if (sortDirection) {
        key.certificates = sortAsc(key.certificates);
      } else {
        key.certificates = sortDesc(key.certificates);
      }
    }
  });

  return temp;
};

/**
 * Sort certificates by OCSP Ascending
 */
export const sortCertsByOcspAsc: CertificateSortFnType = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return a.ocsp_status.localeCompare(b.ocsp_status);
  });
};

/**
 * Sort certificates by OCSP Descending
 */
export const sortCertsByOcspDesc: CertificateSortFnType = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return b.ocsp_status.localeCompare(a.ocsp_status);
  });
};

/**
 * Sort certificates by date
 */
export const sortKeysByDate = (keys: Key[], sortDirection: boolean): Key[] => {
  const temp = keys;

  temp.forEach((key: Key) => {
    if (key.certificates) {
      if (sortDirection) {
        key.certificates = sortCertsByDateAsc(key.certificates);
      } else {
        key.certificates = sortCertsByDateDesc(key.certificates);
      }
    }
  });

  return temp;
};

/**
 * Sort certificates by date Ascending
 */
export const sortCertsByDateAsc: CertificateSortFnType = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return a.certificate_details.not_after.localeCompare(
      b.certificate_details.not_after,
    );
  });
};

/**
 * Sort certificates by date Descending
 */
export const sortCertsByDateDesc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((b: TokenCertificate, a: TokenCertificate) => {
    return a.certificate_details.not_after.localeCompare(
      b.certificate_details.not_after,
    );
  });
};

/**
 * Sort certificates by id Ascendign
 */
export const sortCertsByIdAsc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return a.owner_id.localeCompare(b.owner_id);
  });
};

/**
 * Sort certificates by id Descending
 */
export const sortCertsByIdDesc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return b.owner_id.localeCompare(a.owner_id);
  });
};

/**
 * Sort certificates by status Ascendign
 */
export const sortCertsByStatusAsc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return a.status.localeCompare(b.status);
  });
};

/**
 * Sort certificates by status Descending
 */
export const sortCertsByStatusDesc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return b.status.localeCompare(a.status);
  });
};

/**
 * Sort keys including certificates and csr:s by id
 */
export const sortKeysById = (keys: Key[], sortDirection: boolean): Key[] => {
  const temp = sortCertificatesForKeys(
    keys,
    sortDirection,
    sortCertsByIdAsc,
    sortCertsByIdDesc,
  );

  temp.forEach((key: Key) => {
    if (key.certificate_signing_requests) {
      if (sortDirection) {
        key.certificate_signing_requests = sortRequestsAsc(
          key.certificate_signing_requests,
        );
      } else {
        key.certificate_signing_requests = sortRequestsDesc(
          key.certificate_signing_requests,
        );
      }
    }
  });

  return temp;
};

/**
 * Sort CSR:s by id Ascending
 */
export const sortRequestsAsc = (
  certs: TokenCertificateSigningRequest[],
): TokenCertificateSigningRequest[] => {
  return certs.sort(
    (a: TokenCertificateSigningRequest, b: TokenCertificateSigningRequest) => {
      return a.id.localeCompare(b.id);
    },
  );
};

/**
 * Sort CSR:s by id Descending
 */
export const sortRequestsDesc = (
  certs: TokenCertificateSigningRequest[],
): TokenCertificateSigningRequest[] => {
  return certs.sort(
    (a: TokenCertificateSigningRequest, b: TokenCertificateSigningRequest) => {
      return b.id.localeCompare(a.id);
    },
  );
};
