require 'nokogiri'

class XmlValidator
  def validate(file_content)
    if !Nokogiri::XML(file_content).errors.empty?
      raise I18n.t("distributed_files.invalid_xml")
    end
  end
end