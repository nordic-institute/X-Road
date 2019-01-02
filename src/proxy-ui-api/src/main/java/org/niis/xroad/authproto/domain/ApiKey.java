package org.niis.xroad.authproto.domain;

import lombok.Getter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Getter
public class ApiKey {
    private String key;
    private Set<String> roles;

    public ApiKey(String key, Collection<String> roles) {
        this.key = key;
        this.roles = new HashSet<>();
        this.roles.addAll(roles);
    }
}
