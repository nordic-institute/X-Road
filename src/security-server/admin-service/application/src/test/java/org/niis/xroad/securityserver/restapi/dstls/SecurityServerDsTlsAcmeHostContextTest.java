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
package org.niis.xroad.securityserver.restapi.dstls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.properties.NodeProperties.NODE_TYPE_ENV_VARIABLE;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class SecurityServerDsTlsAcmeHostContextTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;
    @Mock
    private MailNotificationHelper mailNotificationHelper;

    private SecurityServerDsTlsAcmeHostContext hostContext;

    @BeforeEach
    void setUp() {
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        hostContext = new SecurityServerDsTlsAcmeHostContext(adminServiceProperties, mailNotificationHelper);
    }

    @Test
    void getPublicHostnameShouldReturnNullWhenDataSpaceIsNotEnabled() {
        when(dataspace.getIdentityHubUrl()).thenReturn("");

        assertThat(hostContext.getPublicHostname()).isNull();
    }

    @Test
    void getPublicHostnameShouldReturnNullWhenIdentityHubUrlIsBlank() {
        when(dataspace.getIdentityHubUrl()).thenReturn("   ");

        assertThat(hostContext.getPublicHostname()).isNull();
    }

    @Test
    void getPublicHostnameShouldExtractTheHostFromTheConfiguredUrl() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://ds.example.org:7182");

        assertThat(hostContext.getPublicHostname()).isEqualTo("ds.example.org");
    }

    @Test
    void getPublicHostnameShouldThrowWhenTheConfiguredUrlHasNoHost() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://");

        assertThatIllegalArgumentException().isThrownBy(() -> hostContext.getPublicHostname());
    }

    @Test
    void getConfiguredHostnameSourceShouldReturnTheRawIdentityHubUrl() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://");

        assertThat(hostContext.getConfiguredHostnameSource()).isEqualTo("https://");
    }

    @Test
    void getEabAliasShouldBeTheFixedDsTlsAlias() {
        assertThat(hostContext.getEabAlias()).isEqualTo(SecurityServerDsTlsAcmeHostContext.DS_TLS_ACME_ALIAS);
    }

    @Test
    void getAccountContactsShouldBeEmptyWhenUnconfigured() {
        assertThat(hostContext.getAccountContacts()).isEmpty();
    }

    @Test
    void getAccountContactsShouldReturnTheConfiguredContacts() {
        when(dataspace.getTlsCertificateContacts()).thenReturn(List.of("dstls@example.org"));

        assertThat(hostContext.getAccountContacts()).containsExactly("dstls@example.org");
    }

    @Test
    void isSchedulingActiveShouldBeFalseWhenDataSpaceIsDisabled() {
        variables.set(NODE_TYPE_ENV_VARIABLE, NodeProperties.NodeType.PRIMARY.name());
        when(dataspace.isEnabled()).thenReturn(false);

        assertThat(hostContext.isSchedulingActive()).isFalse();
    }

    @Test
    void isSchedulingActiveShouldBeFalseOnASecondaryNode() {
        variables.set(NODE_TYPE_ENV_VARIABLE, NodeProperties.NodeType.SECONDARY.name());
        lenient().when(dataspace.isEnabled()).thenReturn(true);

        assertThat(hostContext.isSchedulingActive()).isFalse();
    }

    @Test
    void isSchedulingActiveShouldBeTrueOnAPrimaryNodeWhenDataSpaceIsEnabled() {
        variables.set(NODE_TYPE_ENV_VARIABLE, NodeProperties.NodeType.PRIMARY.name());
        when(dataspace.isEnabled()).thenReturn(true);

        assertThat(hostContext.isSchedulingActive()).isTrue();
    }

    @Test
    void isSchedulingActiveShouldBeTrueOnAStandaloneNodeWhenDataSpaceIsEnabled() {
        variables.set(NODE_TYPE_ENV_VARIABLE, NodeProperties.NodeType.STANDALONE.name());
        when(dataspace.isEnabled()).thenReturn(true);

        assertThat(hostContext.isSchedulingActive()).isTrue();
    }

    @Test
    void notifyEnrollmentSuccessShouldDelegateToMailNotificationHelper() {
        hostContext.notifyEnrollmentSuccess("ds.example.org", true);

        verify(mailNotificationHelper).sendDsTlsAcmeSuccessNotification("ds.example.org", true);
    }

    @Test
    void notifyEnrollmentFailureShouldDelegateToMailNotificationHelper() {
        hostContext.notifyEnrollmentFailure("ds.example.org", "boom");

        verify(mailNotificationHelper).sendDsTlsAcmeFailureNotification("ds.example.org", "boom");
    }
}
