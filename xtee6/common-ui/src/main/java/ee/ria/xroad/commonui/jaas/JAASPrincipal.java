package ee.ria.xroad.commonui.jaas;

import java.security.Principal;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * JAAS principal.
 */
@RequiredArgsConstructor
@AllArgsConstructor
public class JAASPrincipal implements Principal {

    private final String name;

    @Getter
    private Set<String> roles;

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JAASPrincipal)) {
            return false;
        }

        return name.equals(((JAASPrincipal) other).getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
