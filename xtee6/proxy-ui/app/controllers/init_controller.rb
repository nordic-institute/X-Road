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

require 'net/http'

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor
java_import Java::ee.ria.xroad.common.conf.serverconf.model.ClientType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.ServerConfType
java_import Java::ee.ria.xroad.common.identifier.ClientId
java_import Java::ee.ria.xroad.commonui.SignerProxy
java_import Java::ee.ria.xroad.common.util.TokenPinPolicy

class InitController < ApplicationController

  skip_around_filter :transaction, :only =>
    [:anchor_upload, :anchor_submit, :member_classes, :member_codes, :member_name]

  skip_before_filter :check_conf, :read_server_id, :read_owner_name

  def index
    if request.xhr?
      # come back without ajax
      render_redirect(root_path, "common.initialization_required")
      return
    end

    if cannot?(:init_config)
      raise t('init.not_authorized')
    end

    if initialized?
      raise t('init.already_initialized')
    end

    unless globalconf_initialized?
      @init_anchor = true
    end

    unless serverconf_initialized?
      @init_serverconf = true
    end

    unless software_token_initialized?
      @init_software_token = true
    end

    if serverconf
      if serverconf.owner
        @owner_class = serverconf.owner.identifier.memberClass
        @owner_code = serverconf.owner.identifier.memberCode

        unless @init_anchor
          @owner_name = get_member_name(@owner_class, @owner_code)
        end
      end

      @server_code = serverconf.serverCode
    end
  end

  def anchor_upload
    authorize!(:init_config)

    validate_params({
      :anchor_upload_file => [:required]
    })

    anchor_details =
      save_temp_anchor_file(params[:anchor_upload_file].read)

    render_json(anchor_details)
  end

  def anchor_init
    audit_log("Initialize anchor", audit_log_data = {})

    authorize!(:init_config)

    validate_params

    anchor_details = get_temp_anchor_details

    audit_log_data[:anchorFileHash] = anchor_details[:hash]
    audit_log_data[:anchorFileHashAlgorithm] = anchor_details[:hash_algorithm]
    audit_log_data[:generatedAt] = anchor_details[:generated_at]

    apply_temp_anchor_file

    notice(t('init.configuration_downloaded'))

    render_json
  end

  def serverconf_init
    audit_log("Initialize server configuration", audit_log_data = {})

    authorize!(:init_config)

    required = [:required]

    init_software_token = required unless software_token_initialized?
    init_owner = required unless serverconf && serverconf.owner
    init_server_code = required unless serverconf && serverconf.serverCode

    unless init_software_token || init_owner || init_server_code
      raise t('init.already_initialized')
    end

    validate_params({
      :owner_class => init_owner || [],
      :owner_code => init_owner || [],
      :server_code => init_server_code || [],
      :pin => init_software_token || [],
      :pin_repeat => init_software_token || []
    })

    new_serverconf = serverconf || ServerConfType.new

    if init_owner
      owner_id = ClientId.create(
        xroad_instance,
        params[:owner_class],
        params[:owner_code], nil)

      audit_log_data[:ownerIdentifier] = owner_id

      unless get_member_name(params[:owner_class], params[:owner_code])
        warn_message = t('init.unregistered_member', {
          :member_class => params[:owner_class].upcase,
          :member_code => params[:owner_code]
        })
        warn("unregistered_member", warn_message)
      end

      owner_id = get_identifier(owner_id)

      owner = nil
      new_serverconf.client.each do |client|
        if client.identifier == owner_id
          owner = client
          break
        end
      end

      unless owner
        owner = ClientType.new
        owner.identifier = owner_id
        owner.clientStatus = ClientType::STATUS_SAVED
        owner.isAuthentication = "NOSSL"
        owner.conf = new_serverconf

        new_serverconf.client.add(owner)
      end

      new_serverconf.owner = owner
    end

    if init_server_code
      new_serverconf.serverCode = params[:server_code]
      audit_log_data[:serverCode] = new_serverconf.serverCode
    end

    if init_software_token
      if params[:pin] != params[:pin_repeat]
        raise t('init.mismatching_pins')
      else
        pin = Array.new
        params[:pin].bytes do |b|
          pin << b
        end

        if SystemProperties::should_enforce_token_pin_policy
          description = TokenPinPolicy::describe(pin.to_java(:char))
          if !description.valid?

            if description.has_invalid_characters
              raise  t('init.pin_not_ascii')
            end

            raise t('init.pin_weak', {
                :min_length => description.min_length,
                :length => description.length,
                :min_character_class_count => description.min_character_class_count,
                :character_class_count => description.character_classes.size
            })
          end
        end

        SignerProxy::initSoftwareToken(pin.to_java(:char))
      end
    end

    serverconf_save(new_serverconf)

    after_commit do
      if x55_installed?
        import_v5_services
        import_v5_internal_tls_key
      end
    end

    render_json
  end

  def member_classes
    authorize!(:init_config)

    classes = []

    if globalconf_initialized?
      GlobalConf::getMemberClasses(xroad_instance).each do |memberClass|
        classes << memberClass
      end
    end

    render_json(classes)
  end

  def member_codes
    authorize!(:init_config)

    validate_params({
      :member_class => []
    })

    codes = Set.new

    if globalconf_initialized?
      GlobalConf::getMembers(xroad_instance).each do |member|
        unless params[:member_class] &&
            params[:member_class] != member.id.memberClass
          codes << member.id.memberCode
        end
      end
    end

    render_json(codes)
  end

  def member_name
    authorize!(:init_config)

    validate_params({
      :owner_class => [],
      :owner_code => []
    })

    name = get_member_name(params[:owner_class], params[:owner_code])

    render_json(:name => name)
  end

  private

  def import_v5_services
    if importer = SystemProperties::getServiceImporterCommand
      logger.info("Importing services from 5.0 to X-Road")

      output = %x["#{importer}" 2>&1]

      if $?.exitstatus != 0
        logger.error(output)
        error(t('init.services_import_failed'))
      end
    else
      logger.warn("Service importer unspecified, skipping import")
    end
  end

  def import_v5_internal_tls_key
      if importer = SystemProperties::getInternalTlsKeyImporterCommand
        logger.info("Importing internal TLS key from 5.0 to X-Road")

        output = %x["#{importer}" 2>&1]

        if $?.exitstatus != 0
          logger.error(output)
          error(t('init.internal_tls_key_import_failed'))
        end
      else
        logger.warn("Internal TSL key importer unspecified, skipping import")
      end
    end
end
