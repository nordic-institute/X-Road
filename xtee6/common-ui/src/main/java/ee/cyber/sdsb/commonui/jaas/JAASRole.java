package ee.cyber.sdsb.commonui.jaas;

public class JAASRole extends JAASPrincipal {

    public JAASRole(String name) {
        super(name);
    }

    public boolean equals(Object other) {
        if (!(other instanceof JAASRole)) {
            return false;
        }

        return getName().equals(((JAASRole) other).getName());
    }
}
