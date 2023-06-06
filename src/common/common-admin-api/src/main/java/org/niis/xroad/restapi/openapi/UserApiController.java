/**
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
package org.niis.xroad.restapi.openapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.model.User;
import org.niis.xroad.restapi.util.UsernameHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * User controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class UserApiController implements UserApi {

    public static final String USER_API_V1_PATH = ControllerUtil.API_V1_PREFIX + "/user";
    public static final String ROLE_PREFIX = "ROLE_";

    private final UsernameHelper usernameHelper;

    /**
     * Return user object
     * @return
     */
    @PreAuthorize("permitAll()")
    @Override
    public ResponseEntity<User> getUser() {
        User user = new User();
        user.setUsername(usernameHelper.getUsername());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        user.setPermissions(new HashSet<>(getAuthorities(authentication, name -> !name.startsWith(ROLE_PREFIX))));
        user.setRoles(new HashSet<>(getAuthorities(authentication, name -> name.startsWith(ROLE_PREFIX))));
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    /**
     * get roles
     * @param authentication
     * @return
     */
    @PreAuthorize("permitAll()")
    @GetMapping(value = USER_API_V1_PATH + "/roles")
    public ResponseEntity<Set<String>> getRoles(Authentication authentication) {
        return new ResponseEntity<>(
                getAuthorities(authentication, name -> name.startsWith(ROLE_PREFIX)),
                HttpStatus.OK);
    }

    /**
     * get permissions
     * @param authentication
     * @return
     */
    @PreAuthorize("permitAll()")
    @GetMapping(value = USER_API_V1_PATH + "/permissions")
    public ResponseEntity<Set<String>> getPermissions(Authentication authentication) {
        return new ResponseEntity<>(
                getAuthorities(authentication, name -> !name.startsWith(ROLE_PREFIX)),
                HttpStatus.OK);
    }

    private Set<String> getAuthorities(Authentication authentication,
            Predicate<String> authorityNamePredicate) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authorityNamePredicate)
                .collect(Collectors.toSet());
        return roles;
    }

}
