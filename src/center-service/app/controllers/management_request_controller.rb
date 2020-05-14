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

require 'java'
require 'thread'

java_import Java::ee.ria.xroad.common.request.ClientRegRequestStatusWrapper
java_import Java::ee.ria.xroad.common.request.ManagementRequestHandler
java_import Java::ee.ria.xroad.common.request.ManagementRequestParser
java_import Java::ee.ria.xroad.common.request.ManagementRequestUtil
java_import Java::ee.ria.xroad.common.request.ManagementRequests
java_import Java::ee.ria.xroad.common.message.SoapUtils
java_import Java::ee.ria.xroad.common.message.SoapFault
java_import Java::ee.ria.xroad.common.ErrorCodes
java_import Java::ee.ria.xroad.common.CodedException
java_import Java::ee.ria.xroad.common.validation.SpringFirewallValidationRules

class ManagementRequestController < ApplicationController

    def create
        begin
            response.content_type = "text/xml"

            @xroad_instance = SystemParameter.instance_identifier
            raise "X-Road instance must exist!" if @xroad_instance.blank?

            @client_reg_request_status_wrapper = ClientRegRequestStatusWrapper.new

            @request_soap = ManagementRequestHandler.readRequest(
                request.headers["CONTENT_TYPE"],
                StringIO.new(request.raw_post).to_inputstream,
                @client_reg_request_status_wrapper)

            id = handle_request
            logger.debug("Created request id: #{id}")

            # Simply convert request message to response message
            response_soap = ManagementRequestUtil.toResponse(@request_soap, id)

            render :text => response_soap.getXml()
        rescue Java::java.lang.Exception => e
            handle_error(ErrorCodes.translateException(e))
        rescue Exception => e
            handle_error(CodedException.new(ErrorCodes::X_INTERNAL_ERROR, e.message))
            logger.error("Internal error: #{e.message}\n#{e.backtrace.join("\n\t")}")
        end
    end

    private

    def handle_request
        raise "Unknown service"
    end

    def handle_error(ex)
        render :text => SoapFault.createFaultXml(ex)
    end

    def security_server_id(id_type)
        SecurityServerId.from_parts(id_type.getXRoadInstance(),
                                    id_type.getMemberClass(), id_type.getMemberCode(),
                                    id_type.getServerCode())
    end

    def client_id(id_type)
        ClientId.from_parts(id_type.getXRoadInstance(), id_type.getMemberClass(),
                            id_type.getMemberCode(), id_type.getSubsystemCode())
    end

    def member_id(id_type)
        ClientId.from_parts(id_type.getXRoadInstance(), id_type.getMemberClass(),
                            id_type.getMemberCode())
    end

    def verify_owner(security_server)
        sender = client_id(@request_soap.getClient())
        verify_xroad_instance(sender)

        if not security_server.matches_client_id(sender)
            raise I18n.t("request.server_id_not_match_owner",
                         :security_server => security_server.to_s,
                         :sec_serv_owner => sender.to_s)
        end
    end

    def check_security_server_identifiers(ss)
      if ss.blank?
        check_identifier(ss.xroad_instance)
        check_identifier(ss.member_class)
        check_identifier(ss.member_code)
        check_identifier(ss.server_code)
      end
    end

    def check_client_identifiers(ss)
      if ss.blank?
        check_identifier(ss.xroad_instance)
        check_identifier(ss.member_class)
        check_identifier(ss.member_code)
        check_identifier(ss.subsystem_code)
      end
    end

    def check_identifier(id)
      unless id.blank?
        if SpringFirewallValidationRules::containsPercent(id) ||
          SpringFirewallValidationRules::containsSemicolon(id) ||
          SpringFirewallValidationRules::containsForwardslash(id) ||
          SpringFirewallValidationRules::containsBackslash(id) ||
          !SpringFirewallValidationRules::isNormalized(id)
          raise I18n.t("request.invalid_identifier", :id => id)
        end
      end
    end

    # xroad_id may be either ClientId or ServerId.
    def verify_xroad_instance(xroad_id)
        logger.debug("Instance verification: #{xroad_id}")

        unless @xroad_instance.eql?(xroad_id.xroad_instance)
            raise t("request.incorrect_instance")
        end
    end
end
