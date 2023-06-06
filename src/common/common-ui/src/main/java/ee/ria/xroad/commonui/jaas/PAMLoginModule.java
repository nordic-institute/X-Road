/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.commonui.jaas;

import ee.ria.xroad.common.AuditLogger;

import lombok.extern.slf4j.Slf4j;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.UnixUser;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import java.io.IOException;
import java.util.Map;

/**
 * The PAM login module implementation.
 */
@Slf4j
public class PAMLoginModule implements LoginModule {

    private static final String PAM_SERVICE_NAME = "xroad";

    private Subject subject;
    private CallbackHandler callbackHandler;

    private String webName;
    private UnixUser currentUser;

    @Override
    public void initialize(Subject subj, CallbackHandler callback,
                           Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subj;
        this.callbackHandler = callback;
    }

    @Override
    public boolean login() throws LoginException {
        currentUser = null;

        try {
            if (callbackHandler == null) {
                throw new LoginException("No callback handler");
            }

            Callback[] callbacks = getCallbacks();
            callbackHandler.handle(callbacks);

            webName = ((NameCallback) callbacks[0]).getName();
            String webPassword = new String(
                    ((PasswordCallback) callbacks[1]).getPassword());

            if (webName == null) {
                return false;
            }

            PAM pam = new PAM(PAM_SERVICE_NAME);
            currentUser = pam.authenticate(webName, webPassword);

            return currentUser != null;
        } catch (IOException e) {
            throw new LoginException(e.toString());
        } catch (UnsupportedCallbackException e) {
            throw new LoginException(e.toString());
        } catch (Exception e) {
            throw new LoginException(e.toString());
        }
    }

    @Override
    public boolean commit() throws LoginException {
        AuditLogger.log("Log in user", webName, null, null);
        webName = null;

        if (currentUser == null) {
            return false;
        }

        subject.getPrincipals().add(
                new JAASPrincipal(currentUser.getUserName()));

        for (String group : currentUser.getGroups()) {
            subject.getPrincipals().add(new JAASRole(group));
        }

        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        AuditLogger.log("Log in user failed", webName, null, null);
        webName = null;

        if (currentUser == null) {
            return false;
        }

        currentUser = null;
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        if (currentUser == null) {
            return false;
        }

        subject.getPrincipals().remove(
                new JAASPrincipal(currentUser.getUserName()));

        for (String group : currentUser.getGroups()) {
            subject.getPrincipals().remove(new JAASRole(group));
        }

        AuditLogger.log("Log out user", currentUser.getUserName(), null, null);

        return true;
    }

    private Callback[] getCallbacks() {
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Enter username");
        callbacks[1] = new PasswordCallback("Enter password", false);

        return callbacks;
    }
}
