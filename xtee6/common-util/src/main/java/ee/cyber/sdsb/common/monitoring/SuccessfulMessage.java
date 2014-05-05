package ee.cyber.sdsb.common.monitoring;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class SuccessfulMessage implements Serializable {

    private static final long serialVersionUID = -7771405120786047700L;

    public final MessageInfo message;
    public final Date startTime;
    public final Date endTime;

    public SuccessfulMessage(MessageInfo message, Date startTime,
            Date endTime) {
        this.message = message;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
