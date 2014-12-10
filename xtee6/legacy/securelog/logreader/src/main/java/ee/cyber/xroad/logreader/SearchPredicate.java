package ee.cyber.xroad.logreader;

abstract class SearchPredicate {
    abstract boolean matches(LogFile file, int recordStart);
}
