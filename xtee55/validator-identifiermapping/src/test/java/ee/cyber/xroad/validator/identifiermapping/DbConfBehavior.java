package ee.cyber.xroad.validator.identifiermapping;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify correct reading of database properties.
 */
public class DbConfBehavior {

    /**
     * Test to ensure the database properties file is read correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void shouldReadDbConf() throws Exception {
        // Given/when
        DbConf conf = new DbConf("src/test/resources/db.properties");

        // Then
        String expectedUrl =
                "jdbc:postgresql://127.0.0.1:5432/centerui_development";

        assertEquals(expectedUrl, conf.getUrl());
        assertEquals("centerui", conf.getUsername());
        assertEquals("centerui", conf.getPassword());
    }
}
