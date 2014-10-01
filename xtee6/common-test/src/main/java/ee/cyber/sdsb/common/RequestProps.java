package ee.cyber.sdsb.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ee.cyber.sdsb.common.Request.RequestTag;

/**
 * Encapsulates everything that is specific to one request: name, content and
 * whether it is asynchronous or not.
 */
public abstract class RequestProps {
    protected List<RequestTag> content;
    protected boolean async;

    public RequestProps(boolean async) {
        this.content = new ArrayList<>();
        this.async = async;
    }

    public abstract String getName();

    public List<RequestTag> getContent() {
        return Collections.unmodifiableList(content);
    }

    public boolean isAsync() {
        return async;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
