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

class Conf implements Closeable {

    private DbConf dbConf;
    private Connection connection;

    public Conf(DbConf dbConf) throws Exception {
        this.dbConf = dbConf;

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

        connection = DriverManager.getConnection(
                dbConf.getUrl(), dbConf.getUsername(), dbConf.getPassword());

        connection.setReadOnly(true);

        System.out.println("Database connection established successfully");
    }

}
