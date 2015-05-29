package ee.ria.xroad.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ee.ria.xroad.common.Request.RequestTag;

/**
 * Encapsulates everything that is specific to one request: name, content and
 * whether it is asynchronous or not.
 */
public abstract class RequestProps {
    protected List<RequestTag> content;
    protected boolean async;

    /**
     * Creates a request properties object with no content.
     * @param async whether the request is asynchronous
     */
    public RequestProps(boolean async) {
        this.content = new ArrayList<>();
        this.async = async;
    }

    /**
     * @return name of the request
     */
    public abstract String getName();

    /**
     * @return request content as a read-only list
     */
    public List<RequestTag> getContent() {
        return Collections.unmodifiableList(content);
    }

    /**
     * @return true if this request is asynchronous
     */
    public boolean isAsync() {
        return async;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
