package ee.cyber.xroad.validator.identifiermapping;

import org.apache.commons.lang.StringUtils;


public class ConfBehavior {

    /**
     * Purpose is just to see if we can access the database.
     *
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        setDbProperties();

        try (Conf conf = new Conf()) {
            System.out.println("Instance identifier:");
            System.out.println("\t" + conf.getInstanceIdentifier());

            System.out.println();

            System.out.println("Allowed member classes:");
            System.out.println("\t"
                    + StringUtils.join(conf.getAllowedMemberClasses(), ", "));
        }
    }

    private static void setDbProperties() {
        System.setProperty(Conf.PROP_DB_DATABASE, "centerui_development");
        System.setProperty(Conf.PROP_DB_USERNAME, "centerui");
        System.setProperty(Conf.PROP_DB_PASSWORD, "centerui");
    }

}
