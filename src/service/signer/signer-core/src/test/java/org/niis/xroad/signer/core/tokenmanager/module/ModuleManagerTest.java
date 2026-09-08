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

package org.niis.xroad.signer.core.tokenmanager.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleManagerTest {

    @Mock
    private SoftwareModuleWorkerFactory softwareModuleWorkerFactory;
    @Mock
    private HardwareModuleWorkerFactory hardwareModuleWorkerFactory;
    @Mock
    private SignerHwTokenAddonProperties hwTokenAddonProperties;
    @Mock
    private ModuleConf moduleConf;

    @InjectMocks
    ModuleManager moduleManager;

    private final HardwareModuleType hwModuleType = new HardwareModuleType("hwModule", null, false,
            false, "tokenIdFormat", false, false, false, null,
            null, null, null, null);
    private final SoftwareModuleType swModuleType = new SoftwareModuleType();

    @Mock
    private HardwareModuleWorkerFactory.HardwareModuleWorker hwModuleWorker;
    @Mock
    private SoftwareModuleWorkerFactory.SoftwareModuleWorker swModuleWorker;

    @Test
    void testRefresh() {
        when(hwModuleWorker.getModuleType()).thenReturn(hwModuleType);
        when(swModuleWorker.getModuleType()).thenReturn(swModuleType);

        when(moduleConf.hasChanged()).thenReturn(true);
        when(moduleConf.getModules()).thenReturn(Set.of(swModuleType, hwModuleType));

        when(softwareModuleWorkerFactory.create(isA(SoftwareModuleType.class)))
                .thenReturn(swModuleWorker);
        when(hardwareModuleWorkerFactory.create(isA(HardwareModuleType.class)))
                .thenReturn(hwModuleWorker);

        moduleManager.refresh();

        assertTrue(moduleManager.isModuleInitialized(swModuleType));
        assertTrue(moduleManager.isModuleInitialized(hwModuleType));

        verify(moduleConf).reload();
        verify(softwareModuleWorkerFactory).create(isA(SoftwareModuleType.class));
        verify(hardwareModuleWorkerFactory).create(isA(HardwareModuleType.class));
        verify(swModuleWorker).start();
        verify(swModuleWorker).refresh();
        verify(hwModuleWorker).start();
        verify(hwModuleWorker).refresh();

        when(hwTokenAddonProperties.enabled()).thenReturn(true);
        assertTrue(moduleManager.isHSMModuleOperational());
    }

    @Test
    void testRefreshNoChanges() {
        // run initial refresh to load modules into manager
        testRefresh();

        reset(swModuleWorker, hwModuleWorker, softwareModuleWorkerFactory, hardwareModuleWorkerFactory);

        // verify no actions when refresh again
        when(moduleConf.hasChanged()).thenReturn(false);
        moduleManager.refresh();
        verify(swModuleWorker).refresh();
        verifyNoMoreInteractions(swModuleWorker);
        verify(hwModuleWorker).refresh();
        verifyNoMoreInteractions(hwModuleWorker);

        verifyNoInteractions(hardwareModuleWorkerFactory, softwareModuleWorkerFactory);
    }

    @Test
    void testRefreshModuleRemoved() {
        // run initial refresh to load modules into manager
        testRefresh();
        reset(moduleConf, swModuleWorker, hwModuleWorker, softwareModuleWorkerFactory, hardwareModuleWorkerFactory);

        when(swModuleWorker.getModuleType()).thenReturn(swModuleType);

        when(moduleConf.hasChanged()).thenReturn(true);
        when(moduleConf.getModules()).thenReturn(Set.of(swModuleType));

        moduleManager.refresh();

        verify(moduleConf).reload();
        verify(hwModuleWorker).destroy();

        assertTrue(moduleManager.isModuleInitialized(swModuleType));
        assertFalse(moduleManager.isModuleInitialized(hwModuleType));
    }

}
