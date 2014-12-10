package ee.cyber.xroad.common.message;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import ee.cyber.xroad.common.ErrorCodes;

public class JaxbUtils {

    private static final Map<Class<?>, JAXBContext> CTX_CACHE = new HashMap<>();

    public static Marshaller createMarshaller(Class<?> clazz,
            NamespacePrefixMapper mpr) throws Exception {
        Marshaller marshaller = createMarshaller(clazz);
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mpr);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        return marshaller;
    }

    public static Marshaller createMarshaller(Class<?> clazz)
            throws Exception {
        return getJAXBContext(clazz).createMarshaller();
    }

    public static Unmarshaller createUnmarshaller(Class<?> clazz)
            throws Exception {
        return getJAXBContext(clazz).createUnmarshaller();
    }

    public static JAXBContext initJAXBContext(Class<?>... classesToBeBound) {
        try {
            return JAXBContext.newInstance(classesToBeBound);
        } catch (JAXBException e) {
            throw ErrorCodes.translateException(e);
        }
    }

    private static JAXBContext getJAXBContext(Class<?> clazz) throws Exception {
        if (CTX_CACHE.containsKey(clazz)) {
            return CTX_CACHE.get(clazz);
        } else {
            JAXBContext ctx = JAXBContext.newInstance(clazz);
            CTX_CACHE.put(clazz, ctx);
            return ctx;
        }
    }
}
