#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

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
