package org.niis.xroad.ss.test.ui.container;

import java.util.List;

public final class Port {
    public static final int JMX = 9999, UI = 4000, SERVICE = 8080;

    public static List<Integer> allSsPorts() {
        return List.of(UI, SERVICE, JMX);
    }

    private Port() {
    }
}
