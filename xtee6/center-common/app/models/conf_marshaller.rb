java_import Java::java.io.StringWriter
java_import Java::java.util.GregorianCalendar
java_import Java::javax.xml.bind.JAXBContext
java_import Java::javax.xml.bind.Marshaller
java_import Java::javax.xml.datatype.DatatypeFactory

class ConfMarshaller

  attr_reader :factory, :root

  def initialize(factory_class, root_type_creator)
    @factory = factory_class.new
    @jaxb_context = JAXBContext.newInstance(factory_class.java_class)
    @jaxb_element = root_type_creator.call(factory)
    @root = @jaxb_element.getValue
  end

  # Writes the conf to string
  def write_to_string
    # javax.xml.bind.Marshaller
    marshaller = @jaxb_context.createMarshaller
    marshaller.setProperty(Marshaller::JAXB_FORMATTED_OUTPUT, true)

    writer = StringWriter.new()
    marshaller.marshal(@jaxb_element, writer)
    writer.toString()
  end

  def self.xml_time(time)
    calendar = GregorianCalendar.new
    calendar.setTime(time)
    DatatypeFactory::newInstance.newXMLGregorianCalendar(calendar).normalize
  end
end
