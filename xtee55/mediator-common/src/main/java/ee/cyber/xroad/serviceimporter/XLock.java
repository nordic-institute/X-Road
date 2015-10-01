package ee.cyber.xroad.serviceimporter;

class XLock {

    static {
        System.loadLibrary("xlock");
    }

    private int semId;
    private int rlCount;
    private int wlCount;

    XLock(String xConfPath) {
        init(xConfPath);
    }

    native void init(String xConfPath);
    native void readLock();
    native void writeLock();
    native void unlock();
}
