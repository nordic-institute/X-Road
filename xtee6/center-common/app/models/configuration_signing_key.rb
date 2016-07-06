class ConfigurationSigningKey < ActiveRecord::Base
  belongs_to :configuration_source

  def self.validate(signing_key)
    unless signing_key.is_a?(ConfigurationSigningKey)
      raise "No configuration signing key found"
    end

    if signing_key.key_identifier.blank?
      raise "Signing key must have key identifier"
    end

    if signing_key.cert.blank?
      raise "Signing key must have certificate"
    end
  end
end
