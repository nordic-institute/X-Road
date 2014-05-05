package ee.cyber.sdsb.logreader;

abstract  class SearchPredicate {
    abstract boolean matches(LogFile file, int recordStart);
}
