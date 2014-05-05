package ee.cyber.sdsb.proxy.testsuite;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestcaseLoader {
    private static final Logger LOG = LoggerFactory.getLogger(
            TestcaseLoader.class);

    private static final String TESTCASES_PACKAGE_NAME =
            "ee.cyber.sdsb.proxy.testsuite.testcases.";

    // currently hard coded flag for sorting the all testcases
    private static final boolean SORT_ALPHABETICALLY = false;

    static List<MessageTestCase> getTestCasesToRun(String[] ids) {
        List<MessageTestCase> testsToRun = new ArrayList<>();
        if (ids.length > 0) {
            for (String id : ids) {
                try {
                    final String name = TESTCASES_PACKAGE_NAME + id;
                    MessageTestCase tc =
                            (MessageTestCase) Class.forName(name).newInstance();
                    tc.setId(id);
                    testsToRun.add(tc);
                } catch (ClassNotFoundException e) {
                    LOG.error("Could not find testcase with id '{}'", id);
                } catch (InstantiationException | IllegalAccessException e) {
                    LOG.error(e.toString());
                }
            }
        } else {
            try {
                testsToRun.addAll(getAllTestCases(TESTCASES_PACKAGE_NAME));
            } catch (ClassNotFoundException | IOException e) {
                LOG.error(e.toString());
            }
        }
        return testsToRun;
    }

    private static List<MessageTestCase> getAllTestCases(String packageName)
            throws ClassNotFoundException, IOException {
        List<MessageTestCase> classes = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File dir = new File(resource.getFile());
            File[] files = dir.listFiles();
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".class") && !name.contains("$")) {
                    final String[] clazz = name.split("\\.");
                    try {
                        MessageTestCase tc = (MessageTestCase) Class.forName(
                                packageName + clazz[0]).newInstance();
                        tc.setId(clazz[0]);
                        classes.add(tc);
                    } catch (InstantiationException | IllegalAccessException e) {
                        LOG.error(e.toString());
                    }
                }
            }
        }

        if (SORT_ALPHABETICALLY) {
            // Ensure the testcases are run in the same order each time.
            Collections.sort(classes, new SortTestCasesAlphabetically());
        }

        return classes;
    }

    private static class SortTestCasesAlphabetically
            implements Comparator<MessageTestCase> {
        @Override
        public int compare(MessageTestCase a, MessageTestCase b) {
            return a.getClass().getName().compareTo(b.getClass().getName());
        }
    }
}
