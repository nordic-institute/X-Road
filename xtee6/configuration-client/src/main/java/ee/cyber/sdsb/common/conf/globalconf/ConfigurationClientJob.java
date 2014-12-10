package ee.cyber.sdsb.common.conf.globalconf;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Quartz job implementation for the configuration client.
 */
public class ConfigurationClientJob implements Job {

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        JobDataMap data = context.getJobDetail().getJobDataMap();

        Object client = data.get("client");

        if (client != null && client instanceof ConfigurationClient) {
            try {
                ((ConfigurationClient) client).execute();
            } catch (Exception e) {
                throw new JobExecutionException(e);
            }
        } else {
            throw new JobExecutionException(
                    "Could not get configuration client from job data");
        }
    }
}
