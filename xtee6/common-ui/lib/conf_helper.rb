require 'fileutils'

java_import Java::java.io.FileInputStream
java_import Java::java.io.FileOutputStream
java_import Java::java.io.StringWriter
java_import Java::javax.xml.bind.JAXBContext
java_import Java::javax.xml.bind.Marshaller
java_import Java::javax.xml.bind.util.ValidationEventCollector

java_import Java::ee.cyber.sdsb.common.SystemProperties

module ConfHelper

  class Conf

    def initialize(xml, factory_class)
      @xml = xml
      @factory_class = factory_class

      if @xml && File.exists?(@xml)
        read
      else
        @jaxb_context = JAXBContext.newInstance(@factory_class.java_class)
      end
    end

    def exists?
      File.exists?(@xml)
    end

    def factory
      @factory || @factory = @factory_class.new
    end

    # Initialize conf with new (empty) root element
    def init(jaxb_element)
      @jaxb_element = jaxb_element
    end

    # Read the XML and store the result in @jaxb_element.
    def read
      @jaxb_context = JAXBContext.newInstance(@factory_class.java_class)

      # javax.xml.bind.Unmarshaller
      unmarshaller = @jaxb_context.createUnmarshaller

      stream = FileInputStream.new(@xml)

      vec = ValidationEventCollector.new
      unmarshaller.setEventHandler(vec)

      begin
        # javax.xml.bind.JAXBElement
        @jaxb_element = unmarshaller.unmarshal(stream)
      rescue Java::javax.xml.bind.UnmarshalException => e
        # messages are collected in vec
      end

      if vec.hasEvents
        messages = []
        vec.events.each do |event|
          messages << event.message
        end

        raise "Error reading #{@xml}: #{messages.join(', ')}"
      end
    end

    # Write the content of @jaxb_element back to the XML.
    def write
      # javax.xml.bind.Marshaller
      marshaller = @jaxb_context.createMarshaller
      marshaller.setProperty(Marshaller::JAXB_FORMATTED_OUTPUT, true)

      stream = FileOutputStream.new(@xml)
      marshaller.marshal(@jaxb_element, stream)

      conf_changed
    end

    def conf_changed
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

    # Returns the root element of read XML file.
    def root
      @jaxb_element.getValue if @jaxb_element
    end
  end
end
