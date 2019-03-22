package org.niis.xroad.restapi.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.ErrorInfo;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test api keys controller
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ApiKeyControllerTest {

    @Autowired
    private ApiKeyController apiKeyController;

    @Test
    @WithMockUser(roles = Role.Names.XROAD_SYSTEM_ADMINISTRATOR)
    public void createKey() {
        Map<String, Object> response = apiKeyController.createKey(Arrays.asList(Role.Names.XROAD_REGISTRATION_OFFICER,
                Role.Names.XROAD_SECURITY_OFFICER));
        String key = (String) response.get("key");
        Collection roles = (Collection) response.get("roles");
        Long id = (Long) response.get("id");
        assertTrue(key.length() > 0);
        assertEquals(2, roles.size());
        assertTrue(roles.contains(Role.XROAD_SECURITY_OFFICER));
        assertTrue(id > 0);

        ResponseEntity<Collection<PersistentApiKeyType>> listResponse =
                apiKeyController.list();
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertEquals(1, listResponse.getBody().size());
   }

    @Test
    @WithMockUser(roles = Role.Names.XROAD_SYSTEM_ADMINISTRATOR)
    public void list() {
        ResponseEntity<Collection<PersistentApiKeyType>> response =
                apiKeyController.list();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @WithMockUser(roles = Role.Names.XROAD_SYSTEM_ADMINISTRATOR)
    public void revoke() {
        apiKeyController.createKey(Arrays.asList(Role.Names.XROAD_REGISTRATION_OFFICER,
                Role.Names.XROAD_SECURITY_OFFICER));
        assertEquals(1, apiKeyController.list().getBody().size());
        long id = apiKeyController.list().getBody().iterator().next().getId();

        ResponseEntity<ErrorInfo> result = apiKeyController.revoke(id);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(0, apiKeyController.list().getBody().size());

        try {
            result = apiKeyController.revoke(id);
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }

        try {
            result = apiKeyController.revoke(1000L);
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }
    }
}
