package ee.cyber.sdsb.common.conf;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import ee.cyber.sdsb.common.util.AtomicSave;
import ee.cyber.sdsb.common.util.FileContentChangeChecker;
import ee.cyber.sdsb.common.util.ResourceUtils;
import ee.cyber.sdsb.common.util.SchemaValidator;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;

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
public abstract class AbstractXmlConf<T> implements ConfProvider {

    private final JAXBContext jaxbCtx;

    protected final Class<? extends SchemaValidator> schemaValidator;

    protected String confFileName;

    protected JAXBElement<T> root;

    protected T confType;

    private FileContentChangeChecker confFileChecker;

    // For subclasses to use only default parameters if no valid serverconf
    // present.
    protected AbstractXmlConf() {
        this.schemaValidator = null;
        jaxbCtx = null;
    }

    protected AbstractXmlConf(Class<?> objectFactory, String fileName) {
        this(objectFactory, fileName, null);
    }

    protected AbstractXmlConf(Class<?> objectFactory,
            Class<? extends SchemaValidator> schemaValidator) {
        this(objectFactory, (String) null, schemaValidator);
    }

    protected AbstractXmlConf(Class<?> objectFactory, String fileName,
            Class<? extends SchemaValidator> schemaValidator) {
        try {
            this.jaxbCtx = JAXBContext.newInstance(objectFactory);
            this.schemaValidator = schemaValidator;

            load(fileName);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    protected AbstractXmlConf(Class<?> objectFactory, JAXBElement<T> root,
            Class<? extends SchemaValidator> schemaValidator) {
        try {
            this.jaxbCtx = JAXBContext.newInstance(objectFactory);
            this.schemaValidator = schemaValidator;

            this.root = root;
            this.confType = root.getValue();
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
    @SuppressWarnings("unchecked")
    public void load(String fileName) throws Exception {
        if (fileName == null) {
            return;
        }

        confFileName = fileName;
        confFileChecker = new FileContentChangeChecker(confFileName);

        if (schemaValidator != null) {
            try (InputStream in = new FileInputStream(confFileName)) {
                validateSchemaWithValidator(in);
            }
        }

        try (InputStream in = new FileInputStream(confFileName)) {
            Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
            root = (JAXBElement<T>) unmarshaller.unmarshal(in);
            confType = root.getValue();
        }
    }

    @Override
    public void save() throws Exception {
        AtomicSave.execute(confFileName, "tmpconf",
                out -> AbstractXmlConf.this.save(out));
    }

    @Override
    public void save(OutputStream out) throws Exception {
        Marshaller marshaller = jaxbCtx.createMarshaller();
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
            Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
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

    private void validateSchemaWithValidator(InputStream in) throws Exception {
        try {
            Method validateMethod =
                    schemaValidator.getMethod("validate", Source.class);
            try {
                validateMethod.invoke(null, new StreamSource(in));
            } catch (InvocationTargetException e) {
                throw translateException(e.getCause());
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("SchemaValidator '"
                    + schemaValidator.getName() + "' must implement static "
                        + "method 'void validate(Source)'");
        }
    }
}
