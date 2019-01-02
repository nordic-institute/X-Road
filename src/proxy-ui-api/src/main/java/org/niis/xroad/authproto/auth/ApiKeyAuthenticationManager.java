package org.niis.xroad.authproto.auth;

import org.niis.xroad.authproto.domain.ApiKey;
import org.niis.xroad.authproto.repository.ApiKeyRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AuthenticationManager which expects Authentication.principal to be
 * an api key (prepared by RequestHeaderAuthenticationFilter)
 */

@Component
public class ApiKeyAuthenticationManager implements AuthenticationManager {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationManager.class);

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String principal = (String) authentication.getPrincipal();
        logger.debug("principal: {}", principal);
        ApiKey key = apiKeyRepository.get(principal);
        if (key == null) {
            throw new BadCredentialsException("The API key was not found or not the expected value.");
        }
        PreAuthenticatedAuthenticationToken authenticationWithGrants =
                new PreAuthenticatedAuthenticationToken(authentication.getPrincipal(),
                        authentication.getCredentials(),
                        rolenamesToGrants(key.getRoles()));
        logger.debug("authentication: {}", authenticationWithGrants);
        return authenticationWithGrants;
    }

    private Set<SimpleGrantedAuthority> rolenamesToGrants(Collection<String> rolenames) {
        return rolenames.stream()
                .map(name -> new SimpleGrantedAuthority("ROLE_" + name.toUpperCase()))
                .collect(Collectors.toSet());
    }
}
