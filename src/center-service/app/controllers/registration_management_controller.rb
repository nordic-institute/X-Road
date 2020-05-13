#
# The MIT License
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

class RegistrationManagementController < ManagementRequestController
    @@client_registration_mutex = Mutex.new

    private

    def handle_request
        service = @request_soap.getService().getServiceCode()
        case service
            when ManagementRequests::AUTH_CERT_DELETION
                handle_auth_cert_deletion
            when ManagementRequests::CLIENT_REG
                handle_client_registration
            when ManagementRequests::CLIENT_DELETION
                handle_client_deletion
            when ManagementRequests::OWNER_CHANGE
                handle_owner_change
            else
                raise "Unknown service code '#{service}'"
        end
    end

    def handle_auth_cert_deletion
        req_type = ManagementRequestParser.parseAuthCertDeletionRequest(
            @request_soap)
        security_server = security_server_id(req_type.getServer())
        check_security_server_identifiers(security_server)

        verify_xroad_instance(security_server)
        verify_owner(security_server)

        req = AuthCertDeletionRequest.new(
            :security_server => security_server,
            :auth_cert => String.from_java_bytes(req_type.getAuthCert()),
            :origin => Request::SECURITY_SERVER)
        req.register()
        req.id
    end

    def handle_client_registration
        req_type = ManagementRequestParser.parseClientRegRequest(@request_soap)
        security_server = security_server_id(req_type.getServer())
        check_security_server_identifiers(security_server)
        server_user = client_id(req_type.getClient())
        check_client_identifiers(server_user)

        verify_xroad_instance(security_server)
        verify_xroad_instance(server_user)

        verify_owner(security_server)

        req = nil
        client_reg_request = nil

        server_user_member = SecurityServerClient.find_by_id(member_id(req_type.getClient()))

        # Requests can be automatically approved when:
        # 1) auto approval is enabled;
        # 2) client registration request has been signed by the member owning the client to be added,
        #    and if signature and certificate have passed verification;
        # 3) member owning the subsystem exists on Central Server.
        auto_approve_and_request_verified_and_owner_exists = auto_approve_client_reg_requests? &&
                        @client_reg_request_status_wrapper.getClientRegRequestSignedAndVerified &&
                        !server_user_member.nil?

        @@client_registration_mutex.synchronize do
            req = ClientRegRequest.new(
                :security_server => security_server,
                :sec_serv_user => server_user,
                :origin => Request::SECURITY_SERVER)
            req.register()

            if auto_approve_and_request_verified_and_owner_exists
                client_reg_request = ClientRegRequest.new(
                    :security_server => security_server,
                    :sec_serv_user => server_user,
                    :origin => Request::CENTER)
                client_reg_request.register()
            end
        end

        if auto_approve_and_request_verified_and_owner_exists
            # If subsystem to be added does not exist on Central Server yet, it
            # must be created before the approval
            if SecurityServerClient.find_by_id(server_user).nil?
                Subsystem.create!(
                    :xroad_member => server_user_member,
                    :subsystem_code => req_type.getClient().getSubsystemCode())
                logger.info("New subsystem created: #{server_user}")
            end
            RequestWithProcessing.approve(client_reg_request.id)
        end

        req.id
    end

    def handle_client_deletion
        req_type = ManagementRequestParser.parseClientDeletionRequest(@request_soap)
        security_server = security_server_id(req_type.getServer())
        check_security_server_identifiers(security_server)
        server_user = client_id(req_type.getClient())
        check_client_identifiers(server_user)

        verify_xroad_instance(security_server)
        verify_xroad_instance(server_user)

        verify_owner(security_server)

        req = ClientDeletionRequest.new(
            :security_server => security_server,
            :sec_serv_user => server_user,
            :origin => Request::SECURITY_SERVER)
        req.register()
        req.id
    end

    def handle_owner_change
        req_type = ManagementRequestParser.parseOwnerChangeRequest(@request_soap)
        security_server = security_server_id(req_type.getServer())
        check_security_server_identifiers(security_server)
        server_user = client_id(req_type.getClient())
        check_client_identifiers(server_user)

        verify_xroad_instance(security_server)
        verify_xroad_instance(server_user)

        verify_owner(security_server)

        req = nil
        owner_change_request = nil

        new_owner = SecurityServerClient.find_by_id(member_id(req_type.getClient()))

        # Auto-approval must be enabled and new owner must be registered on Central Server
        auto_approve_and_new_owner_exists = auto_approve_owner_change_requests? && !new_owner.nil?

        @@client_registration_mutex.synchronize do
            req = OwnerChangeRequest.new(
                :security_server => security_server,
                :sec_serv_user => server_user,
                :origin => Request::SECURITY_SERVER)
            req.register()

            owner_change_request = OwnerChangeRequest.new(
                :security_server => security_server,
                :sec_serv_user => server_user,
                :origin => Request::CENTER)
            owner_change_request.register()
        end

        if auto_approve_and_new_owner_exists
            RequestWithProcessing.approve(owner_change_request.id)
        end

        req.id
    end

    def auto_approve_client_reg_requests?
        Java::ee.ria.xroad.common.SystemProperties::getCenterAutoApproveClientRegRequests
    end

    def auto_approve_owner_change_requests?
        Java::ee.ria.xroad.common.SystemProperties::getCenterAutoApproveOwnerChangeRequests
    end

end
