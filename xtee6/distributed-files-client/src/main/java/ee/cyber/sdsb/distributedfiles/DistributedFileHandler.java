package ee.cyber.sdsb.distributedfiles;

public interface DistributedFileHandler {

    void handle(DistributedFile file) throws Exception;
}
