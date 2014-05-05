package ee.cyber.xroad.serviceimporter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class XConf {

    private static final Logger LOG = LoggerFactory.getLogger(XConf.class);

    private static final String XTEETOP = "/usr/xtee";
    private static final String XCONF_PATH = "/etc/xtee";

    private static final String PRODUCERS_PATH = "/proxy/producers/producers";
    private static final String CONSUMERS_PATH = "/proxy/consumers/consumers";

    // A regex to check for UTF-8 encoding.
    // http://www.w3.org/International/questions/qa-forms-utf-8
    private static Pattern UTF_8_PATTERN = Pattern.compile(
        "([\\x09\\x0A\\x0D\\x20-\\x7E]" +
        "|[\\xC2-\\xDF][\\x80-\\xBF]" +
        "|\\xE0[\\xA0-\\xBF][\\x80-\\xBF]" +
        "|[\\xE1-\\xEC\\xEE\\xEF][\\x80-\\xBF]{2}" +
        "|\\xED[\\x80-\\x9F][\\x80-\\xBF]" +
        "|\\xF0[\\x90-\\xBF][\\x80-\\xBF]{2}" +
        "|[\\xF1-\\xF3][\\x80-\\xBF]{3}" +
        "|\\xF4[\\x80-\\x8F][\\x80-\\xBF]{2})*");

    XLock lock;

    public XConf() {
        lock = new XLock(rootDir());
    }

    public void readLock() {
        lock.readLock();
    }

    public void writeLock() {
        lock.writeLock();
    }

    public void unlock() {
        lock.unlock();
    }

    public List<Consumer> getConsumers() throws IOException {
        List<Consumer> result = new ArrayList<>();

        for (String shortName : listSubdirs(CONSUMERS_PATH)) {
            result.add(new Consumer(shortName));
        }

        return result;
    }

    public List<Producer> getProducers() throws IOException {
        List<Producer> result = new ArrayList<>();

        for (String shortName : listSubdirs(PRODUCERS_PATH)) {
            result.add(new Producer(shortName));
        }

        return result;
    }

    public String getFullName(Org org) throws IOException {
        String path = org.getPath("/fullname");

        // Fullname-s could be ISO-8859-15 or UTF-8 encoded. Let's read
        // the file as ISO-8859-1 to translate the sequence of bytes
        // to sequence of characters with exact same codepoint
        // values. Then we can check if it is UTF-8 using regex.
        String fullName = readString(path, StandardCharsets.ISO_8859_1);

        return UTF_8_PATTERN.matcher(fullName).matches()
            ? readString(path, StandardCharsets.UTF_8)
            : readString(path, Charset.forName("ISO-8859-15"));
    }

    public List<String> getServices(Producer producer) throws IOException {
        return readStringList(producer.getPath("/query_list"));
    }

    public Map<String, List<String>> getServiceAuthorizedClients(
            Producer producer) throws IOException {
        return getServiceAuthorizedSubjects(producer, "/acl");
    }

    public Map<String, List<String>> getServiceAuthorizedGroups(
            Producer producer) throws IOException {
        return getServiceAuthorizedSubjects(producer, "/gacl");
    }

    public Map<String, List<String>> getServiceAuthorizedSubjects(
            Producer producer, String aclFile) throws IOException {

        Map<String, List<String>> result = new HashMap<>();

        String confPath = producer.getPath(aclFile);

        for (String entry : readStringList(confPath)) {
            String[] splitEntry = entry.split(" ");
            String subjectShortName = splitEntry[0];
            String serviceCode = splitEntry[1].split("\\.")[1];

            if (!result.containsKey(serviceCode)) {
                result.put(serviceCode, new ArrayList<String>());
            }

            result.get(serviceCode).add(subjectShortName);
        }

        return result;
    }

    public String getAdapterURL(Producer producer) throws IOException {
        String adapterBaseURL = getAdapterBaseURL(producer);
        String adapterURI = readString(producer.getPath("/adapter_uri"));

        if (adapterBaseURL == null || adapterURI == null) {
            return null;
        }

        return adapterBaseURL + adapterURI;
    }

    public String getWsdlURL(Producer producer) throws IOException {
        String adapterBaseURL = getAdapterBaseURL(producer);
        String schemaURI = readString(producer.getPath("/schema_uri"));

        if (adapterBaseURL == null || schemaURI == null) {
            return null;
        }

        return adapterBaseURL + schemaURI;
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

    public int getServiceTimeout(Producer producer) throws IOException {
        String timeout = readString(producer.getPath("/consumer_timeout"));

        return timeout == null ? 0 : Integer.parseInt(timeout);
    }

    public void setServiceTimeout(Producer producer, int timeout)
            throws IOException {
        writeString(producer.getPath("/consumer_timeout"),
            Integer.toString(timeout));
    }

    public boolean createOrg(Org org, String fullName)
            throws IOException {

        if (orgExists(org)) {
            LOG.info("Organization '{}' already exists in X-Road",
                org.getShortName());

            return false;
        }

        if (fullName == null) {
            fullName = org.getShortName();
        }

        Files.createDirectories(toFullPath(org.getPath()));
        writeString(org.getPath("/name"), org.getShortName());
        writeString(org.getPath("/fullname"), fullName);

        return true;
    }

    public String getPeerType(Org org) throws IOException {
        return readString(org.getPath("/peertype"), "http");
    }

    public void setPeerType(Org org, String peerType)
            throws IOException {
        writeString(org.getPath("/peertype"), peerType);
    }

    public void setPeerIP(Producer producer, String peerIP) throws IOException {
        writeString(producer.getPath("/peerip"), peerIP);
    }

    public void setPeerPort(Producer producer, int peerPort) throws IOException {
        writeString(producer.getPath("/peerport"), String.valueOf(peerPort));
    }

    public void setAdapterURI(Producer producer, String adapterURI)
            throws IOException {
        writeString(producer.getPath("/adapter_uri"), adapterURI);
    }

    public void setSchemaURI(Producer producer, String schemaURI)
            throws IOException {
        writeString(producer.getPath("/schema_uri"), schemaURI);
    }

    public void deleteAdapterConf(Producer producer) throws IOException {
        deleteField(producer.getPath("/peerip"));
        deleteField(producer.getPath("/peerport"));
        deleteField(producer.getPath("/adapter_uri"));
        deleteField(producer.getPath("/schema_uri"));
        deleteField(producer.getPath("/consumer_timeout"));
    }

    public void deleteAcl(Producer producer) throws IOException {
        writeStringList(producer.getPath("/acl"), Collections.EMPTY_SET);
        writeStringList(producer.getPath("/gacl"), Collections.EMPTY_SET);
        writeStringList(producer.getPath("/sacl"), Collections.EMPTY_SET);
        writeStringList(producer.getPath("/query_list"), Collections.EMPTY_SET);
        deleteField(producer.getPath("/consumer_list"));
        deleteField(producer.getPath("/group_list"));
    }

    public void saveQueries(Producer producer, Set<String> queries)
            throws IOException {
        writeStringList(producer.getPath("/query_list"), queries);
    }

    public void saveConsumers(Producer producer, Set<String> consumers)
            throws IOException {
        writeStringList(producer.getPath("/consumer_list"), consumers);
    }

    public void saveGroups(Producer producer, Set<String> groups)
            throws IOException {
        writeStringList(producer.getPath("/group_list"), groups);
    }

    public void saveACL(Producer producer, Map<String, Set<String>> acl)
            throws IOException {

        List<String> entries = new ArrayList<>();

        for (String query : acl.keySet()) {
            for (String consumer : acl.get(query)) {
                entries.add(consumer + " " + query);
            }
        }

        writeStringList(producer.getPath("/acl"), entries);
    }

    public void saveGACL(Producer producer, Map<String, Set<String>> gacl)
            throws IOException {

        List<String> entries = new ArrayList<>();

        for (String query : gacl.keySet()) {
            for (String group : gacl.get(query)) {
                entries.add(group + " " + query);
            }
        }

        writeStringList(producer.getPath("/gacl"), entries);
    }

    public List<byte[]> getInternalSSLCerts(Org org) throws IOException {
        List<byte[]> result = new ArrayList<>();

        for (String certFile : listFiles(org.getPath("/https_certs"))) {
            result.add(readBytes(org.getPath("/https_certs/" + certFile)));
        }

        return result;
    }

    public void setInternalSSLCerts(Org org, List<byte[]> certs)
            throws Exception {
        Path path = toFullPath(org.getPath("/https_certs"));

        FileUtils.deleteDirectory(path.toFile());
        Files.createDirectories(path);

        MessageDigest md = MessageDigest.getInstance("SHA-1");

        for (byte[] cert : certs) {
            String hexDigest = encodeHexString(md.digest(cert));
            writeBytes(org.getPath("/https_certs/" + hexDigest), cert);
        }
    }

    public boolean orgExists(Org org) {
        return Files.exists(toFullPath(org.getPath()));
    }

    public void deleteOrg(Org org) throws IOException {
        FileUtils.deleteDirectory(toFullPath(org.getPath()).toFile());
    }

    public boolean adapterExists(Producer producer) throws IOException {
        return !"0.0.0.0".equals(
            readString(producer.getPath("/peerip"), "0.0.0.0"));
    }

    private String encodeHexString(byte[] bytes) {
        Formatter formatter = new Formatter();

        int index = 0;

        for (byte b : bytes) {
            formatter.format("%02X", b);
            if (++index < bytes.length) {
                formatter.format(":");
            }
        }

        return formatter.toString();
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
            return new ArrayList<String>();
        }

        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    private void writeBytes(String confPath, byte[] bytes) throws IOException {
        if (bytes == null) {
            return;
        }

        Files.write(toFullPath(confPath), bytes);
    }

    private void writeString(String confPath, String string)
            throws IOException {
        if (string == null) {
            return;
        }

        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        Files.write(toFullPath(confPath), bytes);
    }

    private void writeStringList(String confPath, Collection<String> strings)
            throws IOException {
        Files.write(toFullPath(confPath), strings, StandardCharsets.UTF_8);
    }

    private void deleteField(String confPath) throws IOException {
        Files.deleteIfExists(toFullPath(confPath));
    }

    abstract static class Org {
        private String shortName;

        Org(String shortName) {
            this.shortName = shortName;
        }

        String getShortName() {
            return shortName;
        }

        String getPath(String field) {
            return getPath() + field;
        }

        abstract String getPath();
    }

    static class Consumer extends Org {
        Consumer(String shortName) {
            super(shortName);
        }

        @Override
        String getPath() {
            return CONSUMERS_PATH + "/" + getShortName();
        }
    }

    static class Producer extends Org {
        Producer(String shortName) {
            super(shortName);
        }

        @Override
        String getPath() {
            return PRODUCERS_PATH + "/" + getShortName();
        }
    }
}
