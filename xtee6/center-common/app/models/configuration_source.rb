java_import Java::java.util.UUID

java_import Java::ee.ria.xroad.common.conf.globalconf.privateparameters.ObjectFactory
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.commonui.SignerProxy
java_import Java::ee.ria.xroad.signer.protocol.dto.KeyUsageInfo

class ConfigurationSource < ActiveRecord::Base
  SOURCE_TYPE_INTERNAL = "internal"
  SOURCE_TYPE_EXTERNAL = "external"

  SIGNING_KEY_CERT_CN = "N/A"
  SIGNING_KEY_CERT_NOT_BEFORE = Time.utc(1970)
  SIGNING_KEY_CERT_NOT_AFTER = Time.utc(2038)

  has_many :configuration_signing_keys,
      :autosave => true,
      :dependent => :destroy

  belongs_to :active_key,
      :class_name => "ConfigurationSigningKey",
      :foreign_key => "active_key_id"

  validates :source_type, :uniqueness => true, :presence => true

  def self.get_source_by_type(source_type)
    unless [SOURCE_TYPE_INTERNAL, SOURCE_TYPE_EXTERNAL].include?(source_type)
      raise "Invalid configuration source type"
    end

    ConfigurationSource.where(:source_type => source_type).first ||
      ConfigurationSource.create!({ :source_type => source_type })
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
    marshaller.root.source.add(generate_source(marshaller))
    marshaller.root.generatedAt = ConfMarshaller::xml_time(now)

    anchor_file = marshaller.write_to_string
    anchor_file_hash = CryptoUtils::hexDigest(
        CryptoUtils::SHA224_ID, anchor_file.to_java_bytes)

    update_attributes!({
      :anchor_file => anchor_file,
      :anchor_file_hash => anchor_file_hash.upcase.scan(/.{1,2}/).join(':'),
      :anchor_generated_at => now
    })
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
      SignerProxy::deleteKey(key_info.id)
      raise $!
    end

    key_record = ConfigurationSigningKey.new
    key_record.token_identifier = token_id
    key_record.key_identifier = key_info.id
    key_record.key_generated_at = Time.now
    key_record.certificate = String.from_java_bytes(cert)

    update_attributes!({
      :active_key => key_record
    }) unless active_key

    configuration_signing_keys << key_record
  end

  private

  def dummy_client_id
    Java::ee.ria.xroad.common.identifier.ClientId.create(
      SystemParameter.instance_identifier, "selfsigned", UUID.randomUUID.toString)
  end

  def generate_source(marshaller)
    source_dir = (source_type == SOURCE_TYPE_INTERNAL) \
      ? SystemProperties::getCenterInternalDirectory \
      : SystemProperties::getCenterExternalDirectory

    source_xml = marshaller.factory.createConfigurationSourceType
    source_xml.downloadURL =
      "http://" + SystemParameter.central_server_address + "/" + source_dir

    configuration_signing_keys.find_each do |key|
      source_xml.getVerificationCert.add(key.certificate.to_java_bytes)
    end

    source_xml
  end
end
