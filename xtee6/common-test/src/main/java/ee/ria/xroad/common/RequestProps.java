package ee.ria.xroad.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ee.ria.xroad.common.Request.RequestTag;

/**
 * Encapsulates everything that is specific to one request: name and content.
 *
 * FUTURE Only 'extra' project uses it (subproject 'testclient')
 */
public abstract class RequestProps {
    protected List<RequestTag> content;

    /**
     * Creates a request properties object with no content.
     */
    public RequestProps() {
        this.content = new ArrayList<>();
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
