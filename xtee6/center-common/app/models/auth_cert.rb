class AuthCert < ActiveRecord::Base
  include Validators

  validates :security_server_id, :present =>true
  validates :certificate, :unique => true

  belongs_to :security_server
end
