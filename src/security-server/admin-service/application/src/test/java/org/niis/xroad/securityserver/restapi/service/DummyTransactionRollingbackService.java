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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.repository.LocalGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * LocalGroup service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class DummyTransactionRollingbackService {

    private final LocalGroupRepository localGroupRepository;

    /**
     * LocalGroupService constructor
     *
     * @param localGroupRepository
     */
    @Autowired
    public DummyTransactionRollingbackService(LocalGroupRepository localGroupRepository) {
        this.localGroupRepository = localGroupRepository;
    }

    /**
     * Edit local group description and throw exception to cause transaction rollback
     *
     * @param exceptionType which type of exception, if any, to throw
     * @return LocalGroupType
     * @throws LocalGroupNotFoundException if local group with given id was not found
     * @throws LocalGroupService.DuplicateLocalGroupCodeException
     * if exceptionType = SERVICE_EXCEPTION
     * @throws RuntimeException if exceptionType = RUNTIME_EXCEPTION
     * @throws Exception if exceptionType = EXCEPTION
     */
    public LocalGroupType updateDescriptionAndRollback(Long groupId, String description, ExceptionType exceptionType)
            throws Exception {
        LocalGroupType localGroupType = localGroupRepository.getLocalGroup(groupId);
        if (localGroupType == null) {
            throw new LocalGroupNotFoundException("LocalGroup with id " + groupId + " not found");
        }
        localGroupType.setDescription(description);
        localGroupType.setUpdated(new Date());
        switch (exceptionType) {
            case EXCEPTION:
                throw new Exception("failing on purpose");
            case RUNTIME_EXCEPTION:
                throw new RuntimeException("failing on purpose");
            case SERVICE_EXCEPTION:
                throw new LocalGroupService.DuplicateLocalGroupCodeException("failing on purpose");
            default:
        }
        return localGroupType;
    }

    public enum ExceptionType {
        NONE,
        EXCEPTION,
        SERVICE_EXCEPTION,
        RUNTIME_EXCEPTION
    }
}
