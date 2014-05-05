package ee.cyber.sdsb.commonui.jaas;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;

public class PAMLoginModule implements LoginModule {

    private static String PAM_SERVICE_NAME = "sdsb";

    private Subject subject;
    private CallbackHandler callbackHandler;

    private UnixUser currentUser;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
            Map<String,?> sharedState, Map<String,?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public boolean login() throws LoginException {
        currentUser = null;

        try {
            if (callbackHandler == null) {
                throw new LoginException ("No callback handler");
            }

            Callback[] callbacks = getCallbacks();
            callbackHandler.handle(callbacks);

            String webName = ((NameCallback) callbacks[0]).getName();
            String webPassword = new String(
                ((PasswordCallback) callbacks[1]).getPassword());

            if (webName == null || webPassword == null) {
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
            e.printStackTrace();
            throw new LoginException(e.toString());
        }
    }

    @Override
    public boolean commit() throws LoginException {
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

        return true;
    }

    private Callback[] getCallbacks() {
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Enter username");
        callbacks[1] = new PasswordCallback("Enter password", false);

        return callbacks;
    }
}
