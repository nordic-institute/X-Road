class OcspInfo < ActiveRecord::Base
  attr_accessible :ca_info_id, :cert, :url

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
end
