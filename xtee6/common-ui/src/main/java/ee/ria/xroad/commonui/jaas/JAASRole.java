package ee.ria.xroad.commonui.jaas;

/**
 * JAAS role.
 */
public class JAASRole extends JAASPrincipal {

    /**
     * Constructs new JAAS role with the given name.
     * @param name the name
     */
    public JAASRole(String name) {
        super(name);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JAASRole)) {
            return false;
        }

        return getName().equals(((JAASRole) other).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
