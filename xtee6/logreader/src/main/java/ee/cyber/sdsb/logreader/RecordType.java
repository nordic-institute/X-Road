package ee.cyber.sdsb.logreader;

import java.util.HashMap;
import java.util.Map;

enum RecordType {
        SOAP('M'), ENC_SOAP('E'), SIGNATURE('S'), TIMESTAMP('T'),
        FIRST_ROW('#'), TODO('?');
    char value;
    static final Map<Character, RecordType> values;

    private RecordType(char value) {
        this.value = value;
    }

    static RecordType fromChar(char c) {
        return values.get(c);
    }

    static {
        values = new HashMap<>();
        for (RecordType t: values()) {
            values.put(t.value, t);
        }
    }
}
