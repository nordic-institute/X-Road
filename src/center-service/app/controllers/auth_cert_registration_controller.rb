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

require 'thread'

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.request.ManagementRequestHandler
java_import Java::ee.ria.xroad.common.request.ManagementRequestParser
java_import Java::ee.ria.xroad.common.request.ManagementRequestUtil
java_import Java::ee.ria.xroad.common.request.ManagementRequests
java_import Java::ee.ria.xroad.common.message.SoapUtils
java_import Java::ee.ria.xroad.common.message.SoapFault
java_import Java::ee.ria.xroad.common.ErrorCodes
java_import Java::ee.ria.xroad.common.CodedException

class AuthCertRegistrationController < ManagementRequestController
    @@auth_cert_registration_mutex = Mutex.new
    @@host_validator = CommonUi::ValidationUtils::HostValidator.new

    private

    def handle_request
        service = @request_soap.getService().getServiceCode()
        case service
            when ManagementRequests::AUTH_CERT_REG
                handle_auth_cert_registration
            else
                raise "Unknown service code '#{service}'"
        end
    end

    def handle_auth_cert_registration
        req_type = ManagementRequestParser.parseAuthCertRegRequest(@request_soap)
        security_server = security_server_id(req_type.getServer())
        check_security_server_identifiers(security_server)
        verify_xroad_instance(security_server)
        verify_owner(security_server)

        address = req_type.address
        @@host_validator.validate(address, :address)

        req = nil
        auth_cert_reg_request = nil

        auth_cert_bytes = String.from_java_bytes(req_type.getAuthCert())

        owner = member_id(req_type.getServer())
        # Auto-approval must be enabled and Security Server owner must be registered on Central Server
        auto_approve_and_owner_exists = auto_approve_auth_cert_reg_requests? && !SecurityServerClient.find_by_id(owner).nil?

        @@auth_cert_registration_mutex.synchronize do
            req = AuthCertRegRequest.new(
                :security_server => security_server,
                :auth_cert => auth_cert_bytes,
                :address => address,
                :origin => Request::SECURITY_SERVER)
            req.register()

            if auto_approve_and_owner_exists
                auth_cert_reg_request = AuthCertRegRequest.new(
                    :security_server => security_server,
                    :auth_cert => auth_cert_bytes,
                    :address => address,
                    :origin => Request::CENTER)
                auth_cert_reg_request.register()
            end
        end

        if auto_approve_and_owner_exists
            RequestWithProcessing.approve(auth_cert_reg_request.id)
        end

        req.id
    end

    def auto_approve_auth_cert_reg_requests?
        Java::ee.ria.xroad.common.SystemProperties::getCenterAutoApproveAuthCertRegRequests
    end

end
