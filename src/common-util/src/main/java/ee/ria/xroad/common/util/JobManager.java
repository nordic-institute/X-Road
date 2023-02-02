/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Date;
import java.util.List;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Service to manage periodic jobs.
 */
@Slf4j
public class JobManager implements StartStop {

    static {
        // Disable update check
        System.setProperty("org.terracotta.quartz.skipUpdateCheck", "true");
    }

    private static final String DEFAULT_JOB_GROUP = "jobs";

    @Getter
    private Scheduler jobScheduler;

    /**
     * Creates a new job manager.
     *
     * @throws SchedulerException if there is a problem with the underlying Scheduler
     */
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
        // not applicable
    }

    /**
     * Registers a repeating job with the specified repeat interval.
     *
     * @param jobClass          class of the job that needs to be repeated
     * @param intervalInSeconds repeat interval of the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     *                            or there is an internal Scheduler error
     */
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

    /**
     * Registers a repeating job with the specified repeat interval.
     *
     * @param jobClass          class of the job that needs to be repeated
     * @param intervalInSeconds repeat interval of the job
     * @param data              state information for the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     *                            or there is an internal Scheduler error
     */
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

    /**
     * Registers a job that should be executed with the given cron expression.
     *
     * @param jobClass       class of the job that needs to be repeated
     * @param cronExpression the cron expression used to execute the job
     * @param data           state information for the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     *                            or there is an internal Scheduler error
     */
    public void registerJob(Class<? extends Job> jobClass, String cronExpression, JobDataMap data)
            throws SchedulerException {
        registerJob(jobClass, jobClass.getSimpleName(), cronExpression, data);
    }

    /**
     * Registers a job that should be executed with the given cron expression.
     *
     * @param jobClass       class of the job that needs to be repeated
     * @param identity       name to identify the job by
     * @param cronExpression the cron expression used to execute the job
     * @param data           state information for the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     *                            or there is an internal Scheduler error
     */
    public void registerJob(Class<? extends Job> jobClass,
                            String identity, String cronExpression, JobDataMap data)
            throws SchedulerException {
        if (CronExpression.isValidExpression(cronExpression)) {
            JobDetail job = newJob(jobClass)
                    .withIdentity(identity, DEFAULT_JOB_GROUP)
                    .usingJobData(data)
                    .build();

            Trigger trigger = newTrigger()
                    .withIdentity(identity, DEFAULT_JOB_GROUP)
                    .withSchedule(cronSchedule(cronExpression))
                    .startNow()
                    .build();

            Date nextTrigger = jobScheduler.scheduleJob(job, trigger);
            log.info("Starting scheduled job {} with a schedule [{}]. Next execution: {}", jobClass.getSimpleName(),
                    cronExpression, nextTrigger);
        } else {
            log.error("Failed to start scheduled job {} with a schedule [{}]", jobClass.getSimpleName(), cronExpression);
        }
    }

    /**
     * Check if specified job is currently executing/running within any group.
     *
     * @param ctx      execution context which is verified
     * @param jobClass job class to check
     * @return job running state
     * @throws SchedulerException is thrown of there is a failure to fetch running jobs
     */
    public static boolean isJobRunning(JobExecutionContext ctx, Class<? extends Job> jobClass)
            throws SchedulerException {
        List<JobExecutionContext> currentJobs = ctx.getScheduler().getCurrentlyExecutingJobs();

        for (JobExecutionContext jobCtx : currentJobs) {
            if (jobCtx.getJobDetail().getJobClass().isAssignableFrom(jobClass)
                    && !jobCtx.getFireTime().equals(ctx.getFireTime())) {
                return true;
            }
        }
        return false;
    }

}
