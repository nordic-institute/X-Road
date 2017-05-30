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

java_import Java::java.util.UUID

java_import Java::ee.ria.xroad.common.conf.globalconf.privateparameters.ObjectFactory
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.commonui.SignerProxy
java_import Java::ee.ria.xroad.signer.protocol.dto.KeyUsageInfo

class ConfigurationSource < ActiveRecord::Base
  SOURCE_TYPE_INTERNAL = "internal"
  SOURCE_TYPE_EXTERNAL = "external"

  ANCHOR_FILE_HASH_ALGORITHM = CryptoUtils::DEFAULT_ANCHOR_HASH_ALGORITHM_ID

  SIGNING_KEY_CERT_CN = "N/A"
  SIGNING_KEY_CERT_NOT_BEFORE = Time.utc(1970)
  SIGNING_KEY_CERT_NOT_AFTER = Time.utc(2038)

  has_many :configuration_signing_keys,
      :autosave => true,
      :dependent => :destroy

  belongs_to :active_key,
      :class_name => "ConfigurationSigningKey",
      :foreign_key => "active_key_id"

  validates :source_type, :presence => true

  # Return an existing or new record with the given source type, local to the
  # database node this instance is running on.
  # For new records, the node name will be set by a trigger function.
  def self.get_source_by_type(source_type)
    unless [SOURCE_TYPE_INTERNAL, SOURCE_TYPE_EXTERNAL].include?(source_type)
      raise "Invalid configuration source type"
    end

    if CommonSql.ha_configured?
      ha_node_name = CommonSql.ha_node_name
      return ConfigurationSource.where(
          :source_type => source_type, :ha_node_name => ha_node_name).first ||
        ConfigurationSource.create!({ :source_type => source_type })
    end
    return ConfigurationSource.where(:source_type => source_type).first ||
        ConfigurationSource.create!({ :source_type => source_type })
  end

  # Return all the records with the given source type, regardless of the
  # originating database node.
  def self.get_all_sources_by_type(source_type)
    return ConfigurationSource.where(:source_type=> source_type).all
  end

  def self.get_internal_signing_key
    get_source_by_type(SOURCE_TYPE_INTERNAL).active_key
  end

  def self.get_external_signing_key
    get_source_by_type(SOURCE_TYPE_EXTERNAL).active_key
  end

  def generate_anchor
    unless SystemParameter.instance_identifier
      raise "System parameter for instance identifier not set"
    end

    unless SystemParameter.central_server_address
      raise "System parameter for central server address not set"
    end

    if configuration_signing_keys.empty?
      raise "No configuration signing keys configured"
    end

    now = Time.now

    marshaller = ConfMarshaller.new(ObjectFactory,
        Proc.new do |factory|
          factory.createConfigurationAnchor(
              factory.createConfigurationAnchorType)
        end)

    marshaller.root.instanceIdentifier = SystemParameter.instance_identifier
    add_sources(marshaller)
    marshaller.root.generatedAt = ConfMarshaller::xml_time(now)

    anchor_file = marshaller.write_to_string
    anchor_file_hash = CryptoUtils::hexDigest(
      ANCHOR_FILE_HASH_ALGORITHM, anchor_file.to_java_bytes)

    # Although a single record is used as the base for generating the anchor,
    # all the records with the same type and an active key will be updated.
    # This is to propagate the new anchor to all the available nodes in HA
    # systems.
    # Assuming the clocks are kept in sync with NTP so the timestamp is valid
    # on each node.
    ConfigurationSource.where(:source_type => source_type).where(
      "configuration_sources.active_key_id IS NOT NULL").update_all(
        :anchor_file => anchor_file,
        :anchor_file_hash => anchor_file_hash.upcase.scan(/.{1,2}/).join(':'),
        :anchor_generated_at => now
      )
  end

  def generate_signing_key(token_id)
    key_info = SignerProxy::generateKey(token_id)

    begin
      cert = SignerProxy::generateSelfSignedCert(
        key_info.id,
        dummy_client_id,
        KeyUsageInfo::SIGNING,
        SIGNING_KEY_CERT_CN,
        SIGNING_KEY_CERT_NOT_BEFORE,
        SIGNING_KEY_CERT_NOT_AFTER)
    rescue
      begin
        SignerProxy::deleteKey(key_info.id, true)
      rescue
        logger.error("Error deleting generated signing key: #{$!.message}")
      end

      raise $!
    end

    key_record = ConfigurationSigningKey.new
    key_record.token_identifier = token_id
    key_record.key_identifier = key_info.id
    key_record.key_generated_at = Time.now
    key_record.cert = String.from_java_bytes(cert)

    update_attributes!({
      :active_key => key_record
    }) unless active_key

    configuration_signing_keys << key_record

    key_record
  end

  private

  def dummy_client_id
    Java::ee.ria.xroad.common.identifier.ClientId.create(
      SystemParameter.instance_identifier, "selfsigned", UUID.randomUUID.toString)
  end

  # Add all the configuration sources of the current type, representing
  # each available database node, to the XML of the anchor.
  def add_sources(marshaller)
    source_dir = (source_type == SOURCE_TYPE_INTERNAL) \
      ? SystemProperties::getCenterInternalDirectory \
      : SystemProperties::getCenterExternalDirectory

    ha_configured = CommonSql.ha_configured?

    ConfigurationSource.get_all_sources_by_type(source_type).each do |source|
      source_xml = marshaller.factory.createConfigurationSourceType
      central_server_address = nil
      if ha_configured
        central_server_address = SystemParameter.where(
          :key => SystemParameter::CENTRAL_SERVER_ADDRESS,
          :ha_node_name => source.ha_node_name).first.value
      else
        central_server_address = SystemParameter.where(
          :key => SystemParameter::CENTRAL_SERVER_ADDRESS).first.value
      end
      source_xml.downloadURL =
        "http://" + central_server_address + "/" + source_dir
      source.configuration_signing_keys.find_each do |key|
        source_xml.getVerificationCert.add(key.cert.to_java_bytes)
      end
      marshaller.root.source.add(source_xml)
    end
  end
end
