package ee.cyber.sdsb.proxy.securelog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class LogState {
    private static final int SIGNATURE_CACHE_MAX_SIZE = 100;

    private PrevRecord prevRecord;
    private List<TodoRecord> todoList;
    /** The number of todo list entries that are not taken into process. */
    private int activeTodoCount;
    private Map<String, Long> signatures = new LinkedHashMap<String, Long>() {
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(Entry<String, Long> eldest) {
            return size() >= SIGNATURE_CACHE_MAX_SIZE;
        }
    };

    static LogState getFirstState() throws Exception {
        return new LogState(LogRecord.getFirstRecord(LogManager.getHashAlg()),
                new ArrayList<TodoRecord>());
    }

    LogState(PrevRecord prevRecord, List<TodoRecord> todoList) {
        super();
        this.prevRecord = prevRecord;
        this.todoList = todoList;
        activeTodoCount = todoList.size();
    }

    PrevRecord getPrevRecord() {
        return prevRecord;
    }

    int getActiveTodoCount() {
        return activeTodoCount;
    }

    /**
     * Returns up to "max" todo-list records that are not "in process" and marks
     * the returned records as "in process".
     */
    List<TodoRecord> takeTodoIntoProcess(int max) {
        List<TodoRecord> subList = new ArrayList<>(max);

        for (int i = 0; i < todoList.size() && subList.size() < max; i++) {
            if (!todoList.get(i).isInProcess()) {
                subList.add(todoList.get(i));
                todoList.get(i).setInProcess(true);
                activeTodoCount--;
            }
        }

        return subList;
    }

    void timestampFailed(List<TodoRecord> failed) {
        for (TodoRecord todo : failed) {
            todo.setInProcess(false);
            activeTodoCount++;
        }
    }

    Long getSignatureRecordNr(SignatureRecord signatureRecord) {
        return signatures.get(signatureRecord.getTsManifestId());
    }

    private List<Long> getTodoRecordNumbers() {
        List<Long> nrs = new ArrayList<>(todoList.size());
        for (TodoRecord todo : todoList) {
            nrs.add(todo.getNr());
        }
        return nrs;
    }

    void update(LogRecord logRecord) {
        prevRecord = logRecord;
        if (logRecord instanceof TodoRecord) {
            todoList.add((TodoRecord) logRecord);
            activeTodoCount++;
        }
        if (logRecord instanceof TimestampRecord) {
            List<Long> tsNumbers = ((TimestampRecord) logRecord).getNumbers();
            Iterator<TodoRecord> i = todoList.iterator();
            while (i.hasNext() && !tsNumbers.isEmpty()) {
                if (tsNumbers.remove(i.next().getNr())) {
                    i.remove();
                }
            }
        }
        if (logRecord instanceof SignatureRecord) {
            signatures.put(((SignatureRecord) logRecord).getTsManifestId(),
                    logRecord.getNr());
        }
    }

    String[] toLogStr() {
        List<String> result = new ArrayList<>(todoList.size() + 1);
        result.add(prevRecord.toFirstRowStr());
        for (TodoRecord todo : todoList) {
            result.add(todo.toTodoStr());
        }
        return result.toArray(new String[result.size()]);
    }

    public String toString() {
        return String.format("prevRecord %1$s, todoList %2$s",
                prevRecord.getNr(), getTodoRecordNumbers());
    }
}
