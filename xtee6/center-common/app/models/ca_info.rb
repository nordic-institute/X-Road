class CaInfo < ActiveRecord::Base
  has_many :ocsp_infos,
    :dependent => :destroy,
    :autosave => true

  before_validation :validate_cert_presence

  before_save do |ca|
    cert_obj = CommonUi::CertUtils.cert_object(ca.cert)
    ca.valid_from = cert_obj.not_before
    ca.valid_to = cert_obj.not_after

    logger.info("Saving CA: '#{ca}'")
  end

  before_destroy do |ca|
    logger.info("Deleting CA: '#{ca}'")
  end

  def validate_cert_presence
    if !self.cert || self.cert.empty?
      raise SdsbArgumentError.new(:no_ca_cert)
    end
  end

  def to_s
    "CaInfo(validFrom: '#{self.valid_from}', validTo: '#{self.valid_to}', "\
    "id: '#{self.id}', ocspInfos: [#{self.ocsp_infos.join(', ')}]"
  end
end
