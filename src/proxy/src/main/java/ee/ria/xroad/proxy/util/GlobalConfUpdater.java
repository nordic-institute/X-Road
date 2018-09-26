package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Periodic reload of global configuration
 */
@Slf4j
@DisallowConcurrentExecution
public class GlobalConfUpdater implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.trace("Reloading globalconf");
            GlobalConf.reloadIfChanged();
        } catch (Exception e) {
            log.error("Error reloading globalconf", e);
            throw new JobExecutionException(e);
        }
    }
}
