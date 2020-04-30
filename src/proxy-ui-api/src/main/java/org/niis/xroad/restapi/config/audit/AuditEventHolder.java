package org.niis.xroad.restapi.config.audit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Getter
@Setter
@Component
@Scope(SCOPE_REQUEST)
public class AuditEventHolder {

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    private String eventName;
    private Map<String, Object> eventData = new HashMap<>();

    private int instanceNumber;

    public AuditEventHolder() {
        instanceNumber = INSTANCE_COUNTER.incrementAndGet();
    }

    public void addData(String propertyName, Object value) {
        eventData.put(propertyName, value);
    }
}
