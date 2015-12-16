package ee.cyber.xroad.confimporter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.mediator.util.MediatorUtils;

/**
 * Encapsulates reading of V5 configuration from the filesystem.
 */
@Slf4j
public class XConf {

    private static final String XTEETOP = "/usr/xtee";
    private static final String XCONF_PATH = "/etc/xtee";

    private static final String PRODUCERS_PATH = "/proxy/producers/producers";
    private static final String CONSUMERS_PATH = "/proxy/consumers/consumers";

    private static final int ACL_ENTRY_PARTS = 2;

    XLock lock;

    /**
     * Read the V5 configuration from disk.
     */
    public XConf() {
        lock = new XLock(rootDir());
    }

    /**
     * Lock the configuration for reading.
     */
    public void readLock() {
        lock.readLock();
    }

    /**
     * Unlock the configuration after reading.
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * @return the list of configured consumers
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public List<Consumer> getConsumers() throws IOException {
        List<Consumer> result = new ArrayList<>();

        for (String shortName : listSubdirs(CONSUMERS_PATH)) {
            result.add(new Consumer(shortName));
        }

        return result;
    }

    /**
     * @return the list of configured producers
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public List<Producer> getProducers() throws IOException {
        List<Producer> result = new ArrayList<>();

        for (String shortName : listSubdirs(PRODUCERS_PATH)) {
            result.add(new Producer(shortName));
        }

        return result;
    }

    /**
     * @param producer the producer
     * @return the list of services configured for the producer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public List<String> getServices(Producer producer) throws IOException {
        return readStringList(producer.getPath("/query_list"));
    }

    /**
     * @param producer the producer
     * @return the list of clients authorized by the given producer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public Map<String, List<String>> getServiceAuthorizedClients(
            Producer producer) throws IOException {
        return getServiceAuthorizedSubjects(producer, "/acl");
    }

    /**
     * @param producer the producer
     * @return the list of groups authorized by the given producer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public Map<String, List<String>> getServiceAuthorizedGroups(
            Producer producer) throws IOException {
        return getServiceAuthorizedSubjects(producer, "/gacl");
    }

    private Map<String, List<String>> getServiceAuthorizedSubjects(
            Producer producer, String aclFile) throws IOException {

        Map<String, List<String>> result = new HashMap<>();

        String confPath = producer.getPath(aclFile);

        for (String entry : readStringList(confPath)) {
            String[] splitEntry = entry.split(" ");

            if (splitEntry.length != ACL_ENTRY_PARTS) {
                log.warn("Invalid ACL entry: '{}', skiping", entry);

                continue;
            }

            String subjectShortName = splitEntry[0];
            String serviceCode = MediatorUtils.extractServiceCode(splitEntry[1]);

            if (serviceCode == null) {
                log.warn("Invalid service name in ACL entry: '{}', skipping",
                        splitEntry[1]);

                continue;
            }

            if (!result.containsKey(serviceCode)) {
                result.put(serviceCode, new ArrayList<>());
            }

            result.get(serviceCode).add(subjectShortName);
        }

        return result;
    }

    /**
     * @param producer the producer
     * @return the adapter URL configured for the given producer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public String getAdapterURL(Producer producer) throws IOException {
        String adapterBaseURL = getAdapterBaseURL(producer);
        String adapterURI = readString(producer.getPath("/adapter_uri"));

        if (adapterBaseURL == null || adapterURI == null) {
            return null;
        }

        return adapterBaseURL + adapterURI;
    }

    private String getAdapterBaseURL(Producer producer) throws IOException {
        String peerType = readString(producer.getPath("/peertype"), "http");
        String peerIP = readString(producer.getPath("/peerip"));
        String peerPort = readString(producer.getPath("/peerport"));

        if (peerType.startsWith("https")) {
            peerType = "https";
        }

        if (peerIP == null || peerPort == null) {
            return null;
        }

        return peerType + "://" + peerIP + ":" + peerPort;
    }

    /**
     * @param producer the producer
     * @return the service timeout configured for the given producer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public int getServiceTimeout(Producer producer) throws IOException {
        String timeout = readString(producer.getPath("/consumer_timeout"));

        return timeout == null ? 0 : Integer.parseInt(timeout);
    }

    /**
     * @param org the producer or consumer
     * @return the peer authentication type configured for the given producer/consumer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public String getPeerType(Org org) throws IOException {
        return readString(org.getPath("/peertype"), "http");
    }

    /**
     * @param org the producer or consumer
     * @return the list of SSL certificates configured for the producer/consumer
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public List<byte[]> getInternalSSLCerts(Org org) throws IOException {
        List<byte[]> result = new ArrayList<>();

        for (String certFile : listFiles(org.getPath("/https_certs"))) {
            result.add(readBytes(org.getPath("/https_certs/" + certFile)));
        }

        return result;
    }

    /**
     * @param producer the producer
     * @return true, if the adapter configured for the given producer exists
     * @throws IOException in case errors occur when reading from the filesystem
     */
    public boolean adapterExists(Producer producer) throws IOException {
        return !"0.0.0.0".equals(
            readString(producer.getPath("/peerip"), "0.0.0.0"));
    }

    private String rootDir() {
        String xteetop = System.getenv("XTEETOP");
        if (xteetop == null) {
            xteetop = XTEETOP;
        }

        return xteetop + XCONF_PATH;
    }

    private Path toFullPath(String confPath) {
        return Paths.get(rootDir(), confPath);
    }

    private List<String> listFiles(String confPath) throws IOException {
        List<String> result = new ArrayList<>();

        Path path = toFullPath(confPath);

        if (!Files.exists(path)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    result.add(entry.getFileName().toString());
                }
            }
        }

        return result;
    }

    private List<String> listSubdirs(String confPath) throws IOException {
        List<String> result = new ArrayList<>();

        Path path = toFullPath(confPath);

        if (!Files.exists(path)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    result.add(entry.getFileName().toString());
                }
            }
        }

        return result;
    }

    private byte[] readBytes(String confPath) throws IOException {
        Path path = toFullPath(confPath);

        if (!Files.exists(path)) {
            return null;
        }

        return Files.readAllBytes(path);
    }

    private String readString(String confPath, String defaultVal)
            throws IOException {
        String result = readString(confPath, StandardCharsets.UTF_8);
        return result == null ? defaultVal : result;
    }

    private String readString(String confPath) throws IOException {
        return readString(confPath, StandardCharsets.UTF_8);
    }

    private String readString(String confPath, Charset charset)
            throws IOException {
        Path path = toFullPath(confPath);

        if (!Files.exists(path)) {
            return null;
        }

        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, charset);
    }

    private List<String> readStringList(String confPath) throws IOException {
        Path path = toFullPath(confPath);

        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    abstract static class Org {
        private String shortName;

        Org(String shortName) {
            this.shortName = shortName;
        }

        public String getShortName() {
            return shortName;
        }

        public String getPath(String field) {
            return getPath() + field;
        }

        public abstract String getPath();
    }

    /**
     * Represents a consumer.
     */
    public static class Consumer extends Org {
        Consumer(String shortName) {
            super(shortName);
        }

        @Override
        public String getPath() {
            return CONSUMERS_PATH + "/" + getShortName();
        }
    }

    /**
     * Represents a producer.
     */
    public static class Producer extends Org {
        Producer(String shortName) {
            super(shortName);
        }

        @Override
        public String getPath() {
            return PRODUCERS_PATH + "/" + getShortName();
        }
    }
}
