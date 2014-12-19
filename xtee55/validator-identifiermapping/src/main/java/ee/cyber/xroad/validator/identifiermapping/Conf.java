package ee.cyber.xroad.validator.identifiermapping;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Conf implements Closeable {
    public static final String PROP_DB_HOST =
            "ee.cyber.xroad.validator.identifiermapping.db.host";
    public static final String PROP_DB_PORT =
            "ee.cyber.xroad.validator.identifiermapping.db.port";
    public static final String PROP_DB_DATABASE =
            "ee.cyber.xroad.validator.identifiermapping.db.database";
    public static final String PROP_DB_USERNAME =
            "ee.cyber.xroad.validator.identifiermapping.db.username";
    public static final String PROP_DB_PASSWORD =
            "ee.cyber.xroad.validator.identifiermapping.db.password";

    private Connection connection;

    public Conf() throws Exception {
        checkPresenceOfDriver();

        establishDbConnection();
    }

    public String getInstanceIdentifier() throws SQLException {
        String sql =
                "SELECT value FROM system_parameters "
                + "WHERE key='instanceIdentifier';";

        Statement statement = null;
        ResultSet rs = null;

        try {
            statement = connection.createStatement();
            rs = statement.executeQuery(sql);

            rs.next();
            return rs.getString("value");
        } finally {
            rs.close();
            statement.close();
        }
    }

    public List<String> getAllowedMemberClasses() throws SQLException {
        String sql = "SELECT code FROM member_classes";

        Statement statement = null;
        ResultSet rs = null;

        List<String> result = new ArrayList<>();

        try {
            statement = connection.createStatement();
            rs = statement.executeQuery(sql);

            while (rs.next()) {
                result.add(rs.getString("code"));
            }
        } finally {
            rs.close();
            statement.close();
        }

        return result;
    }

    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IOException(
                    "Could not close database connection with central database",
                    e);
        }
    }

    private void establishDbConnection() throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%s/%s",
                getDbHost(), getDbPort(), getDbDatabase());

        connection = DriverManager.getConnection(
                url, getDbUsername(), getDbPassword());

        connection.setReadOnly(true);

        System.out.println("Database connection established successfully");
    }

    private static String getDbHost() {
        return System.getProperty(PROP_DB_HOST, "localhost");
    }

    private static String getDbPort() {
        return System.getProperty(PROP_DB_PORT, "5432");
    }

    private static String getDbDatabase() {
        return System.getProperty(PROP_DB_DATABASE, "centerui_production");
    }

    private static String getDbUsername() {
        return System.getProperty(PROP_DB_USERNAME);
    }

    private static String getDbPassword() {
        return System.getProperty(PROP_DB_PASSWORD);
    }

    private static void checkPresenceOfDriver() throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
    }
}
