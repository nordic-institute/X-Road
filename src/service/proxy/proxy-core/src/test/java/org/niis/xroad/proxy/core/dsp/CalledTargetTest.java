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
package org.niis.xroad.proxy.core.dsp;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CalledTargetTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "222", "Provider");
    private static final ClientId MANAGEMENT = ClientId.Conf.create("DEV", "COM", "222", "MANAGEMENT");

    @Test
    void managementServiceAddressedToManagementSubsystemTargetsSystem() {
        var target = CalledTarget.of(ServiceId.Conf.create(MANAGEMENT, "clientReg"), true);

        assertThat(target).isEqualTo(new CalledTarget(MANAGEMENT, true));
    }

    @Test
    void managementServiceNotAddressedToManagementSubsystemTargetsMember() {
        var target = CalledTarget.of(ServiceId.Conf.create(MANAGEMENT, "clientReg"), false);

        assertThat(target.system()).isFalse();
    }

    @Test
    void managementServiceThatIsNeverNegotiatedTargetsMember() {
        var target = CalledTarget.of(ServiceId.Conf.create(MANAGEMENT, "authCertReg"), true);

        assertThat(target.system()).isFalse();
    }

    @Test
    void versionedManagementServiceUnderManagementSubsystemTargetsMember() {
        var versioned = ServiceId.Conf.create(MANAGEMENT, "clientReg", "v1");

        assertThat(CalledTarget.of(versioned, true).system()).isFalse();
    }

    @Test
    void metaserviceCodeUnderManagementSubsystemTargetsMember() {
        var target = CalledTarget.of(ServiceId.Conf.create(MANAGEMENT, "listMethods"), true);

        assertThat(target.system()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"getSecurityServerMetrics", "getSecurityServerOperationalData", "getSecurityServerHealthData"})
    void monitoringServiceOnMemberTargetsSystem(String serviceCode) {
        var target = CalledTarget.of(ServiceId.Conf.create(MEMBER, serviceCode), false);

        assertThat(target).isEqualTo(new CalledTarget(MEMBER, true));
    }

    @Test
    void monitoringServiceCodeOnSubsystemTargetsMember() {
        var target = CalledTarget.of(ServiceId.Conf.create(SUBSYSTEM, "getSecurityServerMetrics"), false);

        assertThat(target.system()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"listMethods", "allowedMethods", "getWsdl", "getOpenAPI"})
    void metaserviceCodeOnMemberTargetsMember(String serviceCode) {
        var target = CalledTarget.of(ServiceId.Conf.create(MEMBER, serviceCode), false);

        assertThat(target.system()).isFalse();
    }

    @Test
    void subsystemProviderGivesMemberTargetForTheCalledClient() {
        var target = CalledTarget.of(ServiceId.Conf.create(SUBSYSTEM, "testService"), false);

        assertThat(target).isEqualTo(new CalledTarget(SUBSYSTEM, false));
    }
}
