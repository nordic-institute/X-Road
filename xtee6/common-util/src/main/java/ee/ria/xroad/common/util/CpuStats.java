package ee.ria.xroad.common.util;

import lombok.Value;

/**
 * Represents summary processor statistics (first line of /proc/stat).
 */
@Value
public class CpuStats {
    private double user; // u
    private double nice; // n
    private double system; // s
    private double idle; // i
    private double iowait; // w
    private double irq; // x
    private double softirq; // y
    private double steal; // z
}
