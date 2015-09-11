class AuthCert < ActiveRecord::Base
  validates_presence_of :security_server_id
  validates_uniqueness_of :cert

  belongs_to :security_server

  before_validation do |record|
    existing_certs = AuthCert.where(:cert => record.cert)

    unless existing_certs.empty?
      existing_cert = existing_certs[0]
      security_server = existing_cert.security_server

      raise I18n.t("errors.request.auth_cert_not_unique",
          {:server_id => security_server.get_server_id()})
    end
  end
end
