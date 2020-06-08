/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.ErrorCodes;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import java.util.HashMap;
import java.util.Map;

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
