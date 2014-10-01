package ee.cyber.sdsb.common.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static org.junit.Assert.assertNull;

@Slf4j
public class IdlePercentCalculatorBehavior {

    /**
     * Just to see if we can get realistic idle percent from realistic data.
     * */
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

    @Test
    public void shouldReturnNullIfPreviousCpuStatsIsNull() {
        CpuStats previous = null;
        CpuStats current = new CpuStats(
                27233052, 16826, 64593978, 188317718, 672029, 4, 4882, 0);
        Double idlePercent = IdlePercentCalculator.calculate(
                previous, current);

        assertNull(idlePercent);
    }

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
