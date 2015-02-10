package ee.cyber.sdsb.common.conf.globalconf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.globalconf.sharedparameters.IdentifierDecoderType;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

@Slf4j
final class IdentifierDecoderHelper {

    private IdentifierDecoderHelper() {
    }

    static ClientId getSubjectName(X509Certificate cert,
            IdentifierDecoderType decoder, String instanceIdentifier)
                    throws Exception {
        String methodName = StringUtils.trim(decoder.getMethodName());
        Method method;
        try {
            method = getMethodFromClassName(methodName, X509Certificate.class);
        } catch (ClassNotFoundException e) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not find identifier decoder: '%s'", methodName);
        } catch (Exception e) {
            log.error("Could not get identifier decoder method '"
                    + methodName + "'", e);
            throw new CodedException(X_INTERNAL_ERROR, e);
        }

        Object result;
        try {
            result = method.invoke(null /* Static, no instance */, cert);
        } catch (Exception e) {
            Throwable t = (e instanceof InvocationTargetException)
                    ? e.getCause() : e;
            log.error("Error during extraction of subject name from "
                    + "certificate '" + cert.getSubjectDN() + "' using "
                    + "identifier decoder '" + methodName + "'", t);
            throw new CodedException(X_INCORRECT_CERTIFICATE, t);
        }

        if (result == null) {
            throw new CodedException(X_INCORRECT_CERTIFICATE,
                    "Could not get SubjectName from certificate "
                            + cert.getSubjectDN());
        }

        if (result instanceof String) {
            return ClientId.create(instanceIdentifier,
                    decoder.getMemberClass(), (String) result);
        } else if (result instanceof String[]) {
            String[] parts = (String[]) result;
            if (parts.length == 2) {
                return ClientId.create(instanceIdentifier, parts[0], parts[1]);
            }
        } else if (result instanceof ClientId) {
            return (ClientId) result;
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Unexpected result from identifier decoder: "
                        + result.getClass());
    }

    /**
     * Returns a method from the class and method name string. Assumes, the
     * class name is in form [class].[method]
     */
    private static Method getMethodFromClassName(String classAndMethodName,
            Class<?>... parameterTypes) throws Exception {
        int lastIdx = classAndMethodName.lastIndexOf('.');
        if (lastIdx == -1) {
            throw new IllegalArgumentException(
                    "classAndMethodName must be in form of <class>.<method>");
        }

        String className = classAndMethodName.substring(0, lastIdx);
        String methodName = classAndMethodName.substring(lastIdx + 1);

        Class<?> clazz = Class.forName(className);
        return clazz.getMethod(methodName, parameterTypes);
    }
}
