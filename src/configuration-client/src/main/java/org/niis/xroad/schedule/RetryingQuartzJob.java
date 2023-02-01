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
package org.niis.xroad.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Base class for Quartz jobs which should reschedule their execution and retry later if specified conditions are met.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class RetryingQuartzJob implements Job {
    private static final String RETRY_COUNT = "retryCount";
    private static final String RETRY_GROUP = "retryGroup";

    private final int retryDelay;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            if (shouldRescheduleRetry(context)) {
                log.warn("Job {} is already running. Will retry in {} seconds", context.getJobDetail().getKey().getName(),
                        retryDelay);
                scheduleRetry(context);
            } else {
                executeWithRetry(context);
            }
        } catch (JobExecutionException e) {
            log.error("Error executing job.", e);
            //JobExecutionException can be just rethrown.
            throw e;
        } catch (Exception e) {
            log.error("Error executing job.", e);
            throw new JobExecutionException(e);
        }
    }

    /**
     * Job code path which is executed if retry conditions allows it.
     *
     * @param context quartz job context
     */
    protected abstract void executeWithRetry(JobExecutionContext context) throws Exception;

    /**
     * A condition which defined whether job should be rescheduled.
     *
     * @param context quartz job context
     * @return true if has to be rescheduled
     */
    protected abstract boolean shouldRescheduleRetry(JobExecutionContext context) throws SchedulerException;

    /**
     * Add retry trigger to current scheduler.
     *
     * @param context quartz job context
     */
    private void scheduleRetry(JobExecutionContext context) throws SchedulerException {
        Trigger trigger = createRetryTrigger(context.getJobDetail(), context.getTrigger(), retryDelay);

        context.getScheduler().scheduleJob(trigger);
    }

    /**
     * Creates a one time trigger which is marked as a retry.
     *
     * @param jobDetail job detail to use
     * @param delay     delay in seconds
     */
    private Trigger createRetryTrigger(JobDetail jobDetail, Trigger lastTrigger, int delay) {
        final String jobName = jobDetail.getKey().getName();
        final int retryCount = lastTrigger.getJobDataMap().containsKey(RETRY_COUNT)
                ? lastTrigger.getJobDataMap().getIntValue(RETRY_COUNT) : 0;
        final int newRetryCount = retryCount + 1;
        return newTrigger()
                .forJob(jobName, jobDetail.getKey().getGroup())
                .withIdentity(jobName + "-" + newRetryCount, RETRY_GROUP)
                .usingJobData(RETRY_COUNT, newRetryCount)
                .startAt(DateBuilder.futureDate(delay, DateBuilder.IntervalUnit.SECOND))
                .build();
    }
}
