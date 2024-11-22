package ee.ria.xroad.common.messagelog;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.sql.Blob;


@Slf4j
@ToString(callSuper = true, exclude = {"attachment"})
@EqualsAndHashCode(exclude = {"attachment"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageAttachment {

    @Getter
    private Long id;

    @Getter
    @Setter
    private MessageRecord logRecord;

    @Getter
    @Setter
    private Integer attachmentNo;

    @Getter
    @Setter
    private Blob attachment;

    public MessageAttachment(MessageRecord logRecord, Integer attachmentNo, Blob attachment) {
        this.logRecord = logRecord;
        this.attachmentNo = attachmentNo;
        this.attachment = attachment;
    }

}
