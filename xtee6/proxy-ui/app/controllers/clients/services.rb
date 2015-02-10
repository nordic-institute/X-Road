java_import Java::ee.cyber.sdsb.common.SystemProperties
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.AclType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.AuthorizedSubjectType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.ServiceType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.WsdlType
java_import Java::ee.cyber.sdsb.common.identifier.SecurityCategoryId
java_import Java::ee.cyber.sdsb.proxyui.InternalServerTestUtil
java_import Java::ee.cyber.sdsb.proxyui.WSDLParser

java_import Java::ee.cyber.sdsb.proxyui.combinedwsdl.WSDLCombinationChecker
java_import Java::ee.cyber.sdsb.proxyui.combinedwsdl.InvalidWSDLCombinationException

module Clients::Services

  BACKEND_TYPE_SDSB = "sdsb"
  BACKEND_TYPE_XROADV5 = "xroadv5"
  BACKEND_TYPE_XROADV5_META = "xroadv5_meta"

  XROADV5_METASERVICES = ["getProducerACL", "getServiceACL"]

  def client_services
    authorize!(:view_client_services)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_services(client))
  end

  def adapter_add
    authorize!(:add_wsdl)

    validate_params({
      :client_id => [:required],
      :adapter_add_url => [:required],
      :adapter_add_wsdl_uri => [:required],
      :adapter_add_sslauth => []
    })

    client = get_client(params[:client_id])

    wsdl = WsdlType.new
    wsdl.url = params[:adapter_add_wsdl_uri]
    wsdl.disabled = true
    wsdl.disabledNotice = t('clients.default_disabled_service_notice')
    wsdl.publish = false
    wsdl.refreshedDate = Date.new
    wsdl.client = client
    wsdl.backend = BACKEND_TYPE_XROADV5
    wsdl.backendURL = params[:adapter_add_url]

    parse_and_check_services(adapter_wsdl_url(params[:adapter_add_url]), wsdl)

    service_url = adapter_service_url(client.identifier)
    wsdl.service.each do |service|
      service.url = service_url
      service.sslAuthentication = !params[:adapter_add_sslauth].nil?
    end

    client.wsdl.add(wsdl)

    if params[:adapter_add_sslauth]
      check_internal_server_certs(client, params[:adapter_add_url])
    end

    serverconf_save

    after_commit do
      export_services
      check_wsdls_mergeability(client)
    end

    render_json(read_services(client))
  end

  def wsdl_add
    authorize!(:add_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_add_url => [:required]
    })

    client = get_client(params[:client_id])

    wsdl = WsdlType.new
    wsdl.url = params[:wsdl_add_url]
    wsdl.disabled = true
    wsdl.disabledNotice = t('clients.default_disabled_service_notice')
    wsdl.publish = false
    wsdl.refreshedDate = Date.new
    wsdl.client = client
    wsdl.backend = BACKEND_TYPE_SDSB

    parse_and_check_services(wsdl.url, wsdl)

    client.wsdl.add(wsdl)

    serverconf_save

    after_commit do
      export_services
      check_wsdls_mergeability(client)
    end

    render_json(read_services(client))
  end

  def wsdl_disable
    authorize!(:enable_disable_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required],
      :wsdl_disabled_notice => [],
      :enable => []
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless params[:wsdl_ids].include?(get_wsdl_id(wsdl))

      wsdl.disabled = params[:enable].nil?
      wsdl.disabledNotice = params[:wsdl_disabled_notice] if params[:enable].nil?
    end

    serverconf_save

    render_json(read_services(client))
  end

  def wsdl_refresh
    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required],
      :new_url => []
    })

    # cannot change more than 1 adapter/WSDL URL at a time
    raise ArgumentError if params[:new_url] && params[:wsdl_ids].size > 1

    client = get_client(params[:client_id])

    added = {}
    added_objs = {}
    deleted = {}

    existing_services = {}

    client.wsdl.each do |wsdl|
      wsdl.service.each do |service|
        existing_services[get_service_id(service)] = get_wsdl_id(wsdl)
      end
    end

    check_new_url = false

    # parse each wsdl
    client.wsdl.each do |wsdl|
      next unless params[:wsdl_ids].include?(get_wsdl_id(wsdl))

      if params[:new_url]
        if wsdl.backend != BACKEND_TYPE_XROADV5
          wsdl.url = params[:new_url]
        else
          check_new_url = adapter_ssl_auth?(wsdl)
          wsdl.backendURL = params[:new_url]
        end
      end

      params[:wsdl_ids] << get_wsdl_id(wsdl)

      wsdl_parse_url = wsdl.backend != BACKEND_TYPE_XROADV5 ? wsdl.url :
        adapter_wsdl_url(wsdl.backendURL)

      services_parsed = parse_wsdl(wsdl_parse_url)

      services_old = []

      wsdl.service.each do |service|
        services_old << get_service_id(service)
      end

      services_new = []
      added_objs[get_wsdl_id(wsdl)] = []

      services_parsed.each do |service_parsed|
        next if x55_installed? &&
          XROADV5_METASERVICES.include?(service_parsed.name)

        service_parsed_id =
          format_service_id(service_parsed.name, service_parsed.version)

        services_new << service_parsed_id

        unless services_old.include?(service_parsed_id)
          if existing_services.has_key?(service_parsed_id)
            raise t('clients.service_exists_refresh',
                    :service => service_parsed_id,
                    :wsdl1 => get_wsdl_id(wsdl),
                    :wsdl2 => existing_services[service_parsed_id])
          end

          added_objs[get_wsdl_id(wsdl)] << service_parsed
        end
      end

      added[get_wsdl_id(wsdl)] = services_new - services_old
      deleted[get_wsdl_id(wsdl)] = services_old - services_new
    end

    unless added.values.flatten.empty?
      add_text = t('clients.adding_services',
                    :added => added.values.join(", "))
    end

    unless deleted.values.flatten.empty?
      delete_text = t('clients.deleting_services',
                    :deleted => deleted.values.join(", "))
    end

    unless deleted.values.flatten.empty? && added.values.flatten.empty?
      warn("changed_services", "#{add_text}#{delete_text}")
    end

    # write changes to conf
    client.wsdl.each do |wsdl|
      services_deleted = []

      deleted[get_wsdl_id(wsdl)].each do |service_id|
        wsdl.service.each do |service|
          if get_service_id(service) == service_id
            services_deleted << service
          end
        end
      end if deleted.has_key?(get_wsdl_id(wsdl))

      services_deleted.each do |service|
        service.wsdl = nil
        wsdl.service.remove(service)
        @session.delete(service)
      end

      added_objs[get_wsdl_id(wsdl)].each do |service_parsed|
        service = ServiceType.new
        service.serviceCode = service_parsed.name
        service.serviceVersion = service_parsed.version
        service.title = service_parsed.title
        service.url = (wsdl.backend == BACKEND_TYPE_SDSB) ? service_parsed.url :
          adapter_service_url(client.identifier)
        service.timeout = 60
        service.wsdl = wsdl

        wsdl.service.add(service)
      end if added_objs.has_key?(get_wsdl_id(wsdl))

      if params[:wsdl_ids].include?(get_wsdl_id(wsdl))
        wsdl.refreshedDate = Date.new
      end
    end

    clean_acls(client)

    if check_new_url
      check_internal_server_certs(client, params[:new_url])
    end

    serverconf_save

    after_commit do
      export_services
      check_wsdls_mergeability(client)
    end

    render_json(read_services(client))
  end

  def wsdl_delete
    authorize!(:delete_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required]
    })

    client = get_client(params[:client_id])

    deleted = []
    client.wsdl.each do |wsdl|
      deleted << wsdl if params[:wsdl_ids].include?(get_wsdl_id(wsdl))
    end

    deleted.each do |wsdl|
      wsdl.client = nil
      client.wsdl.remove(wsdl)
      @session.delete(wsdl)
    end

    clean_acls(client)

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_services(client))
  end

  def service_params
    authorize!(:edit_service_params)

    validate_params({
      :client_id => [:required],
      :params_wsdl_id => [:required],
      :params_service_id => [:required],
      :params_url => [:required, :url],
      :params_url_all => [],
      :params_timeout => [:required, :timeout],
      :params_timeout_all => [],
      :params_security_category => [],
      :params_security_category_all => [],
      :params_sslauth => [],
      :params_sslauth_all => []
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless get_wsdl_id(wsdl) == params[:params_wsdl_id]

      # cannot modify a service with backend xroadv5
      raise ArgumentError if wsdl.backend == BACKEND_TYPE_XROADV5

      wsdl.service.each do |service|
        service_match = params[:params_service_id] == get_service_id(service)

        if params[:params_url_all] || service_match
          service.url = params[:params_url]
        end

        if params[:params_timeout_all] || service_match
          service.timeout = params[:params_timeout].to_i
        end

        if params[:params_security_category_all] || service_match
          service.requiredSecurityCategory.clear

          params[:params_security_category].each do |category|
            category_id = SecurityCategoryId.create(sdsb_instance, category)
            service.requiredSecurityCategory.add(category_id)
          end if params[:params_security_category]
        end

        if params[:params_sslauth_all] || service_match
          service.sslAuthentication = !params[:params_sslauth].nil?
        end
      end
    end

    if params[:params_sslauth]
      check_internal_server_certs(client, params[:params_url])
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_services(client))
  end

  def adapter_params
    authorize!(:edit_service_params)

    validate_params({
      :client_id => [:required],
      :params_adapter_id => [:required],
      :params_adapter_timeout => [:required, :timeout],
      :params_adapter_sslauth => [],
      :params_adapter_wsdl_uri => [:required]
    })

    client = get_client(params[:client_id])

    client.wsdl.each do |wsdl|
      next unless get_wsdl_id(wsdl) == params[:params_adapter_id]

      wsdl.url = params[:params_adapter_wsdl_uri]

      wsdl.service.each do |service|
        service.timeout = params[:params_adapter_timeout].to_i
        service.sslAuthentication = !params[:params_adapter_sslauth].nil?
      end

      break
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_services(client))
  end

  def service_acl
    authorize!(:view_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_acl_subjects(client, params[:service_code]))
  end

  ##
  # Returns a sorted list of unique services for which acl can be
  # created for.
  def acl_services
    authorize!(:view_service_acl)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    services = {}
    client.wsdl.each do |wsdl|
      wsdl.service.each do |service|
        services[service.serviceCode] = {
          :service_code => service.serviceCode,
          :title => service.title
        }
      end
    end

    XROADV5_METASERVICES.each do |service_code|
      services[service_code] = {
        :service_code => service_code,
        :title => nil
      }
    end if x55_installed?

    services_sorted = services.values.sort do |x, y|
      x[:service_code] <=> y[:service_code]
    end

    render_json(services_sorted)
  end

  def service_acl_subjects_add
    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => [:required]
    })

    client = get_client(params[:client_id])
    acl = get_acl(client, params[:service_code])

    unless acl
      acl = AclType.new
      acl.serviceCode = params[:service_code]
      client.acl.add(acl)
    end

    now = Date.new

    params[:subject_ids].each do |subject_id|
      authorized_subject = AuthorizedSubjectType.new
      authorized_subject.subjectId = get_cached_subject_id(subject_id)
      authorized_subject.rightsGiven = now

      acl.authorizedSubject.add(authorized_subject)
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_acl_subjects(client, params[:service_code]))
  end

  def service_acl_subjects_remove
    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => []
    })

    client = get_client(params[:client_id])
    acl = get_acl(client, params[:service_code])

    if params[:subject_ids]
      params[:subject_ids].each do |subject_id|
        remove_subject(acl.authorizedSubject,
          get_cached_subject_id(subject_id))
      end
    else
      acl.authorizedSubject.clear
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_acl_subjects(client, params[:service_code]))
  end

  private

  def read_services(client)
    services = []

    client.wsdl.each do |wsdl|
      adapter = wsdl.backend == BACKEND_TYPE_XROADV5
      name = adapter ? t('clients.adapter') : t('clients.wsdl')
      name += " " + t('clients.wsdl_disabled') if wsdl.disabled

      adapter_timeout = nil
      adapter_sslauth = true

      unless wsdl.service.isEmpty || (first = wsdl.service.get(0)).nil?
        adapter_timeout = first.timeout
        adapter_sslauth = first.sslAuthentication.nil? || first.sslAuthentication
      end

      services << {
        :wsdl => true,
        :wsdl_id => get_wsdl_id(wsdl),
        :adapter => adapter,
        :adapter_wsdl_uri => wsdl.url,
        :service_id => nil,
        :name => name,
        :title => nil,
        :url => nil,
        :timeout => (adapter_timeout if adapter),
        :security_category => nil,
        :sslauth => (adapter_sslauth if adapter),
        :publish => wsdl.publish,
        :last_published => format_time(wsdl.publishedDate),
        :last_refreshed => format_time(wsdl.refreshedDate),
        :disabled => wsdl.disabled,
        :disabled_notice => wsdl.disabledNotice
      }

      wsdl.service.each do |service|
        categories = []
        service.requiredSecurityCategory.each do |category|
          categories << category.categoryCode
        end

        services << {
          :wsdl => false,
          :wsdl_id => get_wsdl_id(wsdl),
          :adapter => adapter,
          :service_id => get_service_id(service),
          :name => get_service_id(service),
          :service_code => service.serviceCode,
          :title => service.title,
          :url => (service.url unless adapter),
          :timeout => service.timeout,
          :security_category => categories,
          :sslauth => service.sslAuthentication.nil? || service.sslAuthentication,
          :publish => wsdl.publish,
          :last_published => format_time(wsdl.publishedDate),
          :last_refreshed => format_time(wsdl.refreshedDate),
          :disabled => wsdl.disabled,
          :subjects_count => subjects_count(client, service.serviceCode)
        } unless x55_installed? && XROADV5_METASERVICES.include?(service.serviceCode)
      end
    end

    if x55_installed? && !services.empty?
      services += read_xroadv5_metaservices(client)
    end

    services
  end

  def read_xroadv5_metaservices(client)
    services = []

    services << {
      :wsdl => true,
      :wsdl_id => BACKEND_TYPE_XROADV5_META,
      :meta => true,
      :name => t('clients.adapter_meta'),
      :title => nil,
      :url => nil,
      :timeout => nil,
      :security_category => nil,
      :sslauth => nil,
      :publish => nil,
      :last_published => nil,
      :last_refreshed => nil,
      :disabled => false,
      :disabled_notice => nil
    }

    XROADV5_METASERVICES.each do |service_code|
      services << {
        :wsdl => false,
        :wsdl_id => BACKEND_TYPE_XROADV5_META,
        :meta => true,
        :service_id => service_code,
        :name => service_code,
        :service_code => service_code,
        :title => nil,
        :url => nil,
        :timeout => nil,
        :security_category => nil,
        :sslauth => nil,
        :publish => nil,
        :last_published => nil,
        :last_refreshed => nil,
        :disabled => false,
        :subjects_count => subjects_count(client, service_code)
      }
    end

    services
  end

  def get_wsdl_id(wsdl)
    wsdl.backend == BACKEND_TYPE_XROADV5 ? wsdl.backendURL : wsdl.url
  end

  def get_acl(client, service_code)
    client.acl.each do |acl|
      return acl if acl.serviceCode == service_code
    end

    nil
  end

  def subjects_count(client, service_code)
    client.acl.each do |acl|
      return acl.authorizedSubject.size if acl.serviceCode == service_code
    end

    return 0
  end

  def parse_and_check_services(wsdl_parse_url, wsdl)
    existing_services = {}

    wsdl.client.wsdl.each do |other_wsdl|
      if get_wsdl_id(other_wsdl) == get_wsdl_id(wsdl)
        if wsdl.backend == BACKEND_TYPE_XROADV5
          raise t('clients.adapter_exists')
        else
          raise t('clients.wsdl_exists')
        end
      end

      other_wsdl.service.each do |service|
        next if x55_installed? &&
          XROADV5_METASERVICES.include?(service.serviceCode)

        existing_services[get_service_id(service)] = get_wsdl_id(other_wsdl)
      end
    end

    parsed_services = parse_wsdl(wsdl_parse_url)

    parsed_services.each do |parsed_service|
      next if x55_installed? &&
        XROADV5_METASERVICES.include?(parsed_service.name)

      service_id =
        format_service_id(parsed_service.name, parsed_service.version)

      if existing_services.has_key?(service_id)
        raise t('clients.service_exists', :service => service_id,
                :wsdl => existing_services[service_id])
      end

      service = ServiceType.new
      service.serviceCode = parsed_service.name
      service.serviceVersion = parsed_service.version
      service.title = parsed_service.title
      service.url = parsed_service.url
      service.timeout = 60
      service.wsdl = wsdl

      wsdl.service.add(service)
    end
  end

  def parse_wsdl(url)
    WSDLParser::parseWSDL(url)
  rescue Java::ee.cyber.sdsb.common.CodedException
    logger.error(ExceptionUtils.getStackTrace($!))

    if ExceptionUtils.indexOfThrowable($!,
        Java::java.net.MalformedURLException.java_class) != -1
      raise t("clients.malformed_wsdl_url")
    end

    if ExceptionUtils.indexOfType($!,
        Java::java.io.IOException.java_class) != -1
      raise t("clients.wsdl_download_failed")
    end

    if ExceptionUtils.indexOfThrowable($!,
        Java::org.xml.sax.SAXParseException.java_class) != -1
      raise t("clients.invalid_wsdl")
    end

    raise $!
  end

  def adapter_wsdl_url(backend)
    service_mediator = SystemProperties::getServiceMediatorAddress
    service_mediator_params = {
      :backend => backend
    }.to_param

    "#{service_mediator}/?#{service_mediator_params}"
  end

  def adapter_service_url(client_id)
    service_mediator = SystemProperties::getServiceMediatorAddress
    service_mediator_params = {
      :sdsbInstance => client_id.sdsbInstance,
      :memberClass => client_id.memberClass,
      :memberCode => client_id.memberCode,
      :subsystemCode => client_id.subsystemCode
    }.to_param

    "#{service_mediator}/?#{service_mediator_params}"
  end

  def format_service_id(name, version)
    version ? "#{name}.#{version}" : name
  end

  def get_service_id(service)
    format_service_id(service.serviceCode, service.serviceVersion)
  end

  def clean_acls(client)
    services = Set.new

    client.wsdl.each do |wsdl|
      wsdl.service.each do |service|
        services << service.serviceCode
      end
    end

    deleted_acls = []

    client.acl.each do |acl|
      unless services.include?(acl.serviceCode) ||
          (x55_installed? && XROADV5_METASERVICES.include?(acl.serviceCode))
        deleted_acls << acl
      end
    end

    client.acl.removeAll(deleted_acls)
  end

  def adapter_ssl_auth?(wsdl)
    wsdl.service.isEmpty || (first = wsdl.service.get(0)).nil? ||
      first.sslAuthentication.nil? || first.sslAuthentication
  end

  def check_internal_server_certs(client, url)
    return unless url && url.start_with?("https://")

    begin
      InternalServerTestUtil::testHttpsConnection(client.isCert, url)
    rescue Java::javax.net.ssl.SSLHandshakeException
      error(t("clients.internal_server_ssl_error", { :url => url }))
    rescue
      logger.error("Checking internal server certs failed: #{$!.message}")
    end
  end

  def check_wsdls_mergeability(client)
    return unless x55_installed?

    client_id = client.getIdentifier()
    WSDLCombinationChecker::check(client_id)
  rescue InvalidWSDLCombinationException => e
    error(t(
        "clients.combinedwsdl.invalid_combination",
        :reason => e.getMessage()))
  rescue Java::javax.wsdl.WSDLException
    error(t(
        "clients.combinedwsdl.invalid_single_wsdl",
        :client_id => client_id))
  rescue Java::java.net.ConnectException
    error(t("clients.combinedwsdl.network_error"))
  rescue Exception => e
    error(t("clients.combinedwsdl.other_error", :message => e.getMessage()))
  end
end
