package org.niis.xroad.restapi.config.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks controller methods that are linked to a named audit event.
 * Method success / fail is audit logged.
 * Fail means method threw an exception
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditEventMethod {
    String eventName();
}