#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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

java_import Java::ee.ria.xroad.common.conf.serverconf.model.ClientType
java_import Java::ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl
java_import Java::ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx
java_import Java::ee.ria.xroad.common.identifier.XRoadObjectType
java_import Java::ee.ria.xroad.commonui.SignerProxy

class ClientsController < ApplicationController

  include Clients::Groups
  include Clients::InternalCerts
  include Clients::Services
  include Clients::AclSubjects

  @@lock_client_add = Mutex.new

  def index
    authorize!(:view_clients)

    @instances = GlobalConf::getInstanceIdentifiers

    @member_classes = GlobalConf::getMemberClasses
    @member_classes_instance = GlobalConf::getMemberClasses(xroad_instance)

    @subject_types = [
      XRoadObjectType::SUBSYSTEM.toString(),
      XRoadObjectType::GLOBALGROUP.toString(),
      XRoadObjectType::LOCALGROUP.toString()
    ]
  end

  def clients_refresh
    authorize!(:view_clients)

    render_json(read_clients)
  end

  def clients_search
    authorize!(:view_clients)

    validate_params({
      :search_member => []
    })

    members = []
    GlobalConf::getMembers(xroad_instance).each do |member|
      if match(member.id.memberClass, params[:search_member]) ||
          match(member.id.memberCode, params[:search_member]) ||
          match(member.id.subsystemCode, params[:search_member]) ||
          match(member.name, params[:search_member])

        members << {
          :member_name => member.name,
          :member_class => member.id.memberClass,
          :member_code => member.id.memberCode,
          :subsystem_code => member.id.subsystemCode
        }
      end
    end

    render_json(members)
  end

  def client_name
    authorize!(:add_client)

    validate_params({
      :add_member_class => [],
      :add_member_code => []
    })

    name = get_member_name(params[:add_member_class], params[:add_member_code])

    render_json(:name => name)
  end

  def client_add
    audit_log("Add client", audit_log_data = {})

    authorize!(:add_client)

    validate_params({
      :add_member_class => [:required],
      :add_member_code => [:required],
      :add_subsystem_code => []
    })

    if params[:add_subsystem_code] && !params[:add_subsystem_code].empty?
      client_id = ClientId.create(
        xroad_instance,
        params[:add_member_class],
        params[:add_member_code],
        params[:add_subsystem_code])
    else
      client_id = ClientId.create(
        xroad_instance,
        params[:add_member_class],
        params[:add_member_code])
    end

    # check if client exists in serverconf and
    # if serverconf already includes one registered
    # member in addition to the owner member
    serverconf.client.each do |client|
      if client.identifier.equals(client_id)
        raise t('clients.client_exists')
      elsif client_id.subsystemCode.nil? &&
          client.identifier.subsystemCode.nil? &&
          client.identifier != owner_identifier
        raise t('clients.cannot_register_another_member')
      end
    end

    member_name = get_member_name(
      params[:add_member_class], params[:add_member_code])

    if !member_name
      warn("new_member", t('clients.unregistered_member', {
        :member_name => member_name,
        :member_class => Encode.forHtml(client_id.memberClass),
        :member_code => Encode.forHtml(client_id.memberCode)
      }))
    end

    client_id = get_identifier(client_id)
    registered = GlobalConf::isSecurityServerClient(client_id, @server_id)

    client = ClientType.new
    client.identifier = client_id
    client.clientStatus = registered ?
      ClientType::STATUS_REGISTERED : ClientType::STATUS_SAVED
    client.isAuthentication = "SSLAUTH"
    client.conf = serverconf

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:isAuthentication] =
      isAuthenticationToUIStr(client.isAuthentication)
    audit_log_data[:clientStatus] = client.clientStatus

    serverconf.client.add(client)
    serverconf_save

    render_json({
      :clients => read_clients,
      :registered => registered,
      :subsystem_registered => subsystem_registered?(client_id),
      :member_name => member_name
    })
  end

  synchronize :client_add, :with => :@@lock_client_add

  def client_certs
    authorize!(:view_client_details_dialog)

    validate_params({
      :client_id => [:required]
    })

    client_id = get_client(params[:client_id]).identifier

    tokens = SignerProxy::getTokens

    certs = []
    tokens.each do |token|
      token.keyInfo.each do |key|
        key.certs.each do |cert|
          next unless client_id.memberEquals(cert.memberId)

          cert_bytes = String.from_java_bytes(cert.certificateBytes)
          cert_obj = OpenSSL::X509::Certificate.new(cert_bytes)

          certs << {
            :csp => CommonUi::CertUtils.cert_csp(cert_obj),
            :serial => cert_obj.serial.to_s,
            :state => cert.active ?
              t('clients.cert_in_use') : t('clients.cert_disabled'),
            :expires => cert_obj.not_after.strftime("%F")
          }
        end if key.certs
      end if token.keyInfo
    end

    render_json(certs)
  end

  def client_regreq
    audit_log("Register client", audit_log_data = {})

    authorize!(:send_client_reg_req)

    validate_params({
      :member_class => [:required],
      :member_code => [:required],
      :subsystem_code => []
    })

    if params[:subsystem_code] && params[:subsystem_code].empty?
      params[:subsystem_code] = nil
    end

    client_id = ClientId.create(
      xroad_instance,
      params[:member_class],
      params[:member_code],
      params[:subsystem_code])

    audit_log_data[:clientIdentifier] = client_id

    if client_id == owner_identifier
      raise t('clients.cannot_register_owner')
    end

    request_id = register_client(client_id)
    audit_log_data[:managementRequestId] = request_id

    client = get_client(client_id.toString)
    client.clientStatus = ClientType::STATUS_REGINPROG

    audit_log_data[:clientStatus] = client.clientStatus

    serverconf_save

    render_json(client_to_json(client))
  end

  def client_delreq
    audit_log("Unregister client", audit_log_data = {})

    authorize!(:send_client_del_req)

    validate_params({
      :member_class => [:required],
      :member_code => [:required],
      :subsystem_code => []
    })

    if params[:subsystem_code] && params[:subsystem_code].empty?
      params[:subsystem_code] = nil
    end

    client_id = ClientId.create(
      xroad_instance,
      params[:member_class],
      params[:member_code],
      params[:subsystem_code])

    audit_log_data[:clientIdentifier] = client_id

    if client_id == owner_identifier
      raise t('clients.cannot_delete_owner')
    end

    request_id = unregister_client(client_id)
    audit_log_data[:managementRequestId] = request_id

    client = get_client(client_id.toString)
    client.clientStatus = ClientType::STATUS_DELINPROG

    audit_log_data[:clientStatus] = client.clientStatus

    serverconf_save

    render_json(client_to_json(client))
  end

  def client_delete
    audit_log("Delete client", audit_log_data = {})

    authorize!(:delete_client)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    if client.identifier == owner_identifier
      raise t('clients.cannot_delete_owner')
    end

    serverconf.client.remove(client)
    serverconf_save

    # keep this client's id in cache in case its certs are also
    # deleted
    clients = read_clients
    cache_client_id(client)

    deleted_id = client.identifier
    ask_delete_certs = true

    serverconf.client.each do |client|
      client_id = client.identifier

      if client_id.memberClass == deleted_id.memberClass &&
          client_id.memberCode == deleted_id.memberCode
        # other clients using same cert
        ask_delete_certs = false
      end
    end

    catch(:done) do
      SignerProxy::getTokens.each do |token|
        token.keyInfo.each do |key|
          key.certs.each do |cert|
            if cert.memberId &&
                cert.memberId.memberClass == deleted_id.memberClass &&
                cert.memberId.memberCode == deleted_id.memberCode
              throw :done
            end
          end

          key.certRequests.each do |cert_request|
            if cert_request.memberId &&
                cert_request.memberId.memberClass == deleted_id.memberClass &&
                cert_request.memberId.memberCode == deleted_id.memberCode
              throw :done
            end
          end
        end
      end

      # no certs or requests found for deleted client
      ask_delete_certs = false
    end if ask_delete_certs

    render_json({
      :clients => clients,
      :ask_delete_certs => ask_delete_certs
    })
  end

  def owner_change_request
    audit_log("Change owner", audit_log_data = {})

    authorize!(:send_owner_change_req)

    validate_params({
      :member_class => [:required],
      :member_code => [:required]
    })

    client_id = ClientId.create(
      xroad_instance,
      params[:member_class],
      params[:member_code])

    audit_log_data[:clientIdentifier] = client_id

    if client_id == owner_identifier
      raise t('clients.already_owner')
    end

    request_id = change_owner(client_id)
    audit_log_data[:managementRequestId] = request_id

    client = get_client(client_id.toString)

    audit_log_data[:clientStatus] = client.clientStatus

    render_json(client_to_json(client))
  end

  def client_delete_certs
    audit_log("Delete client certificates", audit_log_data = {})

    authorize!(:delete_client)

    validate_params({
      :client_id => [:required]
    })

    client_id = get_cached_client_id(params[:client_id])

    audit_log_data[:clientIdentifier] = client_id
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm
    audit_log_data[:certHashes] = []
    audit_log_data[:certRequestIds] = []

    SignerProxy::getTokens.each do |token|
      token.keyInfo.each do |key|
        key.certs.each do |cert|
          if client_id.memberEquals(cert.memberId)
            audit_log_data[:certHashes] <<
              CommonUi::CertUtils.cert_hash(cert.certificateBytes)

            SignerProxy::deleteCert(cert.id)
          end
        end

        key.certRequests.each do |cert_request|
          if client_id.memberEquals(cert_request.memberId)
            audit_log_data[:certRequestIds] << cert_request.id

            SignerProxy::deleteCertRequest(cert_request.id)
          end
        end
      end
    end

    render_json
  end

  private

  def read_clients
    clients = []
    registered_subsystems = []

    GlobalConf::getMembers(xroad_instance).each do |member|
      registered_subsystems << member.id if member.id.subsystemCode
    end

    serverconf.client.each do |client|
      clients << client_to_json(client, registered_subsystems)
    end

    cache_client_ids

    clients
  end

  def subsystem_registered?(client_id)
    registered = GlobalConf::getMembers(xroad_instance).index do |member|
      member.id == client_id
    end

    !registered.nil?
  end

  def client_to_json(client, registered_subsystems = nil)
    is_subsystem = client.identifier.subsystemCode

    unless registered_subsystems
      registered_subsystems = []
      registered_subsystems << client.identifier if subsystem_registered?(client.identifier)
    end

    {
      :client_id => client.identifier.toString,
      :member_name => GlobalConf::getMemberName(client.identifier),
      :type => client.identifier.objectType.toString,
      :instance => client.identifier.xRoadInstance,
      :member_class => client.identifier.memberClass,
      :member_code => client.identifier.memberCode,
      :subsystem_code => client.identifier.subsystemCode,
      :subsystem_registered => registered_subsystems.include?(client.identifier),
      :state => client.clientStatus,
      :register_enabled =>
        [ClientType::STATUS_SAVED].include?(client.clientStatus),
      :unregister_enabled =>
        [ClientType::STATUS_REGINPROG,
         ClientType::STATUS_REGISTERED].include?(client.clientStatus),
      :delete_enabled =>
        [ClientType::STATUS_SAVED,
         ClientType::STATUS_DELINPROG,
         ClientType::STATUS_GLOBALERR].include?(client.clientStatus),
      :owner_change_enabled =>
          serverconf.owner.id != client.id &&
          !is_subsystem &&
          [ClientType::STATUS_REGISTERED].include?(client.clientStatus),
      :owner => serverconf.owner.id == client.id,
      :can_view_client_details_dialog =>
          can?(:view_client_details_dialog),
      :can_view_client_services =>
          can?(:view_client_services) && is_subsystem,
      :can_view_client_local_groups =>
          can?(:view_client_local_groups) && is_subsystem,
      :can_view_client_acl_subjects =>
          can?(:view_client_acl_subjects) && is_subsystem,
      :can_view_client_internal_certs =>
          can?(:view_client_internal_certs)
    }
  end

  def cache_client_ids
    session[:client_ids] = {}

    serverconf.client.each do |client|
      session[:client_ids][client.identifier.toString] = client.identifier
    end

    session[:client_ids]
  end

  def cache_client_id(client)
    session[:client_ids][client.identifier.toString] = client.identifier
  end

  def get_cached_client_id(key)
    get_identifier(session[:client_ids][key])
  end

  def get_client(key)
    unless session[:client_ids]
      cache_client_ids
    end

    if session[:client_ids][key].nil?
      raise t('clients.client_does_not_exist')
    else
      ClientDAOImpl.new.getClient(
          ServerConfDatabaseCtx.session, session[:client_ids][key])
    end

  end
end
