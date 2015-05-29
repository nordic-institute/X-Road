package ee.ria.xroad.asyncdb.messagequeue;

import java.util.Date;

import lombok.Value;

/**
 * Encapsulates data about current state of message queue.
 */
@Value
public class QueueState {
    private int requestCount;
    private int firstRequestNo;
    private Date lastSentTime;
    private int firstRequestSendCount;
    private String lastSuccessId;
    private Date lastSuccessTime;
    private String lastSendResult;
}
