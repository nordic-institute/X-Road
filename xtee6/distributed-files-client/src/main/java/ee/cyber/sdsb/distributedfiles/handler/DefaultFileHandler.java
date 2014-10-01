package ee.cyber.sdsb.distributedfiles.handler;

import java.io.File;
import java.io.OutputStream;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AtomicSave;
import ee.cyber.sdsb.distributedfiles.DistributedFile;
import ee.cyber.sdsb.distributedfiles.DistributedFileHandler;

@Slf4j
public class DefaultFileHandler implements DistributedFileHandler {

    @Override
    public void handle(final DistributedFile file) throws Exception {
        log.trace("Received file {}", file.getFileName());

        DateTime date = file.getSignatureDate();
        String targetFile = SystemProperties.getConfPath() + file.getFileName();

        log.debug("Saving file (signed at {}) to {}", date, targetFile);
        try {
            AtomicSave.execute(targetFile, "tmpconf",
                    new AtomicSave.Callback() {
                @Override
                public void save(OutputStream out) throws Exception {
                    IOUtils.copy(file.getContent(), out);
                }
            });

            // Store the signature time in files last modified field
            new File(targetFile).setLastModified(date.getMillis());
        } catch (Exception e) {
            log.error("Failed to save global conf", e);
        }
    }

}
