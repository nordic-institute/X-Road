package ee.cyber.sdsb.common.util;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class JobManager implements StartStop {

    static {
        // Disable update check
        System.setProperty("org.terracotta.quartz.skipUpdateCheck", "true");
    }

    private static final String DEFAULT_JOB_GROUP = "jobs";

    private Scheduler jobScheduler;

    public JobManager() throws SchedulerException {
        jobScheduler = new StdSchedulerFactory().getScheduler();
    }

    @Override
    public void start() throws Exception {
        jobScheduler.start();
    }

    @Override
    public void stop() throws Exception {
        jobScheduler.shutdown();
    }

    @Override
    public void join() throws InterruptedException {
    }

    public void registerRepeatingJob(Class<? extends Job> jobClass,
            int intervalInSeconds) throws SchedulerException {
        JobDetail job = newJob(jobClass)
                .withIdentity(jobClass.getSimpleName(), DEFAULT_JOB_GROUP)
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(jobClass.getSimpleName(), DEFAULT_JOB_GROUP)
                .withSchedule(simpleSchedule()
                    .withIntervalInSeconds(intervalInSeconds)
                    .repeatForever())
                .startNow()
                .build();

        jobScheduler.scheduleJob(job, trigger);
    }

    public void registerRepeatingJob(Class<? extends Job> jobClass,
            int intervalInSeconds, JobDataMap data) throws SchedulerException {
        JobDetail job = newJob(jobClass)
                .withIdentity(jobClass.getSimpleName(), DEFAULT_JOB_GROUP)
                .usingJobData(data)
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(jobClass.getSimpleName(), DEFAULT_JOB_GROUP)
                .withSchedule(simpleSchedule()
                    .withIntervalInSeconds(intervalInSeconds)
                    .repeatForever())
                .startNow()
                .build();

        jobScheduler.scheduleJob(job, trigger);
    }

    public void registerJob(Class<? extends Job> jobClass,
            String identity, String cronExpression, JobDataMap data)
                    throws SchedulerException {
        JobDetail job = newJob(jobClass)
                .withIdentity(identity, DEFAULT_JOB_GROUP)
                .usingJobData(data)
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(identity, DEFAULT_JOB_GROUP)
                .withSchedule(cronSchedule(cronExpression))
                .startNow()
                .build();

        jobScheduler.scheduleJob(job, trigger);
    }

}
