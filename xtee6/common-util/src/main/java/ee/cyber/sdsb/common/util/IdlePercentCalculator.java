package ee.cyber.sdsb.common.util;

/**
 * Calculates idle percent the same way as UNIX 'top' utility does.
 */
public class IdlePercentCalculator {

    public static Double calculate(CpuStats previous, CpuStats current) {
        if (previous == null || current == null) {
            return null;
        }

        double userDiff = current.getUser() - previous.getUser();
        double niceDiff = current.getNice() - previous.getNice();
        double systemDiff = current.getSystem() - previous.getSystem();
        double idleDiff = getIdleDiff(previous, current);
        double iowaitDiff = current.getIowait() - previous.getIowait();
        double irqDiff = current.getIrq() - previous.getIrq();
        double softirqDiff = current.getSoftirq() - previous.getSoftirq();
        double stealDiff = current.getSteal() - previous.getSteal();

        double totalDiff = userDiff + niceDiff + systemDiff + idleDiff
                + iowaitDiff + irqDiff + softirqDiff + stealDiff;

        if (totalDiff < 1) {
            totalDiff = 1;
        }

        double scale = 100 / totalDiff;

        return idleDiff * scale;
    }

    private static double getIdleDiff(CpuStats previous, CpuStats current) {
        double diff = current.getIdle() - previous.getIdle();

        return diff < 0 ? 0 : diff;
    }
}
