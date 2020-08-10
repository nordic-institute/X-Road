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
require "shellwords"

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.conf.serverconf.model.AccessRightType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.EndpointType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.ServiceType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.DescriptionType
java_import Java::ee.ria.xroad.common.identifier.SecurityCategoryId
java_import Java::ee.ria.xroad.proxyui.InternalServerTestUtil
java_import Java::ee.ria.xroad.proxyui.WSDLParser
java_import Java::ee.ria.xroad.proxyui.OpenApiParser

module Clients::Services

  DEFAULT_SERVICE_TIMEOUT = 60

  def client_services
    authorize!(:view_client_services)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_services(client))
  end

  def servicedescription_add
    service_type = DescriptionType.value_of(params[:service_type])
    if DescriptionType::REST == service_type ||
      DescriptionType::OPENAPI3 == service_type
      servicedescription_openapi3_add(params)
    elsif DescriptionType::WSDL == service_type
      servicedescription_wsdl_add(params)
    end
  end

  def servicedescription_disable
    if params[:enable].nil?
      audit_log("Disable service description", audit_log_data = {})
    else
      audit_log("Enable service description", audit_log_data = {})
    end

    authorize!(:enable_disable_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required],
      :wsdl_disabled_notice => [],
      :enable => []
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    if params[:enable].nil?
      audit_log_data[:disabledNotice] = params[:wsdl_disabled_notice]
    end

    audit_log_data[:wsdlUrls] = []

    client.serviceDescription.each do |servicedescription|
      next unless params[:wsdl_ids].include?(servicedescription.url)

      servicedescription.disabled = params[:enable].nil?
      servicedescription.disabledNotice = params[:wsdl_disabled_notice] if params[:enable].nil?

      audit_log_data[:wsdlUrls] << servicedescription.url
    end


    serverconf_save

    render_json(read_services(client))
  end

  def servicedescription_edit
    service_type = DescriptionType.value_of(params[:service_type])
    if DescriptionType::REST == service_type ||
      DescriptionType::OPENAPI3 == service_type
      servicedescription_openapi3_edit(params)
    elsif DescriptionType::WSDL == service_type
      servicedescription_wsdl_edit(params)
    end
  end

  ## Add rest endpoint
  def openapi3_endpoint_add
    audit_log("Add rest endpoint", audit_log_data = {})
    authorize!(:add_openapi3_endpoint)

    validate_params({
                      :endpoint_method => [:required],
                      :endpoint_path => [:required],
                      :client_id => [:required],
                      :service_code => [:required]
                    })

    client = get_client(params[:client_id])

    create_endpoint(client.endpoint, params[:service_code], params[:endpoint_method], params[:endpoint_path], false)

    serverconf_save
    render_json(read_services(client))
  end

  ## Edit endpoint
  def openapi3_endpoint_edit

    audit_log("Edit rest endpoint", audit_log_data = {})

    authorize!(:edit_openapi3_endpoint)

    validate_params({
                      :client_id => [:required],
                      :service_code => [:required],
                      :old_endpoint_method => [:required],
                      :old_endpoint_path => [:required],
                      :endpoint_method => [:required],
                      :endpoint_path => [:required]
                    })
    client = get_client(params[:client_id])

    client.endpoint.each do |ep|
      if ep.service_code == params[:service_code] && ep.method == params[:old_endpoint_method] && ep.path == params[:old_endpoint_path]
        ep.method = params[:endpoint_method]
        ep.path = params[:endpoint_path]
      end
    end

    serverconf_save
    render_json(read_services(client))
  end

  def servicedescription_refresh
    audit_log("Refresh service description", audit_log_data = {})

    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required]
    })

    client = get_client(params[:client_id])
    audit_log_data[:clientIdentifier] = client.identifier
    servicedescriptions = servicedescriptions_by_urls(client, params[:wsdl_ids])

    openapi3_descriptions = servicedescriptions.select { |item| item.type == DescriptionType::OPENAPI3 }
    wsdl_descriptions = servicedescriptions.select  { |item| item.type == DescriptionType::WSDL }

    # Refresh WSDL servicedescriptions
    added_objs, added, deleted = parse_servicedescriptions(client, wsdl_descriptions, audit_log_data)
    update_servicedescriptions(client, added_objs, deleted)

    servicedescriptions.each do |servicedescription|
      servicedescription.refreshedDate = Date.new
    end

    # Refresh OPENAPI3 servicedescriptions
    openapi3_descriptions.each do |item|

      openapi = parse_openapi(item.url)
      base_url = openapi.base_url
      service_code = item.service.first.serviceCode
      item.service.first.url = base_url

      check_duplicate_url(item)
      check_duplicate_service_codes(item)

      endpoints = []
      endpoints << create_endpoint(client.endpoint, service_code)
      if openapi
        openapi.operations.each do |oper|
          endpoints << create_endpoint(client.endpoint, service_code, oper.method, oper.path, true)
        end
        iterate(client.acl) do |item, it|
          it.remove if item.endpoint.generated? && item.endpoint.service_code == service_code &&
            !endpoints.include?(item.endpoint)
        end

        iterate(client.endpoint) do |item, it|
          it.remove if item.generated? && item.service_code == service_code && !endpoints.include?(item)
        end
      end

    end

    serverconf_save
    render_json(read_services(client))
  end

  def servicedescription_delete
    audit_log("Delete service description", audit_log_data = {})

    authorize!(:delete_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:wsdlUrls] = []

    deleted = []
    client.serviceDescription.each do |servicedescription|
      deleted << servicedescription if params[:wsdl_ids].include?(servicedescription.url)
    end

    deleted.each do |servicedescription|
      audit_log_data[:wsdlUrls] << servicedescription.url

      clean_acls(client, servicedescription)

      servicedescription.client = nil
      client.serviceDescription.remove(servicedescription)
      @session.delete(servicedescription)
    end

    serverconf_save

    render_json(read_services(client))
  end

  def endpoint_delete
    audit_log("Delete rest endpoint", audit_log_data = {})

    authorize!(:delete_endpoint)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :method => [:required],
      :path => [:required]
    })

    client = get_client(params[:client_id])
    endpoint = find_endpoint(client.endpoint, params[:service_code], params[:method], params[:path])

    delete_endpoint(client, endpoint)

    render_json(read_services(client))
  end

  def service_params
    audit_log("Edit service parameters", audit_log_data = {})

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

    audit_log_data[:clientIdentifier] = client.identifier

    client.serviceDescription.each do |servicedescription|
      next unless servicedescription.url == params[:params_wsdl_id]

      audit_log_data[:wsdlUrl] = servicedescription.url
      audit_log_data[:services] = []

      servicedescription.service.each do |service|
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
            category_id = SecurityCategoryId.create(xroad_instance, category)
            service.requiredSecurityCategory.add(category_id)
          end if params[:params_security_category]
        end

        if (params[:params_sslauth_all] || service_match) &&
          service.url.start_with?("https")
          service.sslAuthentication = !params[:params_sslauth].nil?
        end

        if params[:params_url_all] || params[:params_timeout_all] ||
          params[:params_sslauth_all] || service_match
          audit_log_data[:services] << {
            :id => get_service_id(service),
            :url => service.url,
            :timeout => service.timeout,
            :tlsAuth => service.sslAuthentication
          }
        end
      end
    end

    if params[:params_sslauth]
      check_internal_server_certs(client, params[:params_url])
    end

    serverconf_save

    render_json(read_services(client))
  end

  ##
  # Returns acls for a service or an endpoint
  #
  def service_acl
    authorize!(:view_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :method => [:required],
      :path => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_acl_subjects(client, params[:service_code], params[:method], params[:path]))
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
    client.serviceDescription.each do |servicedescription|
      servicedescription.service.each do |service|
        services[service.serviceCode] = {
          :service_code => service.serviceCode,
          :title => service.title,
          :service_description_type => DescriptionType::WSDL == servicedescription.type ? 'WSDL' : DescriptionType::REST == servicedescription.type ? 'REST' : 'OPENAPI3'
        }
      end
    end

    services_sorted = services.values.sort do |x, y|
      x[:service_code] <=> y[:service_code]
    end

    render_json(services_sorted)
  end

  ##
  # Returns endpoints for the service if its servicedescriptiontype is REST or OPENAPI3
  #
  def acl_service_endpoints
    authorize!(:view_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required]
    })

    client = get_client(params[:client_id])

    endpoints = []

    client.endpoint.each do |ep|
      if ep.service_code == params[:service_code]
        endpoints << {
          :method => ep.method,
          :path => ep.path
        }
      end
    end

    render_json(endpoints)
  end

  def service_acl_subjects_add
    audit_log("Add access rights to service", audit_log_data = {})

    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => [:required],
      :method => [],
      :path => []
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:serviceCode] = params[:service_code]
    audit_log_data[:subjectIds] = []

    now = Date.new
    if params[:method] && params[:path]
      endpoint = create_endpoint(client.endpoint, params[:service_code], params[:method], params[:path])
    else
      endpoint = create_endpoint(client.endpoint, params[:service_code])
    end

    params[:subject_ids].each do |subject_id|
      if !contains_subject(client.acl, subject_id, endpoint)
        access_right = AccessRightType.new
        access_right.subjectId = get_cached_subject_id(subject_id)
        access_right.endpoint = endpoint
        access_right.rightsGiven = now

        audit_log_data[:subjectIds] << access_right.subject_id.toString

        client.acl.add(access_right)
      end
    end

    serverconf_save

    render_json(read_acl_subjects(client, params[:service_code], params[:method], params[:path]))
  end

  def service_acl_subjects_remove
    audit_log("Remove access rights from service", audit_log_data = {})

    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => [],
      :method => [],
      :path => []
    })

    client = get_client(params[:client_id])

    subject_ids = []

    params[:subject_ids].each do |subject_id|
      subject_id = get_cached_subject_id(subject_id)
      subject_ids << subject_id
    end if params[:subject_ids]

    removed_access_rights =
      remove_access_rights(client.acl, subject_ids, params[:service_code], params[:method], params[:path])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:serviceCode] = params[:service_code]
    audit_log_data[:subjectIds] = removed_access_rights.map do |access_right|
      access_right.subjectId.toString
    end

    serverconf_save

    render_json(read_acl_subjects(client, params[:service_code], params[:method], params[:path]))
  end

  private

  def servicedescription_wsdl_add(params)
    audit_log("Add service description", audit_log_data = {})

    authorize!(:add_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_add_url => [:required, :url],
      :service_type => [:required]
    })

    client = get_client(params[:client_id])

    servicedescription = ServiceDescriptionType.new
    servicedescription.url = params[:wsdl_add_url]
    servicedescription.disabled = true
    servicedescription.disabledNotice = t('clients.default_disabled_service_notice')
    servicedescription.refreshedDate = Date.new
    servicedescription.client = client
    servicedescription.type = DescriptionType::WSDL

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:wsdlUrl] = servicedescription.url
    audit_log_data[:disabled] = servicedescription.disabled?
    audit_log_data[:refreshedDate] =
      Time.at(servicedescription.refreshedDate.getTime / 1000).iso8601

    parse_and_check_services(servicedescription)

    client.serviceDescription.add(servicedescription)

    servicedescription.service.each { |service|
      create_endpoint(client.endpoint, service.service_code)
    }
    serverconf_save

    render_json(read_services(client))
  end

  def servicedescription_openapi3_add(params)
    audit_log("Add service description", audit_log_data = {})

    authorize!(:add_openapi3)
    validate_params({
      :client_id => [:required],
      :openapi3_add_url => [:required, :url],
      :openapi3_service_code => [:required],
      :service_type => [:required]
    })

    url = params[:openapi3_add_url]
    base_url = url
    client = get_client(params[:client_id])
    openapi = nil

    if DescriptionType::OPENAPI3.name == params[:service_type]
      openapi = parse_openapi(url)
      base_url = openapi.base_url
    end

    servicedescription = ServiceDescriptionType.new
    servicedescription.url = url
    servicedescription.disabled = true
    servicedescription.disabledNotice = t('clients.default_disabled_service_notice')
    servicedescription.refreshedDate = Date.new
    servicedescription.client = client
    servicedescription.type = openapi ? DescriptionType::OPENAPI3 : DescriptionType::REST

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:wsdlUrl] = servicedescription.url
    audit_log_data[:disabled] = servicedescription.disabled?
    audit_log_data[:refreshedDate] = Time.at(servicedescription.refreshedDate.getTime / 1000).iso8601

    service = ServiceType.new
    service.serviceCode = params[:openapi3_service_code]
    service.serviceVersion = nil
    service.title = nil
    service.url = base_url
    service.timeout = DEFAULT_SERVICE_TIMEOUT
    service.serviceDescription = servicedescription
    servicedescription.service.add(service)

    check_duplicate_url(servicedescription)
    check_duplicate_service_codes(servicedescription)

    client.serviceDescription.add(servicedescription)
    create_endpoint(client.endpoint, service.service_code)
    openapi.operations.each do |oper|
      create_endpoint(client.endpoint, service.service_code, oper.method, oper.path, true)
    end if openapi

    serverconf_save
    render_json(read_services(client))
  end

  def servicedescription_openapi3_edit(params)
    audit_log("Edit openapi3 service description", audit_log_data = {})

    authorize!(:refresh_openapi3)

    validate_params({
      :client_id => [:required],
      :wsdl_id => [:required],
      :openapi3_old_service_code => [:required],
      :openapi3_new_url => [:required, :url],
      :openapi3_new_service_code => [:required],
      :service_type => [:required]
    })

    client = get_client(params[:client_id])
    service_type = DescriptionType.value_of(params[:service_type])

    audit_log_data[:clientIdentifier] = client.identifier

    servicedescription = client.serviceDescription.detect { |servicedescription|
      [DescriptionType::REST, DescriptionType::OPENAPI3].include?(servicedescription.type) &&
        servicedescription.url == params[:wsdl_id]
    } or raise t("clients.service_description_does_not_exist")

    base_url = params[:openapi3_new_url]
    openapi = nil

    if DescriptionType::OPENAPI3 == service_type
      if params[:openapi3_new_url] != servicedescription.url
        openapi = parse_openapi(base_url)
        base_url = openapi.base_url
      else
        base_url = servicedescription.service.first.url
      end
    end

    service_code = params[:openapi3_new_service_code]
    servicedescription.url = params[:openapi3_new_url]
    servicedescription.type = service_type
    servicedescription.service.first.url = base_url
    servicedescription.service.first.service_code = service_code

    check_duplicate_url(servicedescription)
    check_duplicate_service_codes(servicedescription)

    client.endpoint.each do |item|
      if params[:openapi3_old_service_code] == item.serviceCode
        item.serviceCode = service_code
      end
    end

    endpoints = []
    endpoints << create_endpoint(client.endpoint, service_code)
    if openapi
      openapi.operations.each do |oper|
        endpoints << create_endpoint(client.endpoint, service_code, oper.method, oper.path, true)
      end
      iterate(client.acl) do |item, it|
        it.remove if item.endpoint.generated? && item.endpoint.service_code == service_code &&
          !endpoints.include?(item.endpoint)
      end

      iterate(client.endpoint) do |item, it|
        it.remove if item.generated? && item.service_code == service_code && !endpoints.include?(item)
      end
    end
    serverconf_save
    render_json(read_services(client))
  end

  def servicedescription_wsdl_edit(params)
    audit_log("Edit wsdl service description", audit_log_data = {})

    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_id => [:required],
      :new_url => [:required, :url],
      :service_type => [:required]
    })
    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    client.serviceDescription.each do |servicedescription|
      if servicedescription.url == params[:new_url]
        raise t('clients.wsdl_exists')
      end
    end if params[:wsdl_id] != params[:new_url]

    servicedescriptions = servicedescriptions_by_urls(client, [params[:wsdl_id]])
    servicedescriptions[0].url = params[:new_url]
    servicedescriptions[0].refreshedDate = Date.new

    added_objs, added, deleted = parse_servicedescriptions(client, servicedescriptions)

    update_servicedescriptions(client, added_objs, deleted)

    serverconf_save

    audit_log_data[:wsdl] = {
      :wsdlUrl => params[:wsdl_id],
      :wsdlUrlNew => params[:new_url],
      :servicesAdded => added[servicedescriptions[0].url],
      :servicesDeleted => deleted[servicedescriptions[0].url]
    }

    render_json(read_services(client))
  end

  def delete_endpoint(client, endpoint)
    acl_to_be_removed = []
    client.acl.each do |access_right|
      if  access_right.endpoint.serviceCode == params[:service_code] && access_right.endpoint.method == params[:method] && access_right.endpoint.path == params[:path]
        acl_to_be_removed << access_right
      end
    end
    client.acl.removeAll(acl_to_be_removed)
    client.endpoint.remove(endpoint)
  end

  def find_endpoint(endpoints, service_code, method, path)
    endpoints.detect do |endpoint|
      endpoint.service_code == service_code &&
        endpoint.method == method &&
        endpoint.path == path
    end
  end

  def servicedescriptions_by_urls(client, servicedescription_urls)
    servicedescriptions = []
    client.serviceDescription.each do |servicedescription|
      servicedescriptions << servicedescription if servicedescription_urls.include?(servicedescription.url)
    end

    if servicedescriptions.length != servicedescription_urls.length
      raise "Some service descriptions were not found"
    end

    servicedescriptions
  end

  ## helper function to iterate Java collections
  def iterate(iterable)
    it = iterable.iterator
    while it.has_next?
      yield(it.next, it)
    end
  end

  # rubocop:disable all
  def read_services(client)
    services = []

    client.serviceDescription.each do |servicedescription|
      name = DescriptionType::WSDL == servicedescription.type ? t('clients.wsdl') : t('clients.openapi3')
      name += " " + t('clients.wsdl_disabled') if servicedescription.disabled

      services << {
        :wsdl => true,
        :wsdl_id => servicedescription.url,
        :service_id => nil,
        :name => name,
        :title => nil,
        :url => nil,
        :timeout => nil,
        :security_category => nil,
        :sslauth => nil,
        :last_refreshed => format_time(servicedescription.refreshedDate),
        :disabled => servicedescription.disabled,
        :disabled_notice => servicedescription.disabledNotice,
        :service_type => servicedescription.type.name,
        :openapi3_service_code => DescriptionType::REST == servicedescription.type || DescriptionType::OPENAPI3 == servicedescription.type ? servicedescription.service.first.serviceCode : nil
      }

      servicedescription.service.each do |service|
        categories = []
        service.requiredSecurityCategory.each do |category|
          categories << category.categoryCode
        end

        serviceObject = {
          :wsdl => false,
          :wsdl_id => servicedescription.url,
          :service_id => get_service_id(service),
          :name => get_service_id(service),
          :service_code => service.serviceCode,
          :title => service.title,
          :url => service.url,
          :timeout => service.timeout,
          :security_category => categories,
          :sslauth => service.sslAuthentication.nil? || service.sslAuthentication,
          :last_refreshed => format_time(servicedescription.refreshedDate),
          :disabled => servicedescription.disabled
        }

        if DescriptionType::OPENAPI3 == servicedescription.type || DescriptionType::REST == servicedescription.type
          unsortedEndpoints = []
          client.endpoint.each do |endpoint|
            if endpoint.service_code == service.service_code
              if endpoint.method == '*' && endpoint.path == '**'
                serviceObject[:subjects_count] = subjects_count_by_endpoint(client, endpoint)
                serviceObject[:endpoint] = true
                services << serviceObject
              else
                unsortedEndpoints << {
                  :wsdl => false,
                  :wsdl_id => servicedescription.url,
                  :service_code => endpoint.service_code,
                  :method => endpoint.method,
                  :path => endpoint.path,
                  :generated => endpoint.generated,
                  :name => get_service_id(service),
                  :title => service.title,
                  :url => service.url,
                  :timeout => service.timeout,
                  :security_category => categories,
                  :sslauth => service.sslAuthentication.nil? || service.sslAuthentication,
                  :last_refreshed => format_time(servicedescription.refreshedDate),
                  :disabled => servicedescription.disabled,
                  :subjects_count => subjects_count_by_endpoint(client, endpoint),
                  :endpoint => true
                }
              end
            end
          end

          sortedEndpointServices = unsortedEndpoints.sort {|a,b| (a[:generated] == b[:generated]) ? ((a[:path] < b[:path]) ? -1 : 1) : (a[:generated] ? -1 : 1)}
          services = services + sortedEndpointServices
        else
          serviceObject[:subjects_count] = subjects_count_by_service_code(client, service.serviceCode)
          services << serviceObject
        end
      end
    end
    services
  end
  # rubocop:enable all

  def subjects_count_by_endpoint(client, endpoint)
    i = 0

    client.acl.each do |access_right|
      i = i + 1 if access_right.endpoint.serviceCode == endpoint.serviceCode && access_right.endpoint.method == endpoint.method && access_right.endpoint.path == endpoint.path
    end

    return i
  end

  def subjects_count_by_service_code(client, service_code)
    i = 0

    client.acl.each do |access_right|
      i = i + 1 if access_right.endpoint.serviceCode == service_code
    end

    return i
  end

  def parse_servicedescriptions(client, wsdls, audit_log_data = nil)
    # construct a list of existing services mapped to their wsdls
    existing_services = {}
    client.serviceDescription.each do |wsdl|
      wsdl.service.each do |service|
        existing_services[get_service_id(service)] = wsdl.url
      end
    end

    audit_log_data[:wsdls] = [] if audit_log_data

    added = {}
    added_objs = {}
    deleted = {}

    wsdls.each do |wsdl|
      services_parsed = parse_wsdl(wsdl)

      services_before = []
      services_after = []

      wsdl.service.each do |service|
        services_before << get_service_id(service)
      end

      added_objs[wsdl.url] = []

      services_parsed.each do |service_parsed|
        service_parsed_id =
          format_service_id(service_parsed.name, service_parsed.version)

        services_after << service_parsed_id

        unless services_before.include?(service_parsed_id)
          if existing_services.has_key?(service_parsed_id)
            raise t('clients.service_exists_refresh',
              :service => service_parsed_id,
              :wsdl1 => wsdl.url,
              :wsdl2 => existing_services[service_parsed_id])
          end

          added_objs[wsdl.url] << service_parsed
        end
      end

      added[wsdl.url] = services_after - services_before
      deleted[wsdl.url] = services_before - services_after

      audit_log_data[:wsdls] << {
        :wsdlUrl => wsdl.url,
        :servicesAdded => added[wsdl.url],
        :servicesDeleted => deleted[wsdl.url]
      } if audit_log_data
    end

    unless added.values.flatten.empty?
      add_text = t('clients.adding_services', :added => Encode.forHtml(added.values.join(", ")))
    end

    unless deleted.values.flatten.empty?
      delete_text = t('clients.deleting_services', :deleted => Encode.forHtml(deleted.values.join(", ")))
    end

    unless deleted.values.flatten.empty? && added.values.flatten.empty?
      warn("changed_services", "#{add_text}#{delete_text}")
    end

    return added_objs, added, deleted
  end

  def update_servicedescriptions(client, added_objs, deleted)
    deleted_codes = Set.new

    client.serviceDescription.each do |wsdl|
      services_deleted = []

      deleted[wsdl.url].each do |service_id|
        wsdl.service.each do |service|
          if get_service_id(service) == service_id
            services_deleted << service
            deleted_codes << service.serviceCode
          end
        end
      end if deleted.has_key?(wsdl.url)

      services_deleted.each do |service|
        service.serviceDescription = nil
        wsdl.service.remove(service)
        @session.delete(service)
      end

      added_objs[wsdl.url].each do |service_parsed|
        service = ServiceType.new
        service.serviceCode = service_parsed.name
        service.serviceVersion = service_parsed.version
        service.title = service_parsed.title
        service.url = service_parsed.url
        service.timeout = DEFAULT_SERVICE_TIMEOUT
        service.serviceDescription = wsdl

        wsdl.service.add(service)
        create_endpoint(client.endpoint, service.service_code)

      end if added_objs.has_key?(wsdl.url)
    end

    clean_service_acls(client, deleted_codes, nil)
  end

  def check_duplicate_url(reviewedService)
    reviewedService.client.serviceDescription.each do |other_servicedescription|
      next if reviewedService.equal? other_servicedescription

      if reviewedService.url == other_servicedescription.url
        raise t('clients.url_exists')
      end
    end
  end

  def check_duplicate_service_codes(reviewedService)
    reviewedService.client.serviceDescription.each do |other_service|
      next if reviewedService.equal? other_service

      reviewedService.service.each do |reviewedItem|
        other_service.service.each do |otherItem|
          if reviewedItem.service_code == otherItem.service_code &&
            reviewedItem.service_version == otherItem.service_version
            raise t('clients.service_exists', :service => otherItem.service_code, :wsdl => other_service.url)
          end
        end
      end
    end
  end

  def parse_and_check_services(wsdl)
    existing_services = {}

    wsdl.client.serviceDescription.each do |other_wsdl|
      if other_wsdl.url == wsdl.url
        raise t('clients.wsdl_exists')
      end

      other_wsdl.service.each do |service|
        existing_services[get_service_id(service)] = other_wsdl.url
      end
    end

    parsed_services = parse_wsdl(wsdl)

    parsed_services.each do |parsed_service|
      service_id =
        format_service_id(parsed_service.name, parsed_service.version)

      if existing_services.has_key?(service_id)
        raise t('clients.service_exists', :service => service_id, :wsdl => existing_services[service_id])
      end

      service = ServiceType.new
      service.serviceCode = parsed_service.name
      service.serviceVersion = parsed_service.version
      service.title = parsed_service.title
      service.url = parsed_service.url
      service.timeout = DEFAULT_SERVICE_TIMEOUT
      service.serviceDescription = wsdl

      wsdl.service.add(service)
    end
  end

  def run_wsdl_validator(url)
    unless SystemProperties::getWsdlValidatorCommand
      logger.warn("Skipping WSDL validator, command not set")
      return
    end

    command = [
      "#{SystemProperties::getWsdlValidatorCommand} #{Shellwords.escape(url)} 2>&1 >/dev/null"
    ]

    logger.info("Running WSDL validator: #{command}")
    output = CommonUi::ScriptUtils.run_script(command, false)
    exitstatus = $?.exitstatus

    logger.debug(" --- Console output - START --- ")
    output.each { |line| logger.debug(line) }
    logger.debug(" --- Console output - END --- ")

    logger.info("WSDL validator finished with exit status '#{exitstatus}'")

    if exitstatus == 127
      raise t("clients.wsdl_validator_not_found")
    elsif exitstatus == 126
      raise t("clients.wsdl_validator_not_executable")
    elsif exitstatus != 0
      raise BaseController::ExceptionWithOutput.new(
        t("clients.wsdl_validation_failed", :wsdl => url), output)
    elsif output.size > 0
      warnings = ""
      output.each do |line|
        warnings += "#{Encode.forHtml(line)}<br/>"
      end

      warn("wsdl_validation_warnings",
        t("clients.wsdl_validation_warnings", { :wsdl => Encode.forHtml(url), :warnings => warnings }))
    end
  end

  def parse_wsdl(wsdl)
    # Run WSDLParser before validator to catch various IO errors
    logger.info("running WSDL parser")
    services = WSDLParser::parseWSDL(wsdl.url)
    run_wsdl_validator(wsdl.url)
    services
  rescue Java::ee.ria.xroad.common.CodedException
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

  def format_service_id(name, version)
    version ? "#{name}.#{version}" : name
  end

  def get_service_id(service)
    format_service_id(service.serviceCode, service.serviceVersion)
  end

  def get_service_codes(wsdl)
    service_codes = Set.new

    wsdl.service.each do |service|
      service_codes << service.serviceCode
    end

    service_codes
  end

  def clean_acls(client, wsdl)
    clean_service_acls(client, get_service_codes(wsdl), wsdl.id)
  end

  def clean_service_acls(client, service_codes, wsdl_id)
    return if service_codes.empty?

    # Exclude services existing with different version.
    client.serviceDescription.each do |w|
      if w.id != wsdl_id
        service_codes.subtract(get_service_codes(w))

        break if service_codes.empty?
      end
    end

    unless service_codes.empty?
      remove_access_rights(client.acl, nil, service_codes)
      remove_endpoints(client.endpoint, service_codes)
    end
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

  def parse_openapi(url)
    begin
      result = OpenApiParser.new(url).parse
      if result.warnings.size > 0
        warnings = ""
        result.warnings.each do |line|
          warnings += "#{Encode.forHtml(line)}<br/>"
        end

        warn("openapi_validation_warnings", t("clients.openapi_validation_warnings",
          { :url => Encode.forHtml(url), :warnings => warnings }))
      end
      result
    rescue OpenApiParser::ParsingException
      raise $!.message
    end
  end

end
