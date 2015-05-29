package ee.ria.xroad_legacy.logreader;

class LogPosition {
    static final LogPosition BEGINNING = new LogPosition(null, 0);

    int pos;
    LogFile file;

    LogPosition(LogFile file, int pos) {
        this.file = file;
        this.pos = pos;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LogPosition
                && pos == ((LogPosition) obj).pos;
    }

    @Override
    public String toString() {
        return String.valueOf(pos);
    }
}
