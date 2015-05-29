package ee.ria.xroad.proxy.serverproxy;

final class ServiceHandlerLoader {

    private ServiceHandlerLoader() {
    }

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
