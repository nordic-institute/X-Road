package org.niis.xroad.restapi.exceptions;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Utilities for working with {@link org.springframework.web.bind.annotation.ResponseStatus}
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class ResponseStatusUtil {
   /**
     * Get correct HTTP status from {@link org.springframework.web.bind.annotation.ResponseStatus} annotation,
     * if one exists. If not, use the defaultStatus
     */
    public static HttpStatus getAnnotatedResponseStatus(Throwable t, HttpStatus defaultStatus) {
        HttpStatus status = defaultStatus;
        ResponseStatus statusAnnotation = AnnotationUtils.findAnnotation(
                t.getClass(), ResponseStatus.class);
        if (statusAnnotation != null) {
            status = statusAnnotation.value();
        }
        return status;
    }
}
