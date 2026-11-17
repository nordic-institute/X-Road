/*
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.core.dataspace;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.TrustedIssuer;
import org.niis.xroad.cs.admin.api.dto.TrustedIssuerRemoval;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.core.entity.IssuerDidEntity;
import org.niis.xroad.cs.admin.core.repository.IssuerDidRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TRUSTED_ISSUER_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
public class DataspaceIssuerDidServiceImpl implements DataspaceIssuerDidService {

    private final IssuerDidRepository issuerDidRepository;

    @Override
    public void register(String issuerDid, String host, int didPort, String issuanceUrl, String statuslistBaseUrl) {
        issuerDidRepository.insert(issuerDid, host, didPort, issuanceUrl, statuslistBaseUrl);
    }

    @Override
    public List<String> findAll() {
        return issuerDidRepository.findAll().stream()
                .map(IssuerDidEntity::getDid)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public List<TrustedIssuer> findAllTrusted() {
        return issuerDidRepository.findAllTrusted().stream()
                .map(entity -> new TrustedIssuer(entity.getId(), entity.getDid(), entity.getCreatedAt()))
                .toList();
    }

    @Override
    public TrustedIssuerRemoval remove(int id) {
        List<TrustedIssuer> trusted = findAllTrusted();
        boolean isTrusted = trusted.stream().anyMatch(issuer -> issuer.id() == id);
        if (!isTrusted || !issuerDidRepository.markRemoved(id)) {
            throw new NotFoundException(TRUSTED_ISSUER_NOT_FOUND.build());
        }
        String did = trusted.stream()
                .filter(issuer -> issuer.id() == id)
                .findFirst()
                .orElseThrow()
                .did();
        return new TrustedIssuerRemoval(did, trusted.size() == 1);
    }
}
