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

require "fileutils"

require "common-ui/io_utils"
require "common-ui/cert_utils"
require "common-ui/script_utils"
require "common-ui/backup_utils"
require "common-ui/validation_utils"

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.commonui.SignerProxy
java_import Java::ee.ria.xroad.signer.protocol.dto.TokenStatusInfo

java_import Java::org.apache.commons.lang3.exception.ExceptionUtils

class BaseController < ActionController::Base

  UI_SKIN_FILE = "ui-skin.css";

  include CommonUi::UserUtils
  include CommonUi::ValidationUtils

  include Base::TransactionCallbacks
  include Base::AuditLog

  protect_from_forgery

  class Warning < StandardError
    def initialize(code, text)
      @code = code
      @text = text
    end

    attr_reader :code, :text
  end

  rescue_from Exception, :with => :render_error
  rescue_from Warning, :with => :render_warning
  rescue_from ValidationError, :with => :render_validation_error

  before_filter :check_restore

  around_filter :catch_java_exceptions
  around_filter :translate_coded_exception

  before_filter :strip_params

  helper_method :format_time

  def index
  end

  def skin
    send_file(SystemProperties.getConfPath + UI_SKIN_FILE)
  end

  def menu
    render :partial => "layouts/menu"
  end

  def activate_token
    audit_log("Log in to token", audit_log_data = {})

    authorize!(:activate_token)

    validate_params({
      :token_id => [:required],
      :pin => [:required]
    })

    pin = Array.new
    params[:pin].bytes do |b|
      pin << b
    end

    token = SignerProxy::getToken(params[:token_id])

    audit_log_data[:tokenId] = token.id
    audit_log_data[:tokenSerialNumber] = token.serialNumber
    audit_log_data[:tokenFriendlyName] = token.friendlyName

    if token.status == TokenStatusInfo::USER_PIN_LOCKED
      raise t("activate_token.pin_locked")
    end

    token.tokenInfo.each do |key, val|
      if (key == "Min PIN length" && pin.size < val.to_i) ||
          (key == "Max PIN length" && pin.size > val.to_i)
        raise t("activate_token.pin_format_incorrect")
      end
    end

    begin
      translate_coded_exception do
        SignerProxy::activateToken(params[:token_id], pin.to_java(:char))
      end
    rescue
      token = SignerProxy::getToken(params[:token_id])

      if token.status == TokenStatusInfo::USER_PIN_FINAL_TRY
        raise "#{$!.message}, #{t('activate_token.final_try')}"
      elsif token.status == TokenStatusInfo::USER_PIN_LOCKED
        raise "#{$!.message}. #{t('activate_token.pin_locked')}."
      end

      raise $!
    end

    render_json
  end

  def deactivate_token
    audit_log("Log out from token", audit_log_data = {})

    authorize!(:deactivate_token)

    validate_params({
      :token_id => [:required]
    })

    token = SignerProxy::getToken(params[:token_id])

    audit_log_data[:tokenId] = token.id
    audit_log_data[:tokenSerialNumber] = token.serialNumber
    audit_log_data[:tokenFriendlyName] = token.friendlyName

    SignerProxy::deactivateToken(params[:token_id])

    render_json
  end

  private

  def verify_get
    return if request.get?

    raise "Expected HTTP method 'GET', but was: '#{request.method}'"
  end

  def verify_post
    return if request.post?

    raise "Expected HTTP method 'POST', but was: '#{request.method}'"
  end

  def software_token_initialized?
    SignerProxy::getTokens.each do |token|
      if token.id == SignerProxy::SSL_TOKEN_ID
        return token.status != TokenStatusInfo::NOT_INITIALIZED
      end
    end

    false
  rescue Java::java.lang.Exception
    logger.warn("Failed to check software token status: #{$!.message}")
    logger.warn(ExceptionUtils.getStackTrace($!))

    return true
  end

  def check_restore
    if CommonUi::BackupUtils.restore_in_progress?
      logger.info("restore in progress, logging out user")

      reset_session

      url = url_for(:controller => :login, :params => {
        :restore => true
      })

      if request.xhr?
        render_redirect(url, "common.restore_in_progress")
      else
        redirect_to(url)
      end
    end
  end

  def catch_java_exceptions
    yield
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def translate_coded_exception
    yield
  rescue Java::ee.ria.xroad.common.CodedException => e
    unless e.translationCode
      # no translationCode, let's try to translate the first faultCode
      begin
        translation =
          t("coded_exception.fault_code.#{e.faultCode.split('.')[0]}",
            :reason => e.faultString, :raise => true)
      rescue
        raise e
      end

      logger.error(ExceptionUtils.getStackTrace(e))
      raise translation
    end

    args_hash = {}

    idx = 0
    e.arguments.each do |arg|
      args_hash[idx.to_s.to_sym] = arg
      idx += 1
    end if e.arguments

    logger.error(ExceptionUtils.getStackTrace(e))
    raise t("coded_exception.#{e.translationCode}", args_hash)
  end

  def handle_unverified_request
    # default behaviour is to reset the session (potential DoS),
    # exception is more appropriate
    raise ActionController::InvalidAuthenticityToken
  end

  def warn(code, text)
    unless params[:ignore] && params[:ignore].include?(code)
      reset_transaction_callbacks
      raise Warning.new(code, text)
    end
  end

  def render_validation_error(exception)
    log_stacktrace(exception)

    prefix = "#{params[:controller]}.#{params[:action]}_params."

    message = t("#{prefix}#{exception.param}.#{exception.validator}", {
      :default => exception.message
    })

    render_error_response(message, exception)
  end

  def render_error(exception)
    log_stacktrace(exception)

    render_error_response(exception.message, exception)
  end

  def log_stacktrace(exception)
    logger.error("#{exception.message}\n#{exception.backtrace.join("\n")}")
  end

  def render_java_error(exception)
    logger.error(ExceptionUtils.getStackTrace(exception))

    if exception.java_kind_of?(Java::ee.ria.xroad.common.CodedException)
      exception_message = exception.getFaultString
    else
      exception_message = ExceptionUtils.getRootCauseMessage(exception)
    end

    render_error_response(exception_message, exception)
  end

  def render_error_response(exception_message, exception = nil)
    execute_after_rollback_actions

    error(get_full_error_message(exception_message))

    # in case of error, only notices from :notice! are rendered
    flash.delete(:notice)

    if request.content_type == "multipart/form-data"
      render_upload_callback(false, {
        :stderr => (exception.stderr if exception.respond_to?(:stderr))
      })
      return
    end

    if request.xhr?
      # ajax request only gets the messages
      # status => 500 invokes .ajaxError() callback in jQuery
      render :json => {
        :messages => flash.discard,
        :data => {
          :stderr => (exception.stderr if exception.respond_to?(:stderr))
        }
      }, :status => 500
    else
      # regular request gets the whole layout with messages
      render :template => "application/index"
    end
  end

  def get_full_error_message(exception_message)
    controller_name = params[:controller]
    action_name = params[:action]

    begin
      translated_action_name =
        t("#{controller_name}.action.#{action_name}", :raise => true)
    rescue
      return exception_message
    end

    return t("common.user_action_error", {
      :translated_action_name => translated_action_name,
      :error_message => exception_message
    })
  end

  def notice(text)
    add_flash(:notice, text)
    logger.info(text)
  end

  def notice!(text)
    add_flash(:notice!, text)
    logger.info(text)
  end

  def error(text)
    add_flash(:error, text)
    logger.error(text)
  end

  def add_flash(type, text)
    flash[type] = [] unless flash[type]
    flash[type] << CGI.escapeHTML(text) if text && text.length > 0
  end

  def render_json(data = nil)
    if request.content_type == "multipart/form-data"
      render_upload_callback(true, data)
    else
      render :json => {
        :messages => flash.discard,
        :data => data
      }
    end
  end

  def render_json_without_messages(data = nil)
    flash.discard

    render :json => {
      :skipMessages => true,
      :data => data
    }
  end

  def render_warning(warning)
    render :json => {
      :messages => flash.discard,
      :warning => {
        :code => warning.code,
        :text => warning.text
      }
    }
  end

  def render_redirect(url, reason)
    render :json => {
      :messages => flash.discard,
      :reason => reason,
      :redirect => root_path
    }
  end

  # s_echo is datatable-specific parameter to handle requests/responses
  # for partial requests
  def render_data_table(data, total_records, s_echo)
    render :json => {
      :skipMessages => true,
      :sEcho => s_echo,
      :iTotalDisplayRecords => total_records,
      :iTotalRecords => total_records,
      :aaData => data
    }
  end

  def render_upload_callback(success, data = {})
    upload_callback_data = current_upload_callback_data

    unless upload_callback_data.empty?
      data.merge!(upload_callback_data)
    end

    json_response = {
      :success => success,
      :messages => flash.discard,
      :data => data
    }

    render :partial => "application/upload_callback", :locals => {
      :callback => current_upload_callback,
      :json_response => json_response
    }
  end

  class << self
    def upload_callbacks(callbacks)
      @upload_callbacks = callbacks
    end

    def get_upload_callback(action_name)
      @upload_callbacks[action_name.to_sym] if @upload_callbacks
    end

    def get_upload_callback_data(action_name)
      @upload_callbacks["#{action_name}_data".to_sym] if @upload_callbacks
    end
  end

  def current_upload_callback
    self.class.get_upload_callback(action_name) || "uploadCallback"
  end

  def current_upload_callback_data
    self.class.get_upload_callback_data(action_name) || {}
  end

  def format_time(time, with_timezone = false)
    return nil if time.to_i == 0

    if with_timezone
      time.strftime(t('common.time_format_with_timezone'))
    else
      time.localtime.strftime(t('common.time_format'))
    end
  end
end
