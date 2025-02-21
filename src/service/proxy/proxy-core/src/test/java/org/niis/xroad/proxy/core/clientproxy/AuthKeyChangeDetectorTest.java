package org.niis.xroad.proxy.core.clientproxy;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.test.keyconf.EmptyKeyConf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthKeyChangeDetectorTest {

    private static final AuthKey KEY1 = mock(AuthKey.class);
    private static final AuthKey KEY2 = mock(AuthKey.class);


    @Test
    void constantKey() {
        TestAuthKeyConfProvider testAuthKeyConfProvider = new TestAuthKeyConfProvider(KEY1);
        AuthKeyChangeDetector authKeyChangeDetector = new AuthKeyChangeDetector(testAuthKeyConfProvider);

        assertFalse(authKeyChangeDetector.hasAuthKeyChanged());
        assertFalse(authKeyChangeDetector.hasAuthKeyChanged());
    }

    @Test
    void keyChanged() {
        TestAuthKeyConfProvider testAuthKeyConfProvider = new TestAuthKeyConfProvider(KEY1);
        AuthKeyChangeDetector authKeyChangeDetector = new AuthKeyChangeDetector(testAuthKeyConfProvider);

        assertFalse(authKeyChangeDetector.hasAuthKeyChanged());

        testAuthKeyConfProvider.setAuthKey(KEY2);

        assertTrue(authKeyChangeDetector.hasAuthKeyChanged());
    }


    @Getter
    @Setter
    static class TestAuthKeyConfProvider extends EmptyKeyConf {
        private AuthKey authKey;

        TestAuthKeyConfProvider(AuthKey authKey) {
            this.authKey = authKey;
        }
    }

}
