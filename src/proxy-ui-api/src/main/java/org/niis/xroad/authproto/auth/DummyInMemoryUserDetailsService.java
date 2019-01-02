package org.niis.xroad.authproto.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Configures in-memory user details with simple user/password, admin/password
 * type of users, if PAM authentication is not activated
 */
@Configuration
public class DummyInMemoryUserDetailsService {

    @Value("${proto.pam}")
    private boolean pam;

    @Bean
    public UserDetailsService userDetailsService() {
        Collection<UserDetails> users = new ArrayList<>();
        if (!pam) {
            users.add(User.withDefaultPasswordEncoder()
                            .username("user")
                            .password("password")
                            .roles("USER")
                            .build());

            users.add(User.withDefaultPasswordEncoder()
                            .username("admin")
                            .password("password")
                            .roles("USER", "ADMIN")
                            .build());

            users.add(User.withDefaultPasswordEncoder()
                            .username("admindba")
                            .password("password")
                            .roles("USER", "ADMIN","DBA")
                            .build());

            users.add(User.withDefaultPasswordEncoder()
                            .username("dba")
                            .password("password")
                            .roles("USER", "DBA")
                            .build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
