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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.serverconf.entity.ServiceEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * service change checker tests
 */
public class ServiceChangeCheckerTest {

    private ServiceChangeChecker serviceChangeChecker;
    private ServiceEntity random;
    private ServiceEntity random1;
    private ServiceEntity random2;
    private ServiceEntity foo1;

    @Before
    public void setup() {
        serviceChangeChecker = new ServiceChangeChecker();
        random = createService("getRandom", null);
        random1 = createService("getRandom", "v1");
        random2 = createService("getRandom", "v2");
        foo1 = createService("fooService", "v1");
    }

    private ServiceEntity createService(String serviceCode, String serviceVersion) {
        ServiceEntity service = new ServiceEntity();
        service.setServiceCode(serviceCode);
        service.setServiceVersion(serviceVersion);
        return service;
    }

    @Test
    public void testNoChanges() {
        List<ServiceEntity> randoms = Arrays.asList(random1, random2);
        assertTrue(serviceChangeChecker.check(randoms, randoms).isEmpty());
    }

    @Test
    public void testChanges() {
        List<ServiceEntity> oldServices = Arrays.asList(random1, random);
        List<ServiceEntity> newServices = Arrays.asList(foo1, random1);
        ServiceChangeChecker.ServiceChanges changes = serviceChangeChecker.check(oldServices, newServices);
        assertFalse(changes.isEmpty());
        assertEquals(List.of("fooService.v1"), changes.getAddedFullServiceCodes());
        assertEquals(List.of("getRandom"), changes.getRemovedFullServiceCodes());

        List<ServiceEntity> allServices = Arrays.asList(random1, random, random2, foo1);
        List<String> allCodes = Arrays.asList("getRandom", "getRandom.v1", "getRandom.v2", "fooService.v1");
        List<ServiceEntity> noServices = new ArrayList<>();
        changes = serviceChangeChecker.check(noServices, allServices);
        assertFalse(changes.isEmpty());
        assertEquals(new HashSet<>(allCodes), new HashSet<>(changes.getAddedFullServiceCodes()));
        assertEquals(new HashSet<>(), new HashSet<>(changes.getRemovedFullServiceCodes()));

        changes = serviceChangeChecker.check(allServices, noServices);
        assertFalse(changes.isEmpty());
        assertEquals(new HashSet<>(), new HashSet<>(changes.getAddedFullServiceCodes()));
        assertEquals(new HashSet<>(allCodes), new HashSet<>(changes.getRemovedFullServiceCodes()));
    }

}
