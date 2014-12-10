java_import Java::ee.cyber.sdsb.common.request.ManagementRequestHandler
java_import Java::ee.cyber.sdsb.common.request.ManagementRequestParser
java_import Java::ee.cyber.sdsb.common.request.ManagementRequestUtil
java_import Java::ee.cyber.sdsb.common.request.ManagementRequests
java_import Java::ee.cyber.sdsb.common.message.SoapUtils
java_import Java::ee.cyber.sdsb.common.message.SoapFault
java_import Java::ee.cyber.sdsb.common.ErrorCodes
java_import Java::ee.cyber.sdsb.common.CodedException

class ManagementRequestsController < ApplicationController

  def create
    begin
      response.content_type = "text/xml"

      @sdsb_instance = SystemParameter.instance_identifier
      raise "SDSB instance must exist!" if @sdsb_instance.blank?

      @request_soap = ManagementRequestHandler.readRequest(
        request.headers["CONTENT_TYPE"],
        StringIO.new(request.raw_post).to_inputstream)

      id = handle_request

      # Simply convert request message to response message
      response_soap = ManagementRequestUtil.toResponse(@request_soap, id)
      render :text => response_soap.getXml()
    rescue Java::java.lang.Exception => e
      handle_error(ErrorCodes.translateException(e))
    rescue Exception => e
      handle_error(CodedException.new(ErrorCodes::X_INTERNAL_ERROR, e.message))
      puts "Internal error: #{e.message}\n#{e.backtrace.join("\n\t")}"
    end
  end

  private

  def handle_request
    service = @request_soap.getService().getServiceCode()
    case service
    when ManagementRequests::AUTH_CERT_REG
      handle_auth_cert_registration
    when ManagementRequests::AUTH_CERT_DELETION
      handle_auth_cert_deletion
    when ManagementRequests::CLIENT_REG
      handle_client_registration
    when ManagementRequests::CLIENT_DELETION
      handle_client_deletion
    else
      raise "Unknown service code '#{service}'"
    end
  end

  def handle_error(ex)
    render :text => SoapFault.createFaultXml(ex)
  end

  def handle_auth_cert_registration
    req_type = ManagementRequestParser.parseAuthCertRegRequest(@request_soap)
    security_server = security_server_id(req_type.getServer())

    verify_sdsb_instance(security_server)
    verify_owner(security_server)

    req = AuthCertRegRequest.new(
      :security_server => security_server,
      :auth_cert => String.from_java_bytes(req_type.getAuthCert()),
      :address => req_type.getAddress(),
      :origin => Request::SECURITY_SERVER)
    req.register()
    req.id
  end

  def handle_auth_cert_deletion
    req_type = ManagementRequestParser.parseAuthCertDeletionRequest(
      @request_soap)
    security_server = security_server_id(req_type.getServer())

    verify_sdsb_instance(security_server)
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
    server_user = client_id(req_type.getClient())

    verify_sdsb_instance(security_server)
    verify_sdsb_instance(server_user)

    verify_owner(security_server)

    req = ClientRegRequest.new(
      :security_server => security_server,
      :sec_serv_user => server_user,
      :origin => Request::SECURITY_SERVER)
    req.register()
    req.id
  end

  def handle_client_deletion
    req_type = ManagementRequestParser.parseClientDeletionRequest(@request_soap)
    security_server = security_server_id(req_type.getServer())
    server_user = client_id(req_type.getClient())

    verify_sdsb_instance(security_server)
    verify_sdsb_instance(server_user)

    verify_owner(security_server)

    req = ClientDeletionRequest.new(
      :security_server => security_server,
      :sec_serv_user => server_user,
      :origin => Request::SECURITY_SERVER)
    req.register()
    req.id
  end

  def security_server_id(id_type)
    SecurityServerId.from_parts(id_type.getSdsbInstance(),
      id_type.getMemberClass(), id_type.getMemberCode(),
      id_type.getServerCode())
  end

  def client_id(id_type)
    ClientId.from_parts(id_type.getSdsbInstance(), id_type.getMemberClass(),
      id_type.getMemberCode(), id_type.getSubsystemCode())
  end

  def verify_owner(security_server)
    sender = client_id(@request_soap.getClient())
    verify_sdsb_instance(sender)

    if not security_server.matches_client_id(sender)
      raise I18n.t("requests.serverid.does.not.match.owner",
        :security_server => security_server.to_s,
        :sec_serv_owner => sender.to_s)
    end
  end

  # sdsb_id may be either ClientId or ServerId.
  def verify_sdsb_instance(sdsb_id)
    logger.debug("Instance verification: #{sdsb_id}")

    actual_instance = sdsb_id.sdsb_instance

    unless @sdsb_instance.eql?(actual_instance)
      raise t("request.incorrect_instance", {
          :allowed_instance => @sdsb_instance,
          :actual_instance => actual_instance})
    end
  end
end
