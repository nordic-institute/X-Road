java_import Java::ee.cyber.sdsb.centerui.IdentifierMappingSchemaValidator
java_import Java::ee.cyber.sdsb.centerui.IdentifierMappingUnmarshaller
java_import Java::ee.cyber.sdsb.common.CodedException
java_import Java::org.xml.sax.SAXException

# TODO (RM #3888) - Make it extend UploadedFileValidator.
class IdentifierMappingValidator < UploadedFileValidator
  def initialize(file, current_sdsb_instance, allowed_member_classes)
    super(file)
    @current_sdsb_instance = current_sdsb_instance
    @allowed_member_classes = allowed_member_classes
    @allowed_classes_as_string = allowed_member_classes.join(", ")
  end

  def validate()
    xml = read_file()

    IdentifierMappingSchemaValidator.validate(xml);
    validate_mappings_content(IdentifierMappingUnmarshaller.unmarshal(xml))
  rescue CodedException => e
    raise I18n.t("errors.identifier_mapping.malformed",
        {:fault_msg => e.getFaultString()})
  end

  private

  def validate_mappings_content(mappings)
    mappings.getMapping().each do |each|
      new_id = each.getNewId()
      member_class = new_id.getMemberClass()
      sdsb_instance = new_id.getSdsbInstance()

      if !@allowed_member_classes.include?(member_class)
        raise I18n.t("errors.identifier_mapping.member_class_not_allowed", {
            :class => member_class,
            :allowed_classes => @allowed_classes_as_string})
      end

      if !@current_sdsb_instance.eql?(sdsb_instance)
        raise I18n.t("errors.identifier_mapping.wrong_sdsb_instance", {
            :current => @current_sdsb_instance, :found => sdsb_instance})
      end
    end
  end
end
