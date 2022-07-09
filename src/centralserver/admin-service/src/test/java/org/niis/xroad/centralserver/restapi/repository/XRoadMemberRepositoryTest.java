/**
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
package org.niis.xroad.centralserver.restapi.repository;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import io.vavr.control.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.entity.ClientId;
import org.niis.xroad.centralserver.restapi.entity.MemberId;
import org.niis.xroad.centralserver.restapi.entity.SubsystemId;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;

public class XRoadMemberRepositoryTest {

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    @ExtendWith(MockitoExtension.class)
    public static class FindMemberMethod {

        @Mock
        XRoadMemberRepository xRoadMemberRepository;

        @Mock
        XRoadMember expectedXRoadMember;

        @Test
        @DisplayName("should use passed argument for internal 'findOneBy' call when object type is MEMBER")
        void shouldUsePassedArgumentForInternalFindOneByCallWhenObjectTypeIsMEMBER() {
            MemberId clientIdWithMemberType = MemberId.create("EE", "EE", "1234567890");
            doCallRealMethod().when(xRoadMemberRepository).findMember(clientIdWithMemberType);
            doReturn(Option.of(expectedXRoadMember)).when(xRoadMemberRepository).findOneBy(clientIdWithMemberType);

            Option<XRoadMember> actual = xRoadMemberRepository.findMember(clientIdWithMemberType);

            assertEquals(expectedXRoadMember, actual.get());
            InOrder inOrder = inOrder(expectedXRoadMember, xRoadMemberRepository);
            inOrder.verify(xRoadMemberRepository).findMember(clientIdWithMemberType);
            inOrder.verify(xRoadMemberRepository).findOneBy(clientIdWithMemberType);
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("should introduce modified argument for internal 'findOneBy' call when object type is not MEMBER")
        void shouldIntroduceModifiedArgumentForInternalFindOneByCallWhenObjectTypeIsNotMEMBER() {
            SubsystemId clientIdSubsystemTypeArgument = SubsystemId.create("EE", "EE", "1234567890", "subSystemName");
            ArgumentMatcher<ClientId> clientIdObjectTypeAlteredToMemberArgMatcher = clientId ->
                    clientId.getXRoadInstance().equals(clientIdSubsystemTypeArgument.getXRoadInstance())
                            && clientId.getMemberClass().equals(clientIdSubsystemTypeArgument.getMemberClass())
                            && clientId.getMemberCode().equals(clientIdSubsystemTypeArgument.getMemberCode())
                            && clientId.getObjectType() == XRoadObjectType.MEMBER;
            doCallRealMethod().when(xRoadMemberRepository).findMember(clientIdSubsystemTypeArgument);
            doReturn(Option.of(expectedXRoadMember))
                    .when(xRoadMemberRepository).findOneBy(argThat(clientIdObjectTypeAlteredToMemberArgMatcher));

            Option<XRoadMember> actual = xRoadMemberRepository.findMember(clientIdSubsystemTypeArgument);

            assertEquals(expectedXRoadMember, actual.get());
            InOrder inOrder = inOrder(expectedXRoadMember, xRoadMemberRepository);
            inOrder.verify(xRoadMemberRepository).findMember(clientIdSubsystemTypeArgument);
            inOrder.verify(xRoadMemberRepository).findOneBy(argThat(clientIdObjectTypeAlteredToMemberArgMatcher));
            inOrder.verifyNoMoreInteractions();
        }
    }
}
