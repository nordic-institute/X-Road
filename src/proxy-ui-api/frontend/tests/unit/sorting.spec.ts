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

import * as Sorting from '@/views/KeysAndCertificates/SignAndAuthKeys/keyColumnSorting';
import { CertificateOcspStatus } from '@/openapi-types';
import { KeysSortColumn } from '@/views/KeysAndCertificates/SignAndAuthKeys/keyColumnSorting';

// Simplified mock version of Keys
const keysUnsorted: any[] = [
  {
    id: '2',
    name: '2',
    label: '',

    certificates: [
      {
        ocsp_status: CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED,
        owner_id: '1',
        status: '1',
        certificate_details: {
          issuer_common_name: 'Cert 1',
          serial: '1',
          not_after: '1',
        },
      },
      {
        ocsp_status: CertificateOcspStatus.EXPIRED,
        owner_id: '2',
        status: '2',
        certificate_details: {
          issuer_common_name: 'Cert 2',
          serial: '2',
          not_after: '2',
        },
      },
      {
        ocsp_status: CertificateOcspStatus.DISABLED,
        owner_id: '0',
        status: '0',
        certificate_details: {
          issuer_common_name: 'Cert 0',
          serial: '0',
          not_after: '0',
        },
      },
    ],
    certificate_signing_requests: [
      {
        id: '0',
      },
      {
        id: '2',
      },
      {
        id: '1',
      },
    ],
  },
  {
    id: '1',
    name: '1',
    label: '',
    certificates: [],
    certificate_signing_requests: [],
  },
  {
    id: '0',
    name: '0',
    label: '',
    certificates: [],
    certificate_signing_requests: [],
  },
];

describe('sorting functions', () => {
  // REST URL can be http or https
  it('Sorting of keys, certificates and CSR:s', () => {
    let sortedKeys = Sorting.keyArraySort(
      keysUnsorted,
      KeysSortColumn.NAME,
      true,
    );

    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.NAME, true);
    expect(sortedKeys[0].id).toEqual('0');
    expect(sortedKeys[1].id).toEqual('1');
    expect(sortedKeys[2].id).toEqual('2');

    // Test sorting of certificates
    expect(sortedKeys[2].certificates[0].certificate_details.serial).toEqual(
      '0',
    );
    expect(sortedKeys[2].certificates[1].certificate_details.serial).toEqual(
      '1',
    );
    expect(sortedKeys[2].certificates[2].certificate_details.serial).toEqual(
      '2',
    );

    //sortedKeys = Sorting.sortKeysByNameDesc(keysUnsorted);
    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.NAME, false);

    expect(sortedKeys[0].id).toEqual('2');
    expect(sortedKeys[1].id).toEqual('1');
    expect(sortedKeys[2].id).toEqual('0');

    // Test sorting of certificates
    expect(sortedKeys[0].certificates[0].certificate_details.serial).toEqual(
      '2',
    );
    expect(sortedKeys[0].certificates[1].certificate_details.serial).toEqual(
      '1',
    );
    expect(sortedKeys[0].certificates[2].certificate_details.serial).toEqual(
      '0',
    );

    // Sorting by ID
    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.ID, true);
    // Test sorting of certificates
    expect(sortedKeys[0].certificates[0].owner_id).toEqual('0');
    expect(sortedKeys[0].certificates[2].owner_id).toEqual('2');

    expect(sortedKeys[0].certificate_signing_requests[0].id).toEqual('0');
    expect(sortedKeys[0].certificate_signing_requests[2].id).toEqual('2');

    // ID
    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.ID, false);
    // Sorting of certificates
    expect(sortedKeys[0].certificates[0].owner_id).toEqual('2');
    expect(sortedKeys[0].certificates[2].owner_id).toEqual('0');
    // Sorting of CSR:s
    expect(sortedKeys[0].certificate_signing_requests[0].id).toEqual('2');
    expect(sortedKeys[0].certificate_signing_requests[2].id).toEqual('0');

    // Expiration
    sortedKeys = Sorting.keyArraySort(
      keysUnsorted,
      KeysSortColumn.EXPIRATION,
      true,
    );

    expect(sortedKeys[0].certificates[0].certificate_details.not_after).toEqual(
      '0',
    );
    expect(sortedKeys[0].certificates[2].certificate_details.not_after).toEqual(
      '2',
    );

    sortedKeys = Sorting.keyArraySort(
      keysUnsorted,
      KeysSortColumn.EXPIRATION,
      false,
    );

    expect(sortedKeys[0].certificates[0].certificate_details.not_after).toEqual(
      '2',
    );
    expect(sortedKeys[0].certificates[2].certificate_details.not_after).toEqual(
      '0',
    );

    // OCSP
    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.OCSP, true);

    expect(sortedKeys[0].certificates[0].ocsp_status).toEqual(
      CertificateOcspStatus.DISABLED,
    );
    expect(sortedKeys[0].certificates[2].ocsp_status).toEqual(
      CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED,
    );

    sortedKeys = Sorting.keyArraySort(keysUnsorted, KeysSortColumn.OCSP, false);

    expect(sortedKeys[0].certificates[0].ocsp_status).toEqual(
      CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED,
    );
    expect(sortedKeys[0].certificates[2].ocsp_status).toEqual(
      CertificateOcspStatus.DISABLED,
    );

    // Status
    sortedKeys = Sorting.keyArraySort(
      keysUnsorted,
      KeysSortColumn.STATUS,
      true,
    );

    expect(sortedKeys[0].certificates[0].status).toEqual('0');
    expect(sortedKeys[0].certificates[2].status).toEqual('2');

    sortedKeys = Sorting.keyArraySort(
      keysUnsorted,
      KeysSortColumn.STATUS,
      false,
    );

    expect(sortedKeys[0].certificates[0].status).toEqual('2');
    expect(sortedKeys[0].certificates[2].status).toEqual('0');
  });
});
