/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Job that sends messages to actors.
 */
@Slf4j
public class MessageSendingJob implements Job {

    private static final String KEY_ACTOR = "actorSelection";
    private static final String KEY_MESSAGE = "message";

    /**
     * Create job data containing a selection of actors and a message.
     * @param actor a selection of actors that should receive the message
     * @param message message that needs to be sent to actors
     * @return the created job data
     */
    public static JobDataMap createJobData(ActorSelection actor,
            Object message) {
        JobDataMap data = new JobDataMap();
        data.put(KEY_ACTOR, actor);
        data.put(KEY_MESSAGE, message);
        return data;
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        ActorSelection actor = getActor(context);
        if (actor == null) {
            log.error("Cannot execute job, no actor specified");
            return;
        }

        Object message = getMessage(context);
        if (message == null) {
            log.error("Cannot execute job, no message specified");
            return;
        }

        actor.tell(message, ActorRef.noSender());
    }

    private Object getMessage(JobExecutionContext context) {
        JobDataMap data = context.getJobDetail().getJobDataMap();
        return data.get(KEY_MESSAGE);
    }

    private ActorSelection getActor(JobExecutionContext context) {
        JobDataMap data = context.getJobDetail().getJobDataMap();

        Object actor = data.get(KEY_ACTOR);
        if (actor != null && actor instanceof ActorSelection) {
            return (ActorSelection) actor;
        }

        return null;
    }

}
