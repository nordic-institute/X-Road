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
require "shellwords"

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.conf.serverconf.model.AccessRightType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.ServiceType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.WsdlType
java_import Java::ee.ria.xroad.common.identifier.SecurityCategoryId
java_import Java::ee.ria.xroad.proxyui.InternalServerTestUtil
java_import Java::ee.ria.xroad.proxyui.WSDLParser

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

  def wsdl_add
    audit_log("Add WSDL", audit_log_data = {})

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
    wsdl.refreshedDate = Date.new
    wsdl.client = client

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:wsdlUrl] = wsdl.url
    audit_log_data[:disabled] = wsdl.disabled
    audit_log_data[:refreshedDate] =
      Time.at(wsdl.refreshedDate.getTime / 1000).iso8601

    parse_and_check_services(wsdl)

    client.wsdl.add(wsdl)

    serverconf_save

    render_json(read_services(client))
  end

  def wsdl_disable
    if params[:enable].nil?
      audit_log("Disable WSDL", audit_log_data = {})
    else
      audit_log("Enable WSDL", audit_log_data = {})
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

    client.wsdl.each do |wsdl|
      next unless params[:wsdl_ids].include?(wsdl.url)

      wsdl.disabled = params[:enable].nil?
      wsdl.disabledNotice = params[:wsdl_disabled_notice] if params[:enable].nil?

      audit_log_data[:wsdlUrls] << wsdl.url
    end


    serverconf_save

    render_json(read_services(client))
  end

  def wsdl_edit
    audit_log("Edit WSDL", audit_log_data = {})

    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_id => [:required],
      :new_url => [:required]
    })

    client = get_client(params[:client_id])
    audit_log_data[:clientIdentifier] = client.identifier

    client.wsdl.each do |wsdl|
      if wsdl.url == params[:new_url]
        raise t('clients.wsdl_exists')
      end
    end if params[:wsdl_id] != params[:new_url]

    wsdls = wsdls_by_urls(client, [params[:wsdl_id]])
    wsdls[0].url = params[:new_url]
    wsdls[0].refreshedDate = Date.new

    added_objs, added, deleted = parse_wsdls(client, wsdls)
    update_wsdls(client, added_objs, deleted)

    serverconf_save

    audit_log_data[:wsdl] = {
      :wsdlUrl => params[:wsdl_id],
      :wsdlUrlNew => params[:new_url],
      :servicesAdded => added[wsdls[0].url],
      :servicesDeleted => deleted[wsdls[0].url]
    }

    render_json(read_services(client))
  end

  def wsdl_refresh
    audit_log("Refresh WSDL", audit_log_data = {})

    authorize!(:refresh_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required]
    })

    client = get_client(params[:client_id])
    audit_log_data[:clientIdentifier] = client.identifier

    wsdls = wsdls_by_urls(client, params[:wsdl_ids])

    added_objs, added, deleted = parse_wsdls(client, wsdls, audit_log_data)
    update_wsdls(client, added_objs, deleted)

    wsdls.each do |wsdl|
      wsdl.refreshedDate = Date.new
    end

    serverconf_save

    render_json(read_services(client))
  end

  def wsdl_delete
    audit_log("Delete WSDL", audit_log_data = {})

    authorize!(:delete_wsdl)

    validate_params({
      :client_id => [:required],
      :wsdl_ids => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:wsdlUrls] = []

    deleted = []
    client.wsdl.each do |wsdl|
      deleted << wsdl if params[:wsdl_ids].include?(wsdl.url)
    end

    deleted.each do |wsdl|
      audit_log_data[:wsdlUrls] << wsdl.url

      clean_acls(client, wsdl)

      wsdl.client = nil
      client.wsdl.remove(wsdl)
      @session.delete(wsdl)
    end

    serverconf_save

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

    client.wsdl.each do |wsdl|
      next unless wsdl.url == params[:params_wsdl_id]

      audit_log_data[:wsdlUrl] = wsdl.url
      audit_log_data[:services] = []

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

    services_sorted = services.values.sort do |x, y|
      x[:service_code] <=> y[:service_code]
    end

    render_json(services_sorted)
  end

  def service_acl_subjects_add
    audit_log("Add access rights to service", audit_log_data = {})

    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:serviceCode] = params[:service_code]
    audit_log_data[:subjectIds] = []

    now = Date.new

    params[:subject_ids].each do |subject_id|
      access_right = AccessRightType.new
      access_right.subjectId = get_cached_subject_id(subject_id)
      access_right.serviceCode = params[:service_code]
      access_right.rightsGiven = now

      audit_log_data[:subjectIds] << access_right.subject_id.toString

      client.acl.add(access_right)
    end

    serverconf_save

    render_json(read_acl_subjects(client, params[:service_code]))
  end

  def service_acl_subjects_remove
    audit_log("Remove access rights from service", audit_log_data = {})

    authorize!(:edit_service_acl)

    validate_params({
      :client_id => [:required],
      :service_code => [:required],
      :subject_ids => []
    })

    client = get_client(params[:client_id])

    subject_ids = []

    params[:subject_ids].each do |subject_id|
      subject_id = get_cached_subject_id(subject_id)
      subject_ids << subject_id
    end if params[:subject_ids]

    removed_access_rights =
      remove_access_rights(client.acl, subject_ids, params[:service_code])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:serviceCode] = params[:service_code]
    audit_log_data[:subjectIds] = removed_access_rights.map do |access_right|
      access_right.subjectId.toString
    end

    serverconf_save

    render_json(read_acl_subjects(client, params[:service_code]))
  end

  private

  def wsdls_by_urls(client, wsdl_urls)
    wsdls = []
    client.wsdl.each do |wsdl|
      wsdls << wsdl if wsdl_urls.include?(wsdl.url)
    end

    if wsdls.length != wsdl_urls.length
      raise "Some WSDLs were not found"
    end

    wsdls
  end

  def read_services(client)
    services = []

    client.wsdl.each do |wsdl|
      name = t('clients.wsdl')
      name += " " + t('clients.wsdl_disabled') if wsdl.disabled

      services << {
        :wsdl => true,
        :wsdl_id => wsdl.url,
        :service_id => nil,
        :name => name,
        :title => nil,
        :url => nil,
        :timeout => nil,
        :security_category => nil,
        :sslauth => nil,
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
          :wsdl_id => wsdl.url,
          :service_id => get_service_id(service),
          :name => get_service_id(service),
          :service_code => service.serviceCode,
          :title => service.title,
          :url => service.url,
          :timeout => service.timeout,
          :security_category => categories,
          :sslauth => service.sslAuthentication.nil? || service.sslAuthentication,
          :last_refreshed => format_time(wsdl.refreshedDate),
          :disabled => wsdl.disabled,
          :subjects_count => subjects_count(client, service.serviceCode)
        }
      end
    end

    services
  end

  def subjects_count(client, service_code)
    i = 0

    client.acl.each do |access_right|
      i = i + 1 if access_right.serviceCode == service_code
    end

    return i
  end

  def parse_wsdls(client, wsdls, audit_log_data = nil)
    # construct a list of existing services mapped to their wsdls
    existing_services = {}
    client.wsdl.each do |wsdl|
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
      add_text =
        t('clients.adding_services', :added => added.values.join(", "))
    end

    unless deleted.values.flatten.empty?
      delete_text =
        t('clients.deleting_services', :deleted => deleted.values.join(", "))
    end

    unless deleted.values.flatten.empty? && added.values.flatten.empty?
      warn("changed_services", "#{add_text}#{delete_text}")
    end

    return added_objs, added, deleted
  end

  def update_wsdls(client, added_objs, deleted)
    deleted_codes = Set.new

    client.wsdl.each do |wsdl|
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
        service.wsdl = nil
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
        service.wsdl = wsdl

        wsdl.service.add(service)
      end if added_objs.has_key?(wsdl.url)
    end

    if deleted_codes.any?
      remove_access_rights(client.acl, nil, deleted_codes)
    end
  end

  def parse_and_check_services(wsdl)
    existing_services = {}

    wsdl.client.wsdl.each do |other_wsdl|
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
        raise t('clients.service_exists', :service => service_id,
                :wsdl => existing_services[service_id])
      end

      service = ServiceType.new
      service.serviceCode = parsed_service.name
      service.serviceVersion = parsed_service.version
      service.title = parsed_service.title
      service.url = parsed_service.url
      service.timeout = DEFAULT_SERVICE_TIMEOUT
      service.wsdl = wsdl

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
        warnings += "#{CGI.escapeHTML(line)}<br/>"
      end

      warn("wsdl_validation_warnings",
        t("clients.wsdl_validation_warnings", {:wsdl => url, :warnings => warnings}))
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
    service_codes = get_service_codes(wsdl)

    # exclude services existing with different version
    client.wsdl.each do |w|

      if w.id != wsdl.id
        service_codes.subtract(get_service_codes(w))
      end
    end

    remove_access_rights(client.acl, nil, service_codes) if !service_codes.empty?
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
end
