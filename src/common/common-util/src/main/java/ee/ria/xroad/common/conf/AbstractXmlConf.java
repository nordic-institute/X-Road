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
package ee.ria.xroad.common.conf;

import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.common.util.ResourceUtils;
import ee.ria.xroad.common.util.SchemaValidator;

import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.StandardCopyOption;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static java.util.Objects.requireNonNull;

/**
 * Base class for XML-based configurations, where underlying classes are
 * generated from the XSD that describes the XML.
 *
 * This class also contains a file content change checker that check if a
 * file contents has been changed since the last time it was accessed. The
 * check is based on the checksum of the file's contents.
 *
 * @param <T> the generated configuration type
 */
@Slf4j
public abstract class AbstractXmlConf<T> implements ConfProvider {
    protected final Class<? extends SchemaValidator> schemaValidator;

    protected String confFileName;

    protected JAXBElement<T> root;

    protected T confType;

    private FileContentChangeChecker confFileChecker;

    // For subclasses to use only default parameters if no valid serverconf present.
    protected AbstractXmlConf() {
        schemaValidator = null;
    }

    protected AbstractXmlConf(Class<? extends SchemaValidator> schemaValidator) {
        this((String) null, schemaValidator);
    }

    protected AbstractXmlConf(String fileName, Class<? extends SchemaValidator> schemaValidator) {
        try {
            this.schemaValidator = schemaValidator;
            load(fileName);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    protected AbstractXmlConf(AbstractXmlConf<T> original) {
        schemaValidator  = original.schemaValidator;
        confFileName = original.confFileName;
        root = original.root;
        confType = original.confType;
        confFileChecker = original.confFileChecker;
    }

    /**
     * A method for subclasses to return (preferably static) JAXBContext
     * @return class specific JAXBContext
     */
    protected abstract JAXBContext getJAXBContext();

    /**
     * A special constructor for creating an AbstractXmlConf from bytes instead of a file on the filesystem.
     * <b>Does not set <code>confFileChecker</code>.</b>
     * @param fileBytes
     * @param schemaValidator
     */
    protected AbstractXmlConf(byte[] fileBytes, Class<? extends SchemaValidator> schemaValidator) {
        try {
            this.schemaValidator = schemaValidator;

            load(fileBytes);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    protected AbstractXmlConf(JAXBElement<T> root, Class<? extends SchemaValidator> schemaValidator) {
        try {
            this.schemaValidator = schemaValidator;

            this.root = root;
            confType = root.getValue();
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public boolean hasChanged() {
        try {
            return confFileChecker == null || confFileChecker.hasChanged();
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public void load(String fileName) throws Exception {
        if (fileName == null) {
            return;
        }

        confFileName = fileName;
        confFileChecker = new FileContentChangeChecker(confFileName);

        doValidateConfFile();

        LoadResult<T> result = doLoadConfFile();
        root = result.getRoot();
        confType = result.getConfType();
    }

    /**
     * Load the xml configuration to a {@link LoadResult} that can be manipulated further.
     * @return
     * @throws IOException if opening {@link #confFileName} fails.
     * @throws JAXBException if an unmarshalling error occurs
     * @throws NullPointerException if {@link #confFileName} or {@link #getJAXBContext()} is null
     */
    @SuppressWarnings("unchecked")
    // the unmarshalling causes an unchecked cast, it is existing functionality.
    // Is there an elegant way to handle the type checking of T at compile time?
    protected LoadResult<T> doLoadConfFile() throws IOException, JAXBException {
        requireNonNull(confFileName, "confFileName not set");
        requireNonNull(getJAXBContext(), "jaxbCtx not set");

        try (InputStream in = new FileInputStream(confFileName)) {
            Unmarshaller unmarshaller = getJAXBContext().createUnmarshaller();

            return new LoadResult<>((JAXBElement<T>) unmarshaller.unmarshal(in));
        }
    }

    protected void doValidateConfFile() throws IOException, IllegalAccessException {
        requireNonNull(confFileName, "confFileName not set");

        if (schemaValidator != null) {
            try (InputStream in = new FileInputStream(confFileName)) {
                validateSchemaWithValidator(in);
            }
        }

    }

    protected static class LoadResult<T> {
        private JAXBElement<T> root;

        private T confType;

        LoadResult(JAXBElement<T> root) {
            this.root = root;
            confType = root.getValue();
        }

        public JAXBElement<T> getRoot() {
            return root;
        }

        public T getConfType() {
            return confType;
        }
    }

    @Override
    public void save() throws Exception {
        AtomicSave.execute(confFileName, "tmpconf", out -> AbstractXmlConf.this.save(out),
                StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void save(OutputStream out) throws Exception {
        Marshaller marshaller = getJAXBContext().createMarshaller();

        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(root, out);
    }

    /**
     * Loads the configuration from a byte array.
     * @param data the data
     * @throws Exception if an error occurs
     */
    @SuppressWarnings("unchecked")
    public void load(byte[] data) throws Exception {
        if (data == null) {
            return;
        }

        if (schemaValidator != null) {
            try (InputStream in = new ByteArrayInputStream(data)) {
                validateSchemaWithValidator(in);
            }
        }

        try (InputStream in = new ByteArrayInputStream(data)) {
            Unmarshaller unmarshaller = getJAXBContext().createUnmarshaller();
            root = (JAXBElement<T>) unmarshaller.unmarshal(in);
            confType = root.getValue();
        }
    }

    /**
     * Reloads the configuration from the file.
     * @throws Exception the file cannot be loaded
     */
    public void reload() throws Exception {
        load(confFileName);
    }

    protected String getConfFileDir() {
        return ResourceUtils.getFullPathFromFileName(confFileName);
    }

    private void validateSchemaWithValidator(InputStream in) throws IllegalAccessException {
        try {
            Method validateMethod = schemaValidator.getMethod("validate", Source.class);

            try {
                validateMethod.invoke(null, new StreamSource(in));
            } catch (InvocationTargetException e) {
                throw translateException(e.getCause());
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("SchemaValidator '" + schemaValidator.getName() + "' must implement static "
                    + "method 'void validate(Source)'");
        }
    }
}
