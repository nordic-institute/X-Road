package ee.cyber.xroad.mediator.message;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;

import lombok.Getter;
import lombok.ToString;

import org.w3c.dom.Element;

@Getter
@ToString
class XRoadHeaderFields {

    @XmlAnyElement
    private final List<Element> fields = new ArrayList<>();

    void add(Element field) {
        fields.add(field);
    }
}
