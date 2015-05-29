package ee.cyber.xroad.validator.identifiermapping;

import org.apache.commons.configuration.HierarchicalINIConfiguration;

/**
 * Represents a database connection configuration file.
 */
class DbConf {
    private final HierarchicalINIConfiguration iniConf;

    public DbConf(String confFile) throws Exception {
        iniConf = new HierarchicalINIConfiguration(confFile);

        checkPresenceOfDriver();
    }

    public String getUsername() {
        return getProperty("username", "");
    }

    public String getPassword() {
        return getProperty("password", "");
    }

    public String getUrl() {
        return String.format("jdbc:%s://%s:%s/%s",
                getAdapter(), getHost(), getPort(), getDatabase());
    }

    private String getAdapter() {
        return getProperty("adapter", "postgresql");
    }

    private String getHost() {
        return getProperty("host", "127.0.0.1");
    }

    private String getPort() {
        return getProperty("port", "5432");
    }

    private String getDatabase() {
        return getProperty("database", "centerui_production");
    }

    private String getProperty(String key, String defaultValue) {
        String result = iniConf.getString(key);

        return result != null ? result : defaultValue;
    }

    private static void checkPresenceOfDriver() throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
    }
}
