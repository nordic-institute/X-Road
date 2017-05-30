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

# This table will hold files to be distributed by the Central Server. It contains
# file name and file data (as blob) pairs.

java_import Java::ee.ria.xroad.common.conf.globalconf.SharedParameters
java_import Java::ee.ria.xroad.common.conf.globalconf.PrivateParameters

class DistributedFiles < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator

  EXTERNAL_SOURCE_CONTENT_IDENTIFIERS = [
    SharedParameters::CONTENT_ID_SHARED_PARAMETERS
  ]

  INTERNAL_SOURCE_REQUIRED_CONTENT_IDENTIFIERS = [
    PrivateParameters::CONTENT_ID_PRIVATE_PARAMETERS,
    SharedParameters::CONTENT_ID_SHARED_PARAMETERS
  ]

  # These file types are handled independently by each instance of the central
  # server even though the records are replicated to each database node.
  NODE_LOCAL_CONTENT_IDS = [
    PrivateParameters::CONTENT_ID_PRIVATE_PARAMETERS,
    SharedParameters::CONTENT_ID_SHARED_PARAMETERS
  ]

  # Return true if the name of the local database node must be taken into
  # account when working with the file.
  def self.node_local_content_id?(content_id)
    return NODE_LOCAL_CONTENT_IDS.include?(content_id)
  end

  # Return all the records on non-HA systems;
  # on HA systems return all records except for the ones that must be treated
  # separately on each node and were created on other nodes.
  def self.get_all
    if !CommonSql.ha_configured?
      return DistributedFiles.all
    end
    return DistributedFiles.where(
      ["NOT (content_identifier IN (?) AND ha_node_name!=(?))",
       NODE_LOCAL_CONTENT_IDS, CommonSql.ha_node_name])
  end

  # Return the first record with the given content identifier on non-HA systems;
  # on HA systems return the first record with the given content identifier that
  # has been created on the current node.
  def self.get_by_content_id(content_identifier)
    if CommonSql.ha_configured? &&
        DistributedFiles.node_local_content_id?(content_identifier)
      ha_node_name = CommonSql.ha_node_name
      return DistributedFiles.where(
        :ha_node_name => ha_node_name,
        :content_identifier => content_identifier).first
    end
    return DistributedFiles.where(:content_identifier =>content_identifier).first
  end

  # Update the database record corresponding to the given configuration part or
  # create a new one if necessary.
  def self.save_configuration_part(file_name, file_data)
    content_identifier = get_content_identifier(file_name)
    file_rec = DistributedFiles.get_by_content_id(content_identifier)
    if file_rec != nil
      file_rec.update_attributes!(
          :file_name => file_name,
          :content_identifier => content_identifier,
          :file_data => file_data,
          :file_updated_at => Time.now())
    else
      DistributedFiles.create!(
          :file_name => file_name,
          :content_identifier => content_identifier,
          :file_data => file_data,
          :file_updated_at => Time.now())
    end
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
    return Java::ee.ria.xroad.commonui.OptionalPartsConf.new(
        OptionalConfParts.get_optional_parts_dir())
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

  def self.get_file_name(content_identifier)
    case content_identifier
    when PrivateParameters::CONTENT_ID_PRIVATE_PARAMETERS
      return PrivateParameters::FILE_NAME_PRIVATE_PARAMETERS
    when SharedParameters::CONTENT_ID_SHARED_PARAMETERS
      return SharedParameters::FILE_NAME_SHARED_PARAMETERS
    else
      raise "No content identifier available for file name '#{file_name}'"
    end
  end

  def self.get_required_configuration_parts_as_json(content_identifiers)
    result = []

    content_identifiers.each do |content_identifier|
      files_relation = nil
      if CommonSql.ha_configured? &&
          DistributedFiles.node_local_content_id?(content_identifier)
        ha_node_name = CommonSql.ha_node_name
        files_relation = DistributedFiles.where(
          :content_identifier => content_identifier,
          :ha_node_name => ha_node_name)
      else
        files_relation = DistributedFiles.where(
          :content_identifier => content_identifier)
      end

      if files_relation.empty?
        result << get_configuration_part_as_json(
            content_identifier, get_file_name(content_identifier), nil)
      else
        files_relation.each do |file|
          updated = CenterUtils::format_time(file.file_updated_at.localtime)
          result << get_configuration_part_as_json(
              content_identifier, file.file_name, updated)
        end
      end
    end

    return result
  end

  def self.get_optional_configuration_parts_as_json()
    result = []

    get_optional_parts_conf().getAllParts().each do |conf_part|
      file_name = conf_part.fileName
      content_identifier = conf_part.contentIdentifier

      file_in_db = nil
      if CommonSql.ha_configured? &&
          DistributedFiles.node_local_content_id?(content_identifier)
        ha_node_name = CommonSql.ha_node_name
        file_in_db = DistributedFiles.where(
          :file_name => file_name, :ha_node_name => ha_node_name).first()
      else
        file_in_db = DistributedFiles.where(:file_name => file_name).first()
      end
      update_time = file_in_db ?
          CenterUtils::format_time(file_in_db.file_updated_at.localtime) : nil

      result << get_configuration_part_as_json(
          content_identifier, file_name, update_time, true)
    end

    return result
  end

  def self.get_configuration_part_as_json(
      content_identifier, file_name, updated, optional = false)
    {
      :content_identifier => content_identifier,
      :file_name => file_name,
      :updated_at => updated,
      :optional => optional
    }
  end
end
