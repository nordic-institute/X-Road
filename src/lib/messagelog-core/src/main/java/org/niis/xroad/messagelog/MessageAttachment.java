/*
 * The MIT License
 *
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
package org.niis.xroad.messagelog;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

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
    private Integer attachmentNo;

    @Getter
    @Setter
    private Blob attachment;

    @Setter
    private transient Cipher attachmentCipher;

    public MessageAttachment(Integer attachmentNo, Blob attachment) {
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
            throw XrdRuntimeException.systemException(e);
        }
    }

    public boolean hasCipher() {
        return attachmentCipher != null;
    }

}
