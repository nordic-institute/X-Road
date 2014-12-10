# This table will hold files to be distributed by the Central. It contains
# file name and file data (as blob) pairs.

java_import Java::ee.cyber.sdsb.common.conf.globalconf.SharedParameters
java_import Java::ee.cyber.sdsb.common.conf.globalconf.PrivateParameters

class DistributedFiles < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :file_name, :uniqueness => true

  EXTERNAL_SOURCE_CONTENT_IDENTIFIERS = [
    SharedParameters::CONTENT_ID_SHARED_PARAMETERS
  ]

  INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS = [
    PrivateParameters::CONTENT_ID_PRIVATE_PARAMETERS,
    SharedParameters::CONTENT_ID_SHARED_PARAMETERS
  ]

  def self.save_configuration_part(file_name, file_data)
    content_identifier = get_content_identifier(file_name)

    DistributedFiles.where(:file_name => file_name).destroy_all
    DistributedFiles.create!(
        :file_name => file_name,
        :content_identifier => content_identifier,
        :file_data => file_data,
        :file_updated_at => Time.now())
  end

  # Gets configuration parts as hash including:
  # :content_identifier, :file_name, :updated_at and :optional.
  def self.get_configuration_parts_as_json(source_type)
    source_external =
        ConfigurationSource::SOURCE_TYPE_EXTERNAL.eql?(source_type)

    if source_external
      return get_required_configuration_parts_as_json(
          EXTERNAL_SOURCE_CONTENT_IDENTIFIERS)
    end

    result = get_required_configuration_parts_as_json(
        INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS)

    result.push(*get_optional_configuration_parts_as_json())

    return result
  end

  def self.get_internal_source_content_identifiers
    result = INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS

    get_optional_parts_conf().getAllParts().each do |each|
      result << each.contentIdentifier
    end

    return result
  end

  def self.get_optional_parts_conf
    return Java::ee.cyber.sdsb.commonui.OptionalPartsConf.new(
        Java::ee.cyber.sdsb.common.SystemProperties::CONF_FILE_OPTIONAL_PARTS)
  end

  private

  def self.get_content_identifier(file_name)
    raise "File name MUST be provided" if file_name.blank?()

    case file_name
    when PrivateParameters::FILE_NAME_PRIVATE_PARAMETERS
      return PrivateParameters::CONTENT_ID_PRIVATE_PARAMETERS
    when SharedParameters::FILE_NAME_SHARED_PARAMETERS
      return SharedParameters::CONTENT_ID_SHARED_PARAMETERS
    else
      return get_optional_parts_conf().getContentIdentifier(file_name)
    end
  end

  def self.get_required_configuration_parts_as_json(content_identifiers)
    result = []

    content_identifiers.each do |each_identifier|
      DistributedFiles.where(
          :content_identifier => each_identifier).each do |each_file|
        result << {
          :content_identifier => each_file.content_identifier,
          :file_name => each_file.file_name,
          :updated_at => CenterUtils::format_time(
              each_file.file_updated_at.localtime),
          :optional => false
        }
      end
    end

    return result
  end

  def self.get_optional_configuration_parts_as_json()
    result = []

    get_optional_parts_conf().getAllParts().each do |each|
      file_name = each.fileName
      file_in_db = DistributedFiles.where(:file_name => file_name).first()
      update_time = file_in_db ?
          CenterUtils::format_time(file_in_db.file_updated_at.localtime) : nil

      result << {
        :content_identifier => each.contentIdentifier,
        :file_name => file_name,
        :updated_at => update_time ,
        :optional => true
      }
    end

    return result
  end
end
