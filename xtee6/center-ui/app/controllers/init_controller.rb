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

java_import Java::ee.ria.xroad.commonui.SignerProxy

class InitController < ApplicationController

  skip_before_filter :check_conf, :read_server_id

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

    @instance_identifier = SystemParameter.instance_identifier
    @central_server_address = SystemParameter.central_server_address

    unless software_token_initialized?
      @init_software_token = true
    end
  end

  def init
    authorize!(:init_config)

    required_validators = [:required]

    unless SystemParameter.instance_identifier
      init_instance_identifier = required_validators.clone
    end

    unless SystemParameter.central_server_address
      init_central_server_address = required_validators.clone
      init_central_server_address << :host
    end

    unless software_token_initialized?
      init_software_token = required_validators.clone
    end

    unless init_instance_identifier ||
        init_central_server_address ||
        init_software_token
      raise t('init.already_initialized')
    end

    validate_params({
      :instance_identifier => init_instance_identifier || [],
      :central_server_address => init_central_server_address || [],
      :pin => init_software_token || [],
      :pin_repeat => init_software_token || []
    })

    if init_instance_identifier
      SystemParameter.create!(
        :key => SystemParameter::INSTANCE_IDENTIFIER,
        :value => params[:instance_identifier])
    end

    if init_central_server_address
      SystemParameter.create!(
        :key => SystemParameter::CENTRAL_SERVER_ADDRESS,
        :value => params[:central_server_address])
    end

    init_other_system_parameters

    if init_software_token
      if params[:pin] != params[:pin_repeat]
        raise t('init.mismatching_pins')
      else
        pin = Array.new
        params[:pin].bytes do |b|
          pin << b
        end

        SignerProxy::initSoftwareToken(pin.to_java(:char))
      end
    end

    render_json
  end

  private

  def init_other_system_parameters
    GlobalGroup.find_or_initialize_by_group_code(
      SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP
    ).update_attributes!({
      :description => SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC
    })

    # System parameters must be looked up using the HA-aware functions, in order
    # for the database node name to be taken into account, if applicable.
    SystemParameter.find_or_initialize(
      SystemParameter::AUTH_CERT_REG_URL,
      SystemParameter::DEFAULT_AUTH_CERT_REG_URL)

    SystemParameter.find_or_initialize(
      SystemParameter::CONF_SIGN_ALGO_ID,
      SystemParameter::DEFAULT_CONF_SIGN_ALGO_ID)

    SystemParameter.find_or_initialize(
      SystemParameter::CONF_HASH_ALGO_URI,
      SystemParameter::DEFAULT_CONF_HASH_ALGO_URI)

    SystemParameter.find_or_initialize(
      SystemParameter::CONF_SIGN_CERT_HASH_ALGO_URI,
      SystemParameter::DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI)

    SystemParameter.find_or_initialize(
      SystemParameter::SECURITY_SERVER_OWNERS_GROUP,
      SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP)
  end
end
