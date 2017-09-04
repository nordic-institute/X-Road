package ee.ria.xroad.monitor;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static junit.framework.TestCase.fail;

/**
 * MetricsRegistryHolderTest
 */
@Slf4j
public class MetricRegistryHolderTest{

    @Test
    public void testGetOrCreateSimpleSensor() {

        try {
            MetricRegistryHolder holder = MetricRegistryHolder.getInstance();
            holder.getOrCreateSimpleSensor("Testi");
            holder.getOrCreateSimpleSensor("Testi");
        } catch (Exception e) {
            fail("Exception should not have thrwon.");
        }

    }
}
