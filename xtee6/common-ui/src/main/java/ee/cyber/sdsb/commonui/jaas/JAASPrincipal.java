package ee.cyber.sdsb.commonui.jaas;

import java.security.Principal;
import java.util.Set;

public class JAASPrincipal implements Principal {

    private String name;
    private Set<String> roles;

    public JAASPrincipal(String name) {
        this.name = name;
    }

    public JAASPrincipal(String name, Set<String> roles) {
        this.name = name;
        this.roles = roles;
    }

    public boolean equals(Object other) {
        if (!(other instanceof JAASPrincipal)) {
            return false;
        }

        return name.equals(((JAASPrincipal) other).getName());
    }

    public int hashCode() {
        return name.hashCode();
    }

    public String getName() {
        return name;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public String toString() {
        return name;
    }
}
