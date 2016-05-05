/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
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

/**
 * Service to manage periodic jobs.
 */
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
    }

    /**
     * Registers a repeating job with the specified repeat interval.
     * @param jobClass class of the job that needs to be repeated
     * @param intervalInSeconds repeat interval of the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     * or there is an internal Scheduler error
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
     * @param jobClass class of the job that needs to be repeated
     * @param intervalInSeconds repeat interval of the job
     * @param data state information for the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     * or there is an internal Scheduler error
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
     * @param jobClass class of the job that needs to be repeated
     * @param identity name to identify the job by
     * @param cronExpression the cron expression used to execute the job
     * @param data state information for the job
     * @throws SchedulerException if the Job or Trigger cannot be added to the Scheduler,
     * or there is an internal Scheduler error
     */
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
