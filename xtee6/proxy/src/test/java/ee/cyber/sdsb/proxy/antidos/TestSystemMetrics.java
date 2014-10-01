package ee.cyber.sdsb.proxy.antidos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class TestSystemMetrics {

    private final List<TestConfiguration> load = new ArrayList<>();
    private Iterator<TestConfiguration> it;
    private TestConfiguration current;

    void addLoad(int freeDescriptors, double cpuLoad) {
        load.add(new TestConfiguration(freeDescriptors, cpuLoad));
        it = load.iterator();
    }

    void next() {
        if (it.hasNext()) {
            current = it.next();
        }
    }

    TestConfiguration get() {
        return current;
    }
}
