package ee.ria.xroad.common.signature;

import lombok.RequiredArgsConstructor;

import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static ee.ria.xroad.common.util.XmlUtils.getElementById;

/**
 * DOM element resolver that resolves by the element id.
 */
@RequiredArgsConstructor
public class IdResolver extends ResourceResolverSpi {

    private final Document document;

    @Override
    public boolean engineCanResolve(Attr uri, String baseUri) {
        return uri.getValue().startsWith("#");
    }

    @Override
    public XMLSignatureInput engineResolve(Attr uri, String baseUri)
            throws ResourceResolverException {
        Element elem = getElementById(document, uri.getValue().substring(1));
        if (elem != null) {
            return new XMLSignatureInput(elem);
        }

        return null;
    }
}
