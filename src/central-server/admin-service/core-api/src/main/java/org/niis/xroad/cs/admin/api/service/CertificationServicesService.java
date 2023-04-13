/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.api.service;

import org.niis.xroad.cs.admin.api.dto.ApprovedCertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificationServiceListItem;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;

import java.util.List;

public interface CertificationServicesService {
    CertificationService add(ApprovedCertificationService approvedCa);

    CertificationService get(Integer id);

    void delete(Integer id);

    CertificationService update(CertificationService approvedCa);

    List<CertificationService> findAll();

    CertificateDetails getCertificateDetails(Integer id);

    CertificateAuthority addIntermediateCa(Integer certificationServiceId, byte[] cert);

    List<CertificateAuthority> getIntermediateCas(Integer certificationServiceId);

    List<CertificationServiceListItem> getCertificationServices();

    OcspResponder addOcspResponder(OcspResponderAddRequest ocspResponder);

    List<OcspResponder> getOcspResponders(Integer certificationServiceId);
}
