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
package org.niis.xroad.cs.admin.rest.api.security;

import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.openapi.model.AddressChangeRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminServicePermissionEvaluator implements PermissionEvaluator {
    private final Map<Class<?>, Enum<?>> targetMapping;
    private final List<TargetTypeResolver<?>> resolvers;

    public AdminServicePermissionEvaluator(List<TargetTypeResolver<?>> resolvers) {
        this.resolvers = resolvers;
        this.targetMapping = new IdentityHashMap<>(Map.of(
                AuthenticationCertificateRegistrationRequestDto.class, ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST,
                AuthenticationCertificateDeletionRequestDto.class, ManagementRequestType.AUTH_CERT_DELETION_REQUEST,
                ClientRegistrationRequestDto.class, ManagementRequestType.CLIENT_REGISTRATION_REQUEST,
                ClientDeletionRequestDto.class, ManagementRequestType.CLIENT_DELETION_REQUEST,
                ClientDisableRequestDto.class, ManagementRequestType.CLIENT_DISABLE_REQUEST,
                ClientEnableRequestDto.class, ManagementRequestType.CLIENT_ENABLE_REQUEST,
                OwnerChangeRequestDto.class, ManagementRequestType.OWNER_CHANGE_REQUEST,
                AddressChangeRequestDto.class, ManagementRequestType.ADDRESS_CHANGE_REQUEST
        ));

    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null
                || targetDomainObject == null
                || !(permission instanceof String)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().isEmpty()) {
            return false;
        }

        final Enum<?> target = targetMapping.get(targetDomainObject.getClass());
        if (target != null) {
            return authentication.getAuthorities().contains(authority(permission.toString(), target));
        }

        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {

        if (authentication == null
                || targetId == null
                || !(permission instanceof String)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().isEmpty()) {
            return false;
        }

        for (var resolver : resolvers) {
            if (resolver.canResolve(targetType, targetId)) {
                return authentication.getAuthorities()
                        .contains(authority(permission.toString(), resolver.resolve(targetType, targetId)));
            }
        }

        return false;
    }

    private static GrantedAuthority authority(String permission, Enum<?> targetType) {
        return new SimpleGrantedAuthority(String.join("_", permission, targetType.name()));
    }
}
