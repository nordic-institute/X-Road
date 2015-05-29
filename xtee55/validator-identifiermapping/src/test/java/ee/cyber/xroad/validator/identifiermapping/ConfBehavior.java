package ee.cyber.xroad.validator.identifiermapping;

import org.apache.commons.lang.StringUtils;

/**
 * Test program to see if the database can be accessed.
 */
public final class ConfBehavior {

    private ConfBehavior() {
    }

    /**
     * Purpose is just to see if we can access the database.
     * @param args command-line arguments
     * @throws Exception in case of any unexpected errors
     */
    public static void main(String[] args) throws Exception {
        DbConf dbConf = new DbConf("src/test/resources/db.properties");

        try (Conf conf = new Conf(dbConf)) {
            System.out.println("Instance identifier:");
            System.out.println("\t" + conf.getInstanceIdentifier());

            System.out.println();

            System.out.println("Allowed member classes:");
            System.out.println("\t"
                    + StringUtils.join(conf.getAllowedMemberClasses(), ", "));
        }
    }

}
