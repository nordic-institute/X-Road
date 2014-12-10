module AuthCertHelper
  include CertTransformationHelper

  private

  def add_auth_cert_reg_request(server_data, temp_cert_id)
    auth_cert = get_temp_cert_from_session(temp_cert_id)
    auth_cert_reg_request = AuthCertRegRequest.new(
        :security_server => SecurityServerId.from_parts(
            SystemParameter.instance_identifier,
            server_data[:owner_class],
            server_data[:owner_code],
            server_data[:server_code]),
        :auth_cert => auth_cert,
        :origin => Request::CENTER)

    auth_cert_reg_request.register()

    logger.debug("Auth cert request '#{auth_cert_reg_request.inspect}'
        registered successfully")

    auth_cert_reg_request
  end
end
