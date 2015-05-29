package ee.ria.xroad_legacy.logreader;

import java.util.HashMap;
import java.util.Map;

enum RecordType {
        SOAP('M'), ENC_SOAP('E'), SIGNATURE('S'), TIMESTAMP('T'),
        FIRST_ROW('#'), TODO('?');
    char value;
    static final Map<Character, RecordType> VALUES;

    private RecordType(char value) {
        this.value = value;
    }

    static RecordType fromChar(char c) {
        return VALUES.get(c);
    }

    static {
        VALUES = new HashMap<>();
        for (RecordType t: values()) {
            VALUES.put(t.value, t);
        }
    }
}
