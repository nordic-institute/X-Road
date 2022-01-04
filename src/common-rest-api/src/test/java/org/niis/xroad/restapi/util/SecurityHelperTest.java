package org.niis.xroad.restapi.util;

import org.junit.AfterClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.restapi.domain.Role;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static ee.ria.xroad.common.SystemProperties.NODE_TYPE;

class SecurityHelperTest {

    private static final Collection<Role> ADMIN_ROLES = Arrays.asList(
            Role.XROAD_SECURITY_OFFICER,
            Role.XROAD_REGISTRATION_OFFICER,
            Role.XROAD_SERVICE_ADMINISTRATOR,
            Role.XROAD_SYSTEM_ADMINISTRATOR,
            Role.XROAD_SECURITYSERVER_OBSERVER);

    private static final Collection<Role> OBSERVER_ROLES = Collections.singletonList(
            Role.XROAD_SECURITYSERVER_OBSERVER);

    private static final Collection<Role> OFFICER_ROLES = Arrays.asList(
            Role.XROAD_SECURITY_OFFICER,
            Role.XROAD_REGISTRATION_OFFICER);

    private static final SecurityHelper SECURITY_HELPER = new SecurityHelper(null);

    @BeforeEach
    public void setUp() {
        System.setProperty(NODE_TYPE, "slave"); // secondary node in a cluster
    }

    @AfterClass
    public static void afterClass() {
        System.setProperty(NODE_TYPE, "foo"); // reset the node type property – gibberish/null defaults to STANDALONE
    }

    @Test
    void getNodeTypeAdjustedUserRolesForAdminInSecondaryNode() {
        Set<Role> adjustedRoles = SECURITY_HELPER.getNodeTypeAdjustedUserRoles(ADMIN_ROLES);
        Assertions.assertTrue(adjustedRoles.stream().allMatch(Role.XROAD_SECURITYSERVER_OBSERVER::equals));
    }

    @Test
    void getNodeTypeAdjustedUserRolesForObserverInSecondaryNode() {
        Set<Role> adjustedRoles = SECURITY_HELPER.getNodeTypeAdjustedUserRoles(OBSERVER_ROLES);
        Assertions.assertTrue(adjustedRoles.stream().allMatch(Role.XROAD_SECURITYSERVER_OBSERVER::equals));
    }

    @Test
    void getNodeTypeAdjustedUserRolesForOfficerOnlyInSecondaryNode() {
        Set<Role> adjustedRoles = SECURITY_HELPER.getNodeTypeAdjustedUserRoles(OFFICER_ROLES);
        Assertions.assertTrue(adjustedRoles.isEmpty());
    }

    @Test
    void agetNodeTypeAdjustedUserRolesForAdminInPrimaryNode() {
        System.setProperty(NODE_TYPE, "master"); // primary node in a cluster
        Set<Role> adjustedRoles = SECURITY_HELPER.getNodeTypeAdjustedUserRoles(ADMIN_ROLES);
        Assertions.assertTrue(adjustedRoles.containsAll(ADMIN_ROLES));
    }
}
