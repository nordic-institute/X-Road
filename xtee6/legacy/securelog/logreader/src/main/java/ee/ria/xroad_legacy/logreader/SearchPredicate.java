package ee.ria.xroad_legacy.logreader;

abstract class SearchPredicate {
    abstract boolean matches(LogFile file, int recordStart);
}
