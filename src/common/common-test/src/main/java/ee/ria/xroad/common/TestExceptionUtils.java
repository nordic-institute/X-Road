package ee.ria.xroad.common;

import org.assertj.core.api.Condition;

public final class TestExceptionUtils {

    private TestExceptionUtils() {
    }

    public static Condition<Throwable> codedException(String faultCode) {
        return new Condition<>(
                t -> t instanceof CodedException codedException && codedException.getFaultCode().equals(faultCode),
                "CodedException with fault code '%s'", faultCode);
    }

}
