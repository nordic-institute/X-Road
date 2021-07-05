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
      return sortKeysByName(keys, sortDirection);
    case KeysSortColumn.ID:
      return sortKeysById(keys, sortDirection);
    case KeysSortColumn.OCSP:
      return sortCertificatesForKeys(keys, sortDirection, 'ocsp_status');
    case KeysSortColumn.EXPIRATION:
      return sortKeysByDate(keys, sortDirection);
    case KeysSortColumn.STATUS:
      return sortCertificatesForKeys(keys, sortDirection, 'status');
    default:
      break;
  }
  return keys;
};

/**
 * Sort array of keys by name ascending
 */
export const sortKeysArrayByName = (keys: Key[]): Key[] => {
  return keys.sort((a: Key, b: Key) => {
    // Value that is shown as name in the UI can be a 'name' or 'id'
    // Check if they exist and which one to compare
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
};

/**
 * Sort keys by name Ascending
 */
export const sortKeysByName = (keys: Key[], sortDirection: boolean): Key[] => {
  let temp = sortKeysArrayByName(keys);

  if (!sortDirection) {
    temp = temp.reverse();
  }

  temp.forEach((key: Key) => {
    if (key.certificates) {
      sortDirection
        ? (key.certificates = sortCertsByNameAsc(key.certificates))
        : (key.certificates = sortCertsByNameAsc(key.certificates).reverse());
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
 * Sort certificates using given functions
 */
export const sortCertificatesForKeys = (
  keys: Key[],
  sortDirection: boolean,
  sortingValue: string,
): Key[] => {
  const temp = keys;

  temp.forEach((key: Key) => {
    if (key.certificates) {
      if (sortDirection) {
        key.certificates = sortCertsAsc(key.certificates, sortingValue);
      } else {
        key.certificates = sortCertsAsc(
          key.certificates,
          sortingValue,
        ).reverse();
      }
    }
  });

  return temp;
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
        key.certificates = sortCertsByDateAsc(key.certificates).reverse();
      }
    }
  });

  return temp;
};

export const sortCertsAsc = (
  certs: TokenCertificate[],
  sortingValue: string,
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (a as any)[sortingValue].localeCompare((b as any)[sortingValue]);
  });
};

/**
 * Sort certificates by date Ascending
 */
export const sortCertsByDateAsc = (
  certs: TokenCertificate[],
): TokenCertificate[] => {
  return certs.sort((a: TokenCertificate, b: TokenCertificate) => {
    return a.certificate_details.not_after.localeCompare(
      b.certificate_details.not_after,
    );
  });
};

/**
 * Sort keys including certificates and csr:s by id
 */
export const sortKeysById = (keys: Key[], sortDirection: boolean): Key[] => {
  const temp = sortCertificatesForKeys(keys, sortDirection, 'owner_id');

  temp.forEach((key: Key) => {
    const sortedRequests = sortRequestsAsc(key.certificate_signing_requests);
    key.certificate_signing_requests = sortDirection
      ? sortedRequests
      : sortedRequests.reverse();
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
