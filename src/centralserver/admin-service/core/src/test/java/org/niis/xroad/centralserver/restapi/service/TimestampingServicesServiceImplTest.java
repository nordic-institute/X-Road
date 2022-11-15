/*
 * The MIT License
 *
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

package org.niis.xroad.centralserver.restapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ApprovedTsa;
import org.niis.xroad.cs.admin.core.entity.ApprovedTsaEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ApprovedTsaMapper;
import org.niis.xroad.cs.admin.core.repository.ApprovedTsaRepository;

import java.util.Set;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimestampingServicesServiceImplTest {

    @Mock
    private ApprovedTsaRepository approvedTsaRepository;
    @Mock
    private ApprovedTsaMapper approvedTsaMapper;

    @InjectMocks
    private TimestampingServicesServiceImpl timestampingServicesService;

    @Test
    void getTimestampingServices() {
        when(approvedTsaRepository.findAll()).thenReturn(of(mock(ApprovedTsaEntity.class), mock(ApprovedTsaEntity.class)));
        when(approvedTsaMapper.toTarget(isA(ApprovedTsaEntity.class))).thenReturn(mock(ApprovedTsa.class), mock(ApprovedTsa.class));

        final Set<ApprovedTsa> timestampingServices = timestampingServicesService.getTimestampingServices();

        assertEquals(2, timestampingServices.size());
        verify(approvedTsaMapper, times(2)).toTarget(isA(ApprovedTsaEntity.class));
    }

}
