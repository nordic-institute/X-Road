package ee.ria.xroad.common.message;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import ee.ria.xroad.common.ErrorCodes;

/**
 * Contains utility functions for working with JAXB.
 */
public final class JaxbUtils {

    private static final Map<Class<?>, JAXBContext> CTX_CACHE = new HashMap<>();

    private JaxbUtils() {
    }

    /**
     * Creates a marshaller for the given class and namespace prefix mapper.
     * @param clazz class for which the marshaller is to be created
     * @param mpr namespace prefix mapper to use with the marshaller
     * @return Marshaller
     * @throws Exception if an error was encountered while creating the Marshaller object
     */
    public static Marshaller createMarshaller(Class<?> clazz,
            NamespacePrefixMapper mpr) throws Exception {
        Marshaller marshaller = createMarshaller(clazz);
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mpr);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        return marshaller;
    }

    /**
     * Creates a marshaller for the given class.
     * @param clazz class for which the marshaller is to be created
     * @return Marshaller
     * @throws Exception if an error was encountered while creating the Marshaller object
     */
    public static Marshaller createMarshaller(Class<?> clazz)
            throws Exception {
        return getJAXBContext(clazz).createMarshaller();
    }

    /**
     * Creates a unmarshaller for the given class.
     * @param clazz class for which the unmarshaller is to be created
     * @return Marshaller
     * @throws Exception if an error was encountered while creating the Unmarshaller object
     */
    public static Unmarshaller createUnmarshaller(Class<?> clazz)
            throws Exception {
        return getJAXBContext(clazz).createUnmarshaller();
    }

    /**
     * Obtains a new instance of a JAXBContext that recognizes the provided classes.
     * @param classesToBeBound list of classes that the new context should recognize
     * @return JAXBContext
     */
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
