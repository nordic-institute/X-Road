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
package org.niis.xroad.cs.admin.jpa.repository;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import io.vavr.control.Option;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.jpa.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.jpa.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.jpa.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.jpa.entity.XRoadMemberEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;

@Disabled("Deprecated. Should be replaced by servicetests")
public class XRoadMemberRepositoryTest {

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    @ExtendWith(MockitoExtension.class)
    public static class FindMemberMethod {

        @Mock
        JpaXRoadMemberRepository xRoadMemberRepository;

        @Mock
        XRoadMemberEntity expectedXRoadMemberEntity;

        @Test
        @DisplayName("should use passed argument for internal 'findOneBy' call when object type is MEMBER")
        void shouldUsePassedArgumentForInternalFindOneByCallWhenObjectTypeIsMEMBER() {
            MemberIdEntity clientIdWithMemberType = MemberIdEntity.create("EE", "EE", "1234567890");
            Mockito.doCallRealMethod().when(xRoadMemberRepository).findMember(clientIdWithMemberType);
            Mockito.doReturn(Option.of(expectedXRoadMemberEntity)).when(xRoadMemberRepository).findOneBy(clientIdWithMemberType);

            Option<XRoadMemberEntity> actual = xRoadMemberRepository.findMember(clientIdWithMemberType);

            assertEquals(expectedXRoadMemberEntity, actual.get());
            InOrder inOrder = inOrder(xRoadMemberRepository);
            inOrder.verify(xRoadMemberRepository).findMember(clientIdWithMemberType);
            inOrder.verify(xRoadMemberRepository).findOneBy(clientIdWithMemberType);
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("should introduce modified argument for internal 'findOneBy' call when object type is not MEMBER")
        void shouldIntroduceModifiedArgumentForInternalFindOneByCallWhenObjectTypeIsNotMEMBER() {
            SubsystemIdEntity clientIdSubsystemTypeArgument = SubsystemIdEntity.create("EE", "EE", "1234567890", "subSystemName");
            ArgumentMatcher<ClientIdEntity> clientIdObjectTypeAlteredToMemberArgMatcher = clientId ->
                    clientId.getXRoadInstance().equals(clientIdSubsystemTypeArgument.getXRoadInstance())
                            && clientId.getMemberClass().equals(clientIdSubsystemTypeArgument.getMemberClass())
                            && clientId.getMemberCode().equals(clientIdSubsystemTypeArgument.getMemberCode())
                            && clientId.getObjectType() == XRoadObjectType.MEMBER;
            Mockito.doCallRealMethod().when(xRoadMemberRepository).findMember(clientIdSubsystemTypeArgument);
            Mockito.doReturn(Option.of(expectedXRoadMemberEntity))
                    .when(xRoadMemberRepository).findOneBy(ArgumentMatchers.argThat(clientIdObjectTypeAlteredToMemberArgMatcher));

            Option<XRoadMemberEntity> actual = xRoadMemberRepository.findMember(clientIdSubsystemTypeArgument);

            assertEquals(expectedXRoadMemberEntity, actual.get());
            InOrder inOrder = inOrder(xRoadMemberRepository);
            inOrder.verify(xRoadMemberRepository).findMember(clientIdSubsystemTypeArgument);
            inOrder.verify(xRoadMemberRepository).findOneBy(ArgumentMatchers.argThat(clientIdObjectTypeAlteredToMemberArgMatcher));
            inOrder.verifyNoMoreInteractions();
        }
    }
}
