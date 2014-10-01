package ee.cyber.sdsb.common.util;

import lombok.extern.slf4j.Slf4j;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;

@Slf4j
public class MessageSendingJob implements Job {

    private static final String KEY_ACTOR = "actorSelection";
    private static final String KEY_MESSAGE = "message";

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
