class OcspInfo < ActiveRecord::Base
  include Validators

  attr_accessible :ca_info_id, :cert, :url
  validates :url, :url => true

  validates_with Validators::MaxlengthValidator

  before_destroy do |ocsp|
    logger.info("Deleting OCSP: '#{ocsp}'")
  end

  before_destroy do |ocsp|
    logger.info("Deleting OCSP: '#{ocsp}'")
  end

  def to_s
    cert_size = cert != nil ? cert.size : 0
    "OcspInfo(id: '#{id}', url: '#{url}', cert length '#{cert_size}')"
  end

  def self.validate_urls(urls)
    urls.each do |each|
      ocsp_info = OcspInfo.new(:url => each)

      next if ocsp_info.valid?

      error_msg = ocsp_info.errors.full_messages.join(", ")
      raise (error_msg)
    end
  end
end
