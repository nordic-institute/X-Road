java_import Java::org.apache.commons.lang3.exception.ExceptionUtils

require "validation_helper"
require "ruby_cert_helper"

class BaseController < ActionController::Base

  include ValidationHelper

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

  # rescue_from RequiredFieldError, :with => :render_required_field

  around_filter :catch_java_exceptions
  around_filter :translate_coded_exception

  before_filter :strip_params

  def index
  end

  def menu
    render :partial => "layouts/menu"
  end

  private

  def catch_java_exceptions
    yield
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def translate_coded_exception
    yield
  rescue Java::ee.cyber.sdsb.common.CodedException => e
    raise e unless e.translationCode

    args_hash = {}

    idx = 0
    e.arguments.each do |arg|
      args_hash[idx.to_s.to_sym] = arg
      idx += 1
    end if e.arguments

    raise t("coded_exception.#{e.translationCode}", args_hash)
  end

  def handle_unverified_request
    # default behaviour is to reset the session (potential DoS),
    # exception is more appropriate
    raise ActionController::InvalidAuthenticityToken
  end

  def dependencies
    []
  end

  def check_dependencies
    @disabled_controllers = []

    dependencies.each do |controller, checks|
      logger.debug("Checking dependencies for #{controller}")

      checks.each do |check|
        @disabled_controllers << controller unless send(check)
      end
    end
  end

  def warn(code, text)
    unless params[:ignore] && params[:ignore].include?(code)
      raise Warning.new(code, text)
    end
  end

  def render_error(exception)
    logger.error("#{exception.message}\n#{exception.backtrace.join("\n")}")
    error(exception.message)

    render_error_response
  end

  def render_java_error(exception)
    logger.error(ExceptionUtils.getStackTrace(exception))
    error(ExceptionUtils.getRootCauseMessage(exception))

    render_error_response
  end

  def render_error_response
    if request.content_type == "multipart/form-data"
      upload_error
      return
    end

    if request.xhr?
      # ajax request only gets the messages
      # status => 500 invokes .ajaxError() callback in jQuery
      render :partial => "application/error", :status => 500
    else
      # regular request gets the whole layout with messages
      render :template => "application/index"
    end
  end

  def notice(text)
    add_flash(:notice, text)
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
    render :json => {
      :messages => flash.discard,
      :data => data
    }
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

  def render_required_field(error)
    render :json => {
      :required => error.field
    }
  end

  def render_redirect
    render :json => {
      :messages => flash.discard,
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

  def upload_success(data = nil, callback = nil)
    upload_callback(true, data, callback)
  end

  def upload_error(data = nil, callback = nil)
    upload_callback(false, data, callback)
  end

  def upload_callback(success, data = nil, callback = nil)
    json_response = {
      :success => success,
      :messages => flash.discard,
      :data => data
    }
    render :partial => "application/upload_callback", :locals => {
      :callback => callback,
      :json_response => json_response
    }
  end
end
