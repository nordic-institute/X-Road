package org.niis.xroad.authproto.auth;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.Set;

/**
 * test PAM authentication provider.
 * Application has to be run as a user who has read access to /etc/shadow (likely means that belongs to group shadow)
 * roles are granted with user groups xroad-auth-proto-user and xroad-auth-proto-admin
 */
public class PamAuthenticationProvider implements AuthenticationProvider {

    static Logger logger = LoggerFactory.getLogger(PamAuthenticationProvider.class);

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getPrincipal());
        String password = String.valueOf(authentication.getCredentials());
        PAM pam;
        try {
            pam = new PAM("XROAD-AUTH-PROTO");
        } catch (PAMException e) {
            throw new AuthenticationServiceException("Could not initialize PAM.", e);
        }
        try {
            logger.info("trying PAM login with {}/{}", username, password);
            UnixUser user = pam.authenticate(username, password);
            logger.info("logged in successfully");
            Set<String> groups = user.getGroups();
            logger.info("got groups: {}", groups);
            Set<GrantedAuthority> grants = new HashSet<>();
            if (groups.contains("xroad-auth-proto-user")) {
                grants.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            if (groups.contains("xroad-auth-proto-admin")) {
                grants.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (grants.isEmpty()) {
                throw new AuthenticationServiceException("user hasn't got any required groups");
            }
            return new UsernamePasswordAuthenticationToken(user.getUserName(), authentication.getCredentials(), grants);
        } catch (PAMException e) {
            throw new BadCredentialsException("PAM authentication failed.", e);
        } finally {
            pam.dispose();
        }
    }
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(
                UsernamePasswordAuthenticationToken.class);
    }
}

