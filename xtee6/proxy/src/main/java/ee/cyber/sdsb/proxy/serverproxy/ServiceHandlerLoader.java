package ee.cyber.sdsb.proxy.serverproxy;


class ServiceHandlerLoader {

    static ServiceHandler load(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (ServiceHandler) clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load service handler: "
                    + className, e);
        }
    }

}
