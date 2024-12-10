package ee.ria.xroad.common.messagelog;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import java.io.InputStream;
import java.sql.Blob;


@Slf4j
@ToString(callSuper = true, exclude = {"attachment", "attachmentCipher"})
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

    @Setter
    private transient Cipher attachmentCipher;

    public MessageAttachment(MessageRecord logRecord, Integer attachmentNo, Blob attachment) {
        this.logRecord = logRecord;
        this.attachmentNo = attachmentNo;
        this.attachment = attachment;
    }

    public InputStream getInputStream() {
        try {
            if (attachmentCipher != null) {
                return new CipherInputStream(attachment.getBinaryStream(), attachmentCipher);
            }
            return attachment.getBinaryStream();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasCipher() {
        return attachmentCipher != null;
    }

}
