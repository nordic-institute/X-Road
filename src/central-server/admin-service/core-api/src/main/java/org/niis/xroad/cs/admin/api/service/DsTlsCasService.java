/*
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

import org.niis.xroad.cs.admin.api.dto.AddDsTlsCaRequest;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaIntermediateCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaListItem;

import java.util.List;

/**
 * Manages the {@code approvedDsTlsCa} globalconf list: designated CAs trusted for dataspace TLS server
 * certificates. Own persistence, fully separate from the approved (member) CAs managed by
 * {@link CertificationServicesService}; never consulted by member-cert validation.
 */
public interface DsTlsCasService {

    DsTlsCa add(AddDsTlsCaRequest request);

    DsTlsCa get(Integer id);

    void delete(Integer id);

    DsTlsCa update(DsTlsCa dsTlsCa);

    List<DsTlsCa> findAll();

    List<DsTlsCaListItem> getDsTlsCas();

    CertificateDetails getCertificateDetails(Integer id);

    DsTlsCaIntermediateCa addIntermediateCa(Integer dsTlsCaId, byte[] cert);

    List<DsTlsCaIntermediateCa> getIntermediateCas(Integer dsTlsCaId);

    DsTlsCaIntermediateCa getIntermediateCa(Integer intermediateCaId);

    void deleteIntermediateCa(Integer intermediateCaId);
}
