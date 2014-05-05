package ee.cyber.sdsb.distributedfiles.handler;

import java.io.File;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.util.AtomicSave;
import ee.cyber.sdsb.distributedfiles.DistributedFile;
import ee.cyber.sdsb.distributedfiles.DistributedFileHandler;

public class DefaultFileHandler implements DistributedFileHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultFileHandler.class);

    @Override
    public void handle(final DistributedFile file) throws Exception {
        LOG.trace("Received file {}", file.getFileName());

        DateTime date = file.getSignatureDate();
        String targetFile = SystemProperties.getConfPath() + file.getFileName();

        LOG.debug("Saving file (signed at {}) to {}", date, targetFile);
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
            LOG.error("Failed to save global conf", e);
        }
    }

}
