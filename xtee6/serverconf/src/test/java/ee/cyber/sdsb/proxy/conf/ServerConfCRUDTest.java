package ee.cyber.sdsb.proxy.conf;

import java.util.Scanner;

import org.hibernate.Session;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.conf.serverconf.model.ServiceType;
import ee.cyber.sdsb.common.conf.serverconf.model.WsdlType;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.identifier.ClientId;

import static java.lang.System.out;

/**
 * Simple command line application for modifying the server conf in database.
 * Expects PostgreSQL database.
 */
public class ServerConfCRUDTest {

    private static final Scanner console = new Scanner(System.in);

    public static void main(String[] args) {
        out.println("Starting...");

        System.setProperty(
                SystemProperties.DATABASE_PROPERTIES,
                "src/test/resources/hibernate-postgres.properties");
                //"src/test/resources/hibernate-crud.properties");

        while (true) {
            try {
                if (!mainLoop()) {
                    HibernateUtil.closeSessionFactories();
                    return;
                }
            } catch (Exception e) {
                System.err.println("########################################");
                System.err.println("ERROR: " + e);
                System.err.println("########################################");
            }
        }
    }

    private static boolean mainLoop() throws Exception {
        out.print("Command: ");

        String input = console.next();
        switch (input) {
            case "init":
                initDB();
                return true;
            case "create":
                doInTransaction(session -> {
                    createClient(session);
                    return null;
                });
                return true;
            case "read":
                doInTransaction(session -> {
                    readClient(session);
                    return null;
                });
                return true;
            case "update":
                doInTransaction(session -> {
                    updateClient(session);
                    return null;
                });
                return true;
            case "delete":
                doInTransaction(session -> {
                    deleteClient(session);
                    return null;
                });
                return true;
            case "exit":
                out.println("Exiting...");
                return false;
        }

        out.println("Unknown command: " + input);
        return true;
    }

    private static void createClient(Session session) {
        out.println("Creating client...");

        // TODO
    }

    private static void deleteClient(Session session) {
        out.println("Deleting client...");

        // TODO
    }

    private static void updateClient(Session session) {
        out.println("Updating client...");

        String contacts = readFromConsole("Contacts");
        String status = readFromConsole("Status");

        ClientId clientId = TestUtil.createTestClientId(TestUtil.client(1));
        ClientType client =
                ClientDAOImpl.getInstance().getClient(session, clientId);
        if (client == null) {
            out.println("Client " + clientId + " not found!");
            return;
        }

        client.setContacts(contacts);
        client.setClientStatus(status);
    }

    private static void readClient(Session session) {
        out.println("Reading client...");

        ClientId clientId = TestUtil.createTestClientId(TestUtil.client(1));
        ClientType client =
                ClientDAOImpl.getInstance().getClient(session, clientId);

        display("", client);
    }

    private static void doInTransaction(TransactionCallback<?> cb)
            throws Exception {
        long start = System.currentTimeMillis();

        ServerConfDatabaseCtx.doInTransaction(cb);

        out.println(String.format("(Executed in %d ms)",
                (System.currentTimeMillis() - start)));
    }

    private static String readFromConsole(String label) {
        out.print(label + ":");
        return console.next();
    }

    private static void initDB() throws Exception {
        out.println("Initializing database...");

        TestUtil.prepareDB(false);
    }

    private static void display(String tab, ClientType client) {
        out.println(tab + "Client " + client.getIdentifier());
        out.println(tab + "\tContacts: " + client.getContacts());
        out.println(tab + "\tStatus: " + client.getClientStatus());
        out.println(tab + "\tWSDLs (" + client.getWsdl().size() + "):");

        for (WsdlType wsdl : client.getWsdl()) {
            display(tab + "\t", wsdl);
            out.println();
        }
    }

    private static void display(String tab, WsdlType wsdl) {
        out.println(tab + "\tURL: " + wsdl.getUrl());
        out.println(tab + "\tBackend: " + wsdl.getBackend());
        out.println(tab + "\tBackendURL: " + wsdl.getBackendURL());
        out.println(tab + "\tServices (" + wsdl.getService().size() + "): ");

        for (ServiceType service : wsdl.getService()) {
            display(tab + "\t", service);
            out.println();
        }
    }

    private static void display(String tab, ServiceType service) {
        out.println(tab + "\tTitle: " + service.getTitle());
        out.println(tab + "\tCode: " + service.getServiceCode());
        out.println(tab + "\tVersion: " + service.getServiceVersion());
        out.println(tab + "\tTimeout: " + service.getTimeout());
        out.println(tab + "\tURL: " + service.getUrl());
    }
}
