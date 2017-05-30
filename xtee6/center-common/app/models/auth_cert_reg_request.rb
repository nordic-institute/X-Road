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

# Request for registering authentication certificate of a security server.
# Fields: security_server, auth_cert, address, origin
# Origin: either :security_server or :center
# Usage:
# AuthCertRegRequest.new(
#     :security_server => serverId,
#     :auth_cert => base64bytes,
#     :address => "0.0.0.0",
#     :origin => origin).register()
class AuthCertRegRequest < RequestWithProcessing
  def find_processing
    AuthCertRegProcessing.find_by_server_and_cert(security_server, auth_cert)
  end

  def new_processing
    AuthCertRegProcessing.new
  end

  def verify_request()
    require_client(security_server.owner_id) if from_center?
    verify_against_waiting_requests()
    verify_against_submitted_requests()
    verify_against_existing_server_certs()
  end

  # Revokes auth cert reg request with specific database id. Raises error if not
  # found.
  def self.revoke(request_id)
    reg_request = AuthCertRegRequest.find(request_id)

    raise "No auth cert registration request with id '#{id}'" unless reg_request

    if !reg_request.can_revoke?
      raise "Cannot revoke authentication certificate registration request "\
        "in state '#{reg_request.request_processing.status}' and with origin "\
        "'#{reg_request.origin}'"
    end

    del_request_server_id = reg_request.security_server.clean_copy()
    comment = "'#{request_id}' revocation"

    AuthCertDeletionRequest.new(
         :security_server => del_request_server_id,
         :auth_cert => reg_request.auth_cert,
         :comments => comment,
         :origin => reg_request.origin).register()
  end

  def get_revoking_request_id
    request = AuthCertDeletionRequest.joins(:security_server).where(
      :identifiers => {
        :xroad_instance => security_server.xroad_instance,
        :member_class => security_server.member_class,
        :member_code => security_server.member_code,
        :server_code => security_server.server_code},
      :auth_cert => auth_cert,
      :origin => origin).first

    request ? request.id : nil
  end

  private

  def verify_against_waiting_requests
    waiting_requests_with_same_auth_cert =
        AuthCertRegRequest.joins(:request_processing).where(
      :request_processings => {
          :status => RequestProcessing::WAITING},
      :auth_cert => self.auth_cert)

    waiting_requests_with_same_auth_cert.each do |each|
      if has_same_origin?(each)
        abort_registration(each)
      end
    end
  end

  def verify_against_submitted_requests
    submitted_request =
        AuthCertRegRequest.joins(:request_processing).where(
      :request_processings => {
        :status => RequestProcessing::SUBMITTED_FOR_APPROVAL},
      :auth_cert => self.auth_cert).first

    if submitted_request != nil
      abort_registration(submitted_request)
    end
  end

  def verify_against_existing_server_certs
    existing_cert = AuthCert.where(:cert => self.auth_cert).first

    if existing_cert != nil
      registering_request = AuthCertRegRequest.where(
        :auth_cert => self.auth_cert).first
      raise InvalidAuthCertRegRequestException.new(
          I18n.t("requests.security_server_exists",
              :id => registering_request.id))
    end
  end

  def abort_registration(other_request)
    raise InvalidAuthCertRegRequestException.new(
        I18n.t("requests.request_with_same_cert_already_exists",
            :id => other_request.id))
  end

  def has_same_origin?(other_request)
    return other_request.origin == self.origin
  end
end
