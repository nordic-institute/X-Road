package ee.ria.xroad.common.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.assertNull;

/**
 * Tests to verify CPU idle percent calculator behavior.
 */
@Slf4j
public class IdlePercentCalculatorBehavior {

    /**
     * Just to see if we can get realistic idle percent from realistic data.
     */
    @Test
    public void shouldCalculateIdlePercent() {
        CpuStats previous = new CpuStats(
                27232919, 16826, 64593485, 188316115, 672027, 4, 4882, 0);
        CpuStats current = new CpuStats(
                27233052, 16826, 64593978, 188317718, 672029, 4, 4882, 0);
        double idlePercent = IdlePercentCalculator.calculate(
                previous, current);

        log.info("Idle percent: '{}'", idlePercent);
    }

    /**
     * Tests that the result is null if there's no previous result.
     */
    @Test
    public void shouldReturnNullIfPreviousCpuStatsIsNull() {
        CpuStats previous = null;
        CpuStats current = new CpuStats(
                27233052, 16826, 64593978, 188317718, 672029, 4, 4882, 0);
        Double idlePercent = IdlePercentCalculator.calculate(
                previous, current);

        assertNull(idlePercent);
    }

    /**
     * Tests that the result is null if there's no current result.
     */
    @Test
    public void shouldReturnNullIfCurrentCpuStatsIsNull() {
        CpuStats previous = new CpuStats(
                27232919, 16826, 64593485, 188316115, 672027, 4, 4882, 0);
        CpuStats current = null;
        Double idlePercent = IdlePercentCalculator.calculate(
                previous, current);

        assertNull(idlePercent);
    }
}
