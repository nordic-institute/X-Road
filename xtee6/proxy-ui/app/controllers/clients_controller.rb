java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ClientType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl
java_import Java::ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx
java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType
java_import Java::ee.cyber.sdsb.commonui.SignerProxy

class ClientsController < ApplicationController

  include Clients::Groups
  include Clients::InternalCerts
  include Clients::Services
  include Clients::AclSubjects

  def index
    authorize!(:view_clients)

    @instances = [globalconf.root.instanceIdentifier]

    @member_classes = []
    globalconf.root.memberClass.each do |memberClass|
      @member_classes << memberClass.code
    end

    @security_categories = []
    globalconf.root.securityCategory.each do |category|
      @security_categories << category
    end

    @subject_types = [
      SdsbObjectType::MEMBER.toString(),
      SdsbObjectType::SUBSYSTEM.toString(),
      SdsbObjectType::GLOBALGROUP.toString(),
      SdsbObjectType::LOCALGROUP.toString(),
    ]

    @member_types = [
      SdsbObjectType::MEMBER.toString(),
      SdsbObjectType::SUBSYSTEM.toString()
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
    globalconf.root.member.each do |member|
      if match(member.memberClass, params[:search_member]) ||
          match(member.memberCode, params[:search_member]) ||
          match(member.name, params[:search_member])

        members << {
          :member_name => member.name,
          :member_class => member.memberClass,
          :member_code => member.memberCode,
          :subsystem_code => nil
        }
      end

      member.subsystem.each do |subsystem|
        if match(subsystem.subsystemCode, params[:search_member])
          members << {
            :member_name => member.name,
            :member_class => member.memberClass,
            :member_code => member.memberCode,
            :subsystem_code => subsystem.subsystemCode
          }
        end
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
    authorize!(:add_client)

    validate_params({
      :add_member_class => [RequiredValidator.new],
      :add_member_code => [RequiredValidator.new],
      :add_subsystem_code => []
    })

    if params[:add_subsystem_code].empty?
      params[:add_subsystem_code] = nil
    end

    client_id = ClientId.create(
      globalconf.root.instanceIdentifier,
      params[:add_member_class],
      params[:add_member_code],
      params[:add_subsystem_code])

    # check if client exists in serverconf
    serverconf.client.each do |client|
      if client.identifier.equals(client_id)
        raise t('clients.client_exists')
      end
    end

    member_name = get_member_name(
      params[:add_member_class], params[:add_member_code])

    member_string = "#{member_name} #{params[:add_member_class]}: " \
      "#{params[:add_member_code]}"

    if !member_name
      warn("new_member", t('clients.unregistered_member', :member => member_string))
    end

    if params[:add_subsystem_code] && !globalconf_subsystems.include?(client_id)
      warn("new_subsys", t('clients.new_subsystem',
        :subsystem => params[:add_subsystem_code], :member => member_string))
    end

    client = ClientType.new
    client.identifier = get_identifier(client_id)
    client.clientStatus = ClientType::STATUS_SAVED
    client.isAuthentication = "NOSSL"
    client.conf = serverconf

    serverconf.client.add(client)
    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_clients)
  end

  def client_certs
    authorize!(:view_client_details)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client_id = get_client(params[:client_id]).identifier
    member_id = to_member_id(client_id)

    tokens = SignerProxy::getTokens

    certs = []
    tokens.each do |token|
      token.keyInfo.each do |key|
        key.certs.each do |cert|
          next if cert.memberId != member_id

          cert_bytes = String.from_java_bytes(cert.certificateBytes)
          cert_obj = OpenSSL::X509::Certificate.new(cert_bytes)

          certs << {
            :csp => cert_csp(cert_obj),
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
    authorize!(:send_client_reg_req)

    validate_params({
      :member_class => [RequiredValidator.new],
      :member_code => [RequiredValidator.new],
      :subsystem_code => []
    })

    if params[:subsystem_code] && params[:subsystem_code].empty?
      params[:subsystem_code] = nil
    end

    client_id = ClientId.create(
      globalconf.root.instanceIdentifier,
      params[:member_class],
      params[:member_code],
      params[:subsystem_code])

    if x55_installed?
      member_id = to_member_id(client_id)
      sign_cert_exists = false

      catch :cert_checked do
        SignerProxy::getTokens.each do |token|
          token.keyInfo.each do |key|
            next unless key.usage == KeyUsageInfo::SIGNING

            key.certs.each do |cert|
              if cert.memberId == member_id
                sign_cert_exists = true
                throw :cert_checked
              end
            end
          end
        end
      end

      unless sign_cert_exists
        raise t('clients.cannot_register_without_sign_cert')
      end
    end

    register_client(client_id)

    client = get_client(client_id.toString)
    client.clientStatus = ClientType::STATUS_REGINPROG

    serverconf_save

    render_json(client_to_json(client))
  end

  def client_delreq
    authorize!(:send_client_del_req)

    validate_params({
      :member_class => [RequiredValidator.new],
      :member_code => [RequiredValidator.new],
      :subsystem_code => []
    })

    if params[:subsystem_code] && params[:subsystem_code].empty?
      params[:subsystem_code] = nil
    end

    client_id = ClientId.create(
      globalconf.root.instanceIdentifier,
      params[:member_class],
      params[:member_code],
      params[:subsystem_code])

    if client_id == owner_identifier
      raise t('clients.cannot_delete_owner')
    end

    unregister_client(client_id)

    client = get_client(client_id.toString)
    client.clientStatus = ClientType::STATUS_DELINPROG

    serverconf_save

    render_json(client_to_json(client))
  end

  def client_delete
    authorize!(:delete_client)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

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
        ask_delete_certs = false
      end
    end

    after_commit do
      export_services(client.identifier)
    end

    render_json({
      :clients => clients,
      :ask_delete_certs => ask_delete_certs
    })
  end

  def client_delete_certs
    authorize!(:delete_client)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client_id = get_cached_client_id(params[:client_id])
    member_id = to_member_id(client_id)

    SignerProxy::getTokens.each do |token|
      token.keyInfo.each do |key|
        key.certs.each do |cert|
          if cert.memberId == member_id
            SignerProxy::deleteCert(cert.id)
          end
        end

        key.certRequests.each do |cert_request|
          if cert_request.memberId == member_id
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

    serverconf.client.each do |client|
      clients << client_to_json(client)
    end

    cache_client_ids

    clients
  end

  def client_to_json(client)
    {
      :client_id => client.identifier.toString,
      :member_name => get_member_name(
        client.identifier.memberClass, client.identifier.memberCode),
      :type => client.identifier.objectType.toString,
      :instance => client.identifier.sdsbInstance,
      :member_class => client.identifier.memberClass,
      :member_code => client.identifier.memberCode,
      :subsystem_code => client.identifier.subsystemCode,
      :state => client.clientStatus,
      :contact => client.contacts,
      :register_enabled =>
        [ClientType::STATUS_SAVED].include?(client.clientStatus),
      :unregister_enabled =>
        [ClientType::STATUS_REGINPROG,
         ClientType::STATUS_REGISTERED].include?(client.clientStatus),
      :delete_enabled =>
        [ClientType::STATUS_SAVED,
         ClientType::STATUS_DELINPROG,
         ClientType::STATUS_GLOBALERR].include?(client.clientStatus),
      :owner => serverconf.owner.id == client.getId,
      :can_view_client_details => can?(:view_client_details),
      :can_view_client_services => can?(:view_client_services),
      :can_view_client_local_groups => can?(:view_client_local_groups),
      :can_view_client_acl_subjects => can?(:view_client_acl_subjects),
      :can_view_client_internal_certs => can?(:view_client_internal_certs)
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
    ClientDAOImpl.instance.getClient(
      ServerConfDatabaseCtx.session, session[:client_ids][key])
  end

  def globalconf_subsystems
    subsystems = []

    globalconf.root.member.each do |member|
      member.subsystem.each do |subsystem|
        subsystems << ClientId.create(
          globalconf.root.instanceIdentifier,
          member.memberClass, member.memberCode,
          subsystem.subsystemCode)
      end
    end

    subsystems
  end

  def cert_csp(cert)
    issuer_cn = ""
    cert.issuer.to_a.each do |part|
      if part[0] == "CN"
        issuer_cn = part[1]
        break
      end
    end
    issuer_cn
  end
end
