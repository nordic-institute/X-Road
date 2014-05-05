package ee.cyber.sdsb.common;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;

public class TestQuery {
    private String content;
    private String name;
    private String consumerName;
    private String producerName;

    public TestQuery(String rawContent, String name, String consumerName,
            String producerName) {
        String id = UUID.randomUUID().toString();
        this.content = changeHeaderValues(rawContent, consumerName,
                producerName, id);
        this.name = name;
        this.consumerName = consumerName;
        this.producerName = producerName;
    }

    public TestQuery(File queryFile, String name, String consumerName,
            String producerName) throws IOException {
        this(FileUtils.readFileToString(queryFile), name, consumerName,
                producerName);
    }

    public String getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getProducerName() {
        return producerName;
    }

    private static String changeHeaderValues(String message,
            String consumerName, String producerName, String id) {
        String result = message;
        result = result.replaceAll("(<sdsb:consumer>).*(</sdsb:consumer>)",
                "$1" + consumerName + "$2");
        result = result.replaceAll("(<sdsb:producer>).*(</sdsb:producer>)",
                "$1" + producerName + "$2");
        result = result.replaceAll(
                "(<sdsb:service>)(.*)[.](.*)(</sdsb:service>)", "$1"
                        + producerName + ".$3$4");
        result = result.replaceAll("(<sdsb:id>).*(</sdsb:id>)", "$1" + id
                + "$2");
        return result;
    }
}
