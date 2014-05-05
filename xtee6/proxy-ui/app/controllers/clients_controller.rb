java_import Java::ee.cyber.sdsb.common.conf.serverconf.AuthorizedSubjectType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.ClientType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.LocalGroupType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.GroupMemberType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.ServiceType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.WsdlType
java_import Java::ee.cyber.sdsb.common.identifier.GlobalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.LocalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType
java_import Java::ee.cyber.sdsb.common.identifier.SecurityCategoryId
java_import Java::ee.cyber.sdsb.common.SystemProperties
java_import Java::ee.cyber.sdsb.proxyui.SignerProxy
java_import Java::ee.cyber.sdsb.proxyui.WSDLParser

class ClientsController < ApplicationController

  STATE_SAVED = "saved"
  STATE_REGINPROG = "registration in progress"
  STATE_REGISTERED = "registered"
  STATE_DELINPROG = "deletion in progress"
  STATE_GLOBALERR = "global error"

  include SubjectsSearch
  include CertTransformationHelper

  def index
    authorize!(:view_clients)

    @instances = [globalconf.root.instanceIdentifier]

    @member_classes = []
    globalconf.root.memberClass.each do |memberClass|
      @member_classes << memberClass.code
    end

    @security_categories = []
    globalconf.root.securityCategory.each do |category|
      @security_categories << category.code
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
      :search_member_name => [],
      :search_member_class => [],
      :search_member_code => [],
      :search_show_subsystems => []
    })

    members = []
    globalconf.root.member.each do |member|
       if (!params[:search_member_class] ||
            params[:search_member_class].include?(member.memberClass)) &&
          (!params[:search_member_name] ||
            member.name.include?(params[:search_member_name])) &&
           member.memberCode.include?(params[:search_member_code])
    
        members << {
          :member_name => member.name,
          :member_class => member.memberClass,
          :member_code => member.memberCode,
          :subsystem_code => nil
        }

        member.subsystem.each do |subsystem|
          members << {
            :member_name => member.name,
            :member_class => member.memberClass,
            :member_code => member.memberCode,
            :subsystem_code => subsystem.subsystemCode
          }
        end if params[:search_show_subsystems]
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
    serverconf.root.client.each do |client|
      if client.identifier.equals(client_id)
        raise "Client already exists"
      end
    end

    member_name = get_member_name(
      params[:add_member_class], params[:add_member_code])

    member_string = "#{member_name} #{params[:add_member_class]}: #{params[:add_member_code]}"

    if !member_name
      warn("new_member", "The person/organisation '#{member_string}' is not registered as SDSB member.")
    end

    if params[:add_subsystem_code] && !globalconf_subsystems.include?(client_id)
      warn("new_subsys", "New subsystem '#{params[:add_subsystem_code]}' will be registered for member '#{member_string}'.")
    end

    client = ClientType.new
    client.identifier = client_id
    client.fullName = member_name
    client.clientStatus = STATE_SAVED
    client.isAuthentication = "NOSSL"

    serverconf.root.client.add(client)
    serverconf.write

    export_services

    render_json(read_clients)
  end

  def client_certificates
    authorize!(:view_client_details)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client_id = get_cached_client_id(params[:client_id])
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
            :state => cert.active ? "in use" : "disabled",
            :expires => cert_obj.not_after.strftime("%F")
          }
        end if key.certs
      end if token.keyInfo
    end

    render_json(certs)
  rescue Java::java.lang.Exception
    render_java_error($!)
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

    register_client(client_id)

    client = get_client(client_id.toString)
    client.clientStatus = STATE_REGINPROG

    serverconf.write

    render_json(client_to_json(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
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
      raise "Security server owner cannot be deleted"
    end

    unregister_client(client_id)

    client = get_client(client_id.toString)
    client.clientStatus = STATE_DELINPROG

    serverconf.write

    render_json(client_to_json(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def client_delete
    authorize!(:delete_client)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    if client.identifier == owner_identifier
      raise "Security server owner cannot be deleted"
    end

    serverconf.root.client.remove(client)
    serverconf.write

    # keep this client's id in cache in case its certs are also
    # deleted
    clients = read_clients
    cache_client_id(client)

    render_json(clients)
  rescue Java::java.lang.Exception
    render_java_error($!)
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
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def client_groups
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    render_json(read_groups(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_add
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :add_group_code => [RequiredValidator.new],
      :add_group_description => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    group = LocalGroupType.new
    group.groupCode = params[:add_group_code]
    group.description = params[:add_group_description]
    group.updated = now_xml

    client.localGroup.add(group)

    serverconf.write

    render_json(read_groups(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_delete
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :group_code => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    deleted_group = nil
    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]
      deleted_group = group
    end

    client.localGroup.remove(deleted_group)

    group_id = LocalGroupId.create(params[:group_code])
    
    client.wsdl.each do |wsdl|
      wsdl.service.each do |service|
        remove_subject(service.authorizedSubject, group_id)
      end
    end

    serverconf.write

    export_services

    render_json(read_groups(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_members
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :group_code => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    render_json(read_group_members(client, params[:group_code]))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_members_add
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :group_code => [RequiredValidator.new],
      :member_ids => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])
    group = nil

    client.localGroup.each do |local_group|
      if local_group.groupCode == params[:group_code]
        group = local_group
        break
      end
    end

    params[:member_ids].each do |member_id|
      groupMember = GroupMemberType.new
      groupMember.groupMemberId = get_cached_subject_id(member_id)
      groupMember.added = now_xml
      group.groupMember.add(groupMember)
    end

    group.updated = now_xml

    serverconf.write

    export_services

    render_json(read_group_members(client, params[:group_code]))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_members_remove
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :group_code => [RequiredValidator.new],
      :member_ids => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])
    group = nil

    client.localGroup.each do |local_group|
      if local_group.groupCode == params[:group_code]
        group = local_group
        break
      end
    end

    removed_members = []

    params[:member_ids].each do |member_id|
      group.groupMember.each do |member|
        cached_id = get_cached_subject_id(member_id)

        if member.groupMemberId.equals(cached_id)
          removed_members << member
        end
      end
    end

    group.groupMember.removeAll(removed_members)
    group.updated = now_xml

    serverconf.write

    export_services

    render_json(read_group_members(client, params[:group_code]))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def group_description_edit
    authorize!(:view_client_groups)

    validate_params({
      :client_id => [RequiredValidator.new],
      :group_code => [RequiredValidator.new],
      :description => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]

      group.description = params[:description]
    end

    serverconf.write

    render_json(read_groups(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def client_services
    authorize!(:view_client_services)

    validate_params({
      :client_id => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    render_json(read_services(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def wsdl_add
    authorize!(:add_wsdl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_add_type => [RequiredValidator.new],
      :wsdl_add_url => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    existing_services = []
    client.wsdl.each do |wsdl|
      raise "WSDL already exists" if wsdl.url == params[:wsdl_add_url]

      wsdl.service.each do |service|
        existing_services << service.serviceCode
      end
    end

    backend_sdsb = params[:wsdl_add_type] != "adapter"
    service_mediator = SystemProperties::getServiceMediatorAddress()

    wsdl_url = backend_sdsb ? params[:wsdl_add_url] :
      "#{service_mediator}?backend=#{params[:wsdl_add_url]}"

    parsed_services = WSDLParser::parseWSDL(wsdl_url)

    wsdl = WsdlType.new
    wsdl.url = params[:wsdl_add_url]
    wsdl.wsdlLocation = nil
    wsdl.disabled = true
    wsdl.disabledNotice = "Out of order"
    wsdl.publish = false
    wsdl.publishedDate = nil
    wsdl.refreshedDate = now_xml
    unless backend_sdsb
      wsdl.backend = "adapter"
      wsdl.backendURL = params[:wsdl_add_url]
    end

    parsed_services.each do |parsed_service|
      if existing_services.include?(parsed_service.name)
        raise "Service '#{parsed_service.name}' already exists in another WSDL"
      end

      service = ServiceType.new
      service.serviceCode = parsed_service.name
      service.serviceVersion = parsed_service.version
      service.title = parsed_service.title
      service.url = parsed_service.url
      service.timeout = 60

      wsdl.service.add(service)
    end

    client.wsdl.add(wsdl)

    serverconf.write

    export_services

    render_json(read_services(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def wsdl_disable
    authorize!(:enable_disable_wsdl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_ids => [RequiredValidator.new],
      :wsdl_disabled_notice => [],
      :enable => []
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless params[:wsdl_ids].include?(wsdl.url)
      wsdl.disabled = params[:enable].nil?
      wsdl.disabledNotice = params[:wsdl_disabled_notice] if params[:enable].nil?
    end

    serverconf.write

    render_json(read_services(client))
  end

  def wsdl_refresh
    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_ids => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    added = {}
    added_objs = {}
    deleted = {}

    # parse each wsdl
    client.wsdl.each do |wsdl|
      next unless params[:wsdl_ids].include?(wsdl.url)

      service_mediator = SystemProperties::getServiceMediatorAddress()

      wsdl_url = wsdl.backend != "adapter" ? wsdl.url :
        "#{service_mediator}?backend=#{wsdl.url}"

      services_parsed = WSDLParser::parseWSDL(wsdl_url)

      services_old = []
      wsdl.service.each do |service|
        services_old << service.serviceCode
      end

      services_new = []
      added_objs[wsdl.url] = []
      services_parsed.each do |service_parsed|
        services_new << service_parsed.name

        unless services_old.include?(service_parsed.name)
          added_objs[wsdl.url] << service_parsed
        end
      end

      added[wsdl.url] = services_new - services_old
      deleted[wsdl.url] = services_old - services_new
    end

    unless added.values.flatten.empty?
      add_text = t("clients.adding_services",
                    :added => added.values.join(", "))
    end
    unless deleted.values.flatten.empty?
      delete_text = t("clients.deleting_services",
                    :deleted => deleted.values.join(", "))
    end

    unless deleted.values.flatten.empty? && added.values.flatten.empty?
      warn("changed_services", "#{add_text}#{delete_text}")
    end

    # write changes to conf
    client.wsdl.each do |wsdl|
      services_deleted = []

      deleted[wsdl.url].each do |name|
        wsdl.service.each do |service|
          if service.serviceCode == name
            services_deleted << service
          end
        end
      end if deleted.has_key?(wsdl.url)

      services_deleted.each do |service|
        wsdl.service.remove(service)
      end

      added_objs[wsdl.url].each do |service_parsed|
        service = ServiceType.new
        service.serviceCode = service_parsed.name
        service.serviceVersion = service_parsed.version
        service.title = service_parsed.title
        service.url = service_parsed.url
        service.timeout = 60

        wsdl.service.add(service)
      end if deleted.has_key?(wsdl.url)

      if params[:wsdl_ids].include?(wsdl.url)
        wsdl.refreshedDate = now_xml
      end
    end

    serverconf.write

    export_services

    render_json(read_services(client))
  rescue Java::java.lang.Exception
    render_java_error($!)
 end

  def wsdl_delete
    authorize!(:delete_wsdl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_ids => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    deleted = []
    client.wsdl.each do |wsdl|
      deleted << wsdl if params[:wsdl_ids].include?(wsdl.url)
    end

    deleted.each do |wsdl|
      client.wsdl.remove(wsdl)
    end

    serverconf.write

    export_services

    render_json(read_services(client))
  end

  def service_params
    authorize!(:edit_service_params)

    validate_params({
      :client_id => [RequiredValidator.new],
      :params_wsdl_id => [RequiredValidator.new],
      :params_name => [RequiredValidator.new],
      :params_url => [RequiredValidator.new],
      :params_url_all => [],
      :params_timeout => [RequiredValidator.new],
      :params_timeout_all => [],
      :params_security_category => [],
      :params_security_category_all => []
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless wsdl.url == params[:params_wsdl_id]

      wsdl.service.each do |service|
        service_match = params[:params_name] == service.serviceCode

        if params[:params_url_all] || service_match
          service.url = params[:params_url]
        end

        if params[:params_timeout_all] || service_match
          service.timeout = params[:params_timeout].to_i
        end

        if params[:params_security_category_all] || service_match
          service.requiredSecurityCategory.clear

          params[:params_security_category].each do |category|
            sdsb = globalconf.root.instanceIdentifier
            category_id = SecurityCategoryId.create(sdsb, category)
            service.requiredSecurityCategory.add(category_id)
          end if params[:params_security_category]
        end
      end
    end

    serverconf.write

    export_services

    render_json(read_services(client))
  end

  def adapter_params
    authorize!(:edit_service_params)

    validate_params({
      :client_id => [RequiredValidator.new],
      :params_adapter_id => [RequiredValidator.new],
      :params_adapter_timeout => [RequiredValidator.new]
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless wsdl.url == params[:params_adapter_id]

      wsdl.service.each do |service|
        service.timeout = params[:params_adapter_timeout].to_i
      end
    end

    serverconf.write

    export_services

    render_json(read_services(client))
  end

  def service_acl
    authorize!(:view_service_acl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_id => [RequiredValidator.new],
      :name => [RequiredValidator.new]
    })

    service = get_service(params[:client_id], params[:wsdl_id], params[:name])

    render_json(read_subjects(service))
  end

  def subjects_add
    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_id => [RequiredValidator.new],
      :name => [RequiredValidator.new],
      :subject_ids => [RequiredValidator.new]
    })

    service = get_service(params[:client_id], params[:wsdl_id], params[:name])

    params[:subject_ids].each do |subject_id|
      authorized_subject = AuthorizedSubjectType.new
      authorized_subject.subjectId = get_cached_subject_id(subject_id)
      authorized_subject.rightsGiven = now_xml

      service.authorizedSubject.add(authorized_subject)
    end

    serverconf.write

    export_services

    render_json(read_subjects(service))
  end

  def subjects_remove
    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [RequiredValidator.new],
      :wsdl_id => [RequiredValidator.new],
      :name => [RequiredValidator.new],
      :subject_ids => []
    })

    service = get_service(params[:client_id], params[:wsdl_id], params[:name])

    if params[:subject_ids]
      params[:subject_ids].each do |subject_id|
        remove_subject(service.authorizedSubject,
          get_cached_subject_id(subject_id))
      end
    else
      service.authorizedSubject.clear
    end

    serverconf.write

    export_services

    render_json(read_subjects(service))
  end

  def get_internal_certs
    authorize!(:view_internal_certificates)

    validate_params({
      :client_id => []
    })

    client = get_client(params[:client_id])
    certs = get_internal_certs_fingerprints(client)
    
    render_json(certs)
  end

  def get_internal_cert
    authorize!(:view_internal_certficate)

    validate_params({
      :client_id => [],
      :fingerprint => []
    })

    details = nil
    client = get_client(params[:client_id])
    cert_data = get_cert(client, params[:fingerprint])

    digest = CryptoUtils::certHash(cert_data[:cert].to_der.to_java_bytes)
    details = {
      :cert_dump => %x[echo "#{cert_data[:cert].to_s}" | openssl x509 -text -noout 2>&1],
      :cert_hash => CryptoUtils::encodeBase64(digest)
    }
    render_json(details)
  end

  def import_internal_cert
    authorize!(:add_internal_certificate)

    validate_params({
      :cert => [],
      :client_id => []
    })

    client = get_client(params[:client_id])
    certs = get_internal_certs_fingerprints(client)

    cert = OpenSSL::X509::Certificate.new(params[:cert].read)
    client.isCert.add(Base64.encode64(cert.to_pem))
    serverconf.write

    certs << { :fingerprint => get_fingerprint(cert.to_der) }

    upload_success(certs, 'certImportCallback')
  end

  def delete_internal_cert
    authorize!(:delete_internal_certificate)

    validate_params({
      :client_id => [],
      :fingerprint => []
    })

    certificate = nil
    client = get_client(params[:client_id])

    client.isCert.each do |cert|
      c = OpenSSL::X509::Certificate.new(cert.to_s)
      next unless get_fingerprint(c.to_der) == params[:fingerprint]
      certificate = cert
    end

    client.isCert.remove(certificate)
    serverconf.write

    certs = get_internal_certs_fingerprints(client)

    render_json(certs)
  end

  def get_ssl_cert
    authorize!(:view_ssl_certificate)

    raw_cert = OpenSSL::X509::Certificate.new(serverconf.root.getInternalSSLCert.to_s)
    cert = Digest::MD5.hexdigest(raw_cert.to_der)
    render_json(cert)
  end

  def export_ssl_cert
    authorize!(:export_ssl_certificate)

    raw_cert = OpenSSL::X509::Certificate.new(serverconf.root.getInternalSSLCert.to_s)
    data = export_cert(raw_cert)
    send_data data, :filename => "certs.tar.gz"
  end

  def get_connection_type
    authorize!(:view_connection_type)

    validate_params({
      :client_id => []
    })

    connection = get_client(params[:client_id]).getIsAuthentication

    render_json({ :connection_type => connection })
  end

  def edit_connection_type
    authorize!(:edit_connection_type)
    
    validate_params({
      :client_id => [],
      :connection_type => []
    })

    client = get_client(params[:client_id])

    serverconf.root.client.remove(client)
    client.setIsAuthentication(params[:connection_type])
    serverconf.root.client.add(client)
    serverconf.write

    render_json({ :connection_type => nil })
  end

  private

  def read_clients
    clients = []
    write_conf = false

    # check if any clients have been registered
    # TODO: should be done upon receiving globalconf
    registered_clients = []
    local_server_id = read_server_id

    globalconf.root.securityServer.each do |server|
      if extract_server_id(server) == local_server_id
        registered_clients << owner_identifier

        server.client.each do |client|
          registered_clients << globalconf_member_to_client_id(client)
        end

        break
      end
    end

    serverconf.root.client.each do |client|
      registered = registered_clients.include?(client.identifier)

      if client.clientStatus == STATE_REGINPROG && registered
        client.clientStatus = STATE_REGISTERED
        write_conf = true
      end

      # owner skips STATE_REGINPROG
      if client.identifier == owner_identifier &&
          client.clientStatus == STATE_SAVED && registered
        client.clientStatus = STATE_REGISTERED
        write_conf = true
      end

      if client.clientStatus == STATE_REGISTERED && !registered
        client.clientStatus = STATE_GLOBALERR
      end

      # set client name if it has appeared in globalconf
      unless client.fullName
        logger.debug("#{client.identifier} has no name, checking globalconf")

        name = get_member_name(
          client.identifier.memberClass, client.identifier.memberCode)

        if name
          logger.debug("found name #{name}")
          client.fullName = name
          write_conf = true
        else
          logger.debug("name not found")          
        end
      end

      clients << client_to_json(client)
    end

    serverconf.write if write_conf

    cache_client_ids
    clients
  end

  def client_to_json(client)
    {
      :client_id => client.identifier.toString,
      :member_name => client.fullName,
      :member_class => client.identifier.memberClass,
      :member_code => client.identifier.memberCode,
      :subsystem_code => client.identifier.subsystemCode,
      :state => client.clientStatus,
      :contact => client.contacts,
      :register_enabled =>
        [STATE_SAVED].include?(client.clientStatus),
      :unregister_enabled =>
        [STATE_REGINPROG, STATE_REGISTERED].include?(client.clientStatus),
      :delete_enabled =>
        [STATE_SAVED, STATE_DELINPROG, STATE_GLOBALERR].include?(
            client.clientStatus),
      :owner => serverconf.root.owner.id == client.getId
    }
  end

  def cache_client_ids
    session[:client_ids] = {}

    serverconf.root.client.each do |client|
      session[:client_ids][client.identifier.toString] = client.identifier
    end

    session[:client_ids]
  end

  def cache_client_id(client)
    session[:client_ids][client.identifier.toString] = client.identifier
  end

  def get_cached_client_id(key)
    session[:client_ids][key]
  end

  def get_client(key)
    client_id = get_cached_client_id(key)

    serverconf.root.client.each do |client|
      return client if client.identifier == client_id
    end

    nil
  end

  def get_service(client_id, wsdl_id, service_name)
    get_client(client_id).wsdl.each do |wsdl|
      next unless wsdl.url == wsdl_id

      wsdl.service.each do |service|
        if service.serviceCode == service_name
          return service
        end
      end
    end

    nil
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

  def read_groups(client)
    groups = []

    client.localGroup.each do |group|
      groups << {
        :code => group.groupCode,
        :description => group.description,
        :member_count => group.groupMember.size,
        :updated => format_xml_time(group.updated)
      }
    end

    groups
  end

  def read_group_members(client, group_code)
    clear_cached_subject_ids

    members = []
    client.localGroup.each do |group|
      next unless group.groupCode == group_code

      group.groupMember.each do |member|
        member_id = member.groupMemberId
        cache_subject_id(member_id)

        members << {
          :member_id => member_id.toString,
          :name => get_member_name(member_id.memberClass, member_id.memberCode),
          :code => member_id.memberCode,
          :class => member_id.memberClass,
          :subsystem => member_id.subsystemCode,
          :sdsb => member_id.sdsbInstance,
          :type => member_id.objectType.toString,
          :added => format_xml_time(member.added)
        }
      end
    end

    members
  end

  def read_services(client)
    services = []

    client.wsdl.each do |wsdl|
      adapter = wsdl.backend == "adapter"
      name = adapter ? "Adapter" : "WSDL"
      name += " DISABLED" if wsdl.disabled
      name += " (#{wsdl.url})"

      adapter_timeout =
        wsdl.service.isEmpty ? nil : wsdl.service.get(0).timeout

      services << {
        :wsdl => true,
        :wsdl_id => wsdl.url,
        :adapter => adapter,
        :name => name,
        :title => nil,
        :url => nil,
        :timeout => (adapter_timeout if adapter),
        :security_category => nil,
        :publish => wsdl.publish,
        :last_published => format_xml_time(wsdl.publishedDate),
        :last_refreshed => format_xml_time(wsdl.refreshedDate),
        :disabled => wsdl.disabled,
        :disabled_notice => wsdl.disabledNotice,
        :open => false
      }

      wsdl.service.each do |service|
        categories = []
        service.requiredSecurityCategory.each do |category|
          categories << category.categoryCode
        end

        services << {
          :wsdl => false,
          :wsdl_id => wsdl.url,
          :adapter => false,
          :name => service.serviceCode,
          :title => service.title,
          :url => service.url || "",
          :timeout => (service.timeout unless adapter),
          :security_category => categories,
          :publish => wsdl.publish,
          :last_published => format_xml_time(wsdl.publishedDate),
          :last_refreshed => format_xml_time(wsdl.refreshedDate),
          :disabled => wsdl.disabled,
          :open => false
        }
      end
    end

    services
  end

  def read_subjects(service)
    clear_cached_subject_ids

    subjects = []
    service.authorizedSubject.each do |authorized_subject|
      cache_subject_id(authorized_subject.subjectId)

      subjects << subject_hash(
        authorized_subject.subjectId,
        authorized_subject.rightsGiven)
    end

    subjects
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

  def get_member_name(member_class, member_code)
    logger.debug("finding member name for: #{member_class}, #{member_code}")

    globalconf.root.member.each do |member|
      if member_class == member.memberClass && member_code == member.memberCode
        return member.name
      end
    end if member_class && member_code

    return nil
  end

  def get_cert(client, fingerprint)
    data = nil
    client.isCert.each do |cert|
      c = OpenSSL::X509::Certificate.new(cert.to_s)
      next unless get_fingerprint(c.to_der) == params[:fingerprint]
      data = {
        :bytes => cert,
        :cert => c
      }
    end
    data
  end

  def get_internal_certs_fingerprints(client)
    certs = []
    client.isCert.each do |cert|
      c = OpenSSL::X509::Certificate.new(cert.to_s)
      certs << { :fingerprint => get_fingerprint(c.to_der) }
    end
    certs
  end
end
