package ee.cyber.sdsb.distributedfiles;

import java.io.InputStream;

import lombok.Value;

import org.joda.time.DateTime;

@Value
public class DistributedFile {

    private final String fileName;
    private final DateTime signatureDate;
    private final InputStream content;

}
