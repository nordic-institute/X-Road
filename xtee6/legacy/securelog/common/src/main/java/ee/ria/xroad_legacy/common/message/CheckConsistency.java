package ee.ria.xroad_legacy.common.message;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that is used in SoapHeader to indicate which fields should be
 * checked for consistency.
 */
@Retention(RetentionPolicy.RUNTIME)
@interface CheckConsistency {

}
