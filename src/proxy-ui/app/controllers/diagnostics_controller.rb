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

java_import Java::ee.ria.xroad.common.DiagnosticsStatus
java_import Java::ee.ria.xroad.common.DiagnosticsErrorCodes
java_import Java::ee.ria.xroad.common.util.JsonUtils
java_import Java::ee.ria.xroad.common.PortNumbers
java_import Java::com.google.gson.reflect.TypeToken
java_import Java::java.util.Collection
require 'json'
require 'time'

# Controller for the diagnostics view
class DiagnosticsController < ApplicationController
  helper_method :get_status_message, :get_status_class, :get_formatted_time

  def index
    logger.info("Diagnostics index")
    authorize!(:diagnostics)
    prepare_confclient_ui
    prepare_timestamper_ui
    prepare_ocsp_ui
  end

  def prepare_timestamper_ui
    @timestamp_data = query_timestamper_status
  end

  def prepare_confclient_ui
    response = query_confclient_status
    if !response.nil? && response.java_kind_of?(DiagnosticsStatus)
      return_code = response.getReturnCode
      @global_conf_status = {
        status_class: get_status_class(return_code),
        status_message: get_status_message(return_code),
        prev_update: response.getFormattedPrevUpdate,
        next_update: response.getFormattedNextUpdate
      }
    else
      @global_conf_status = {
        status_class: 'diagnostics_status_fail',
        status_message: t('diagnostics.error_code_connection_failed'),
        prev_update: '',
        next_update: ''
      }
    end
  end

  def prepare_ocsp_ui
    data = query_ocsp_status
    if data && data.is_a?(Hash)
      @ocsp_data = data['certificationServiceStatusMap'] || {}
    else
      @ocsp_data = {}
    end
  end

  def get_status_class(return_code)
    case return_code
    when DiagnosticsErrorCodes::RETURN_SUCCESS
      'diagnostics_status_ok'
    when DiagnosticsErrorCodes::ERROR_CODE_UNINITIALIZED,
         DiagnosticsErrorCodes::ERROR_CODE_TIMESTAMP_UNINITIALIZED,
         DiagnosticsErrorCodes::ERROR_CODE_OCSP_UNINITIALIZED
      'diagnostics_status_waiting'
    else
      'diagnostics_status_fail'
    end
  end

  def get_formatted_time(tm)
    tm['hour'].to_s.rjust(2, '0') + ':' + tm['minute'].to_s.rjust(2, '0')
  end

  def query_confclient_status
    logger.info("Query configuration client status")

    port = SystemProperties.getConfigurationClientAdminPort
    url = "http://localhost:#{port}/status"

    query_status_from_adminport(url, DiagnosticsStatus.java_class)
  end

  def query_timestamper_status
    logger.info("Query timestamper status")

    port = PortNumbers::ADMIN_PORT
    url = "http://localhost:#{port}/timestampstatus"

    query_status_from_adminport(url, nil)
  end

  def query_ocsp_status
    logger.info("Query OCSP status")

    port =  SystemProperties.getSignerAdminPort
    url = "http://localhost:#{port}/status"

    query_status_from_adminport(url, nil)
  end

  def query_status_from_adminport(url, target_class)
    unless url.is_a?(String)
      logger.error("Non-string URL, defaulting to an empty response")
      return nil
    end

    response = nil
    response_data = nil

    begin
      uri = URI(url)
      response = Net::HTTP.get_response(uri)
      logger.debug("Response code: " + response.code + " message: " +
                       response.message + " body: " + response.body)
    rescue
      logger.error("Unable to connect to designated admin port, defaulting to an empty response")
      log_stacktrace($!)
      return nil
    end

    if response.code == '500'
      logger.error(response.code + ": " + response.message)
      return nil
    end

    begin
      if target_class.nil?
        response_data = JSON.parse(response.body)
      else
        logger.info("Target class detected, attempting to deserialize")
        gson = JsonUtils.getSerializer
        response_data = gson.fromJson(response.body, target_class)
      end
      rescue
        logger.error("Status response parsing failed, defaulting to an empty response")
        log_stacktrace($!)
        return nil
    end
    response_data
  end

  def get_status_message(return_code)
    case return_code
    when DiagnosticsErrorCodes::RETURN_SUCCESS
      t('diagnostics.return_success')
    when DiagnosticsErrorCodes::ERROR_CODE_INTERNAL
      t('diagnostics.error_code_internal')
    when DiagnosticsErrorCodes::ERROR_CODE_INVALID_SIGNATURE_VALUE
      t('diagnostics.error_code_invalid_signature_value')
    when DiagnosticsErrorCodes::ERROR_CODE_EXPIRED_CONF
      t('diagnostics.error_code_expired_conf')
    when DiagnosticsErrorCodes::ERROR_CODE_CANNOT_DOWNLOAD_CONF
      t('diagnostics.error_code_cannot_download_conf')
    when DiagnosticsErrorCodes::ERROR_CODE_MISSING_PRIVATE_PARAMS
      t('diagnostics.error_code_missing_private_params')
    when DiagnosticsErrorCodes::ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT
      t('diagnostics.error_code_timestamp_request_timed_out')
    when DiagnosticsErrorCodes::ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL
      t('diagnostics.error_code_malformed_timestamp_server_url')
    when DiagnosticsErrorCodes::ERROR_CODE_UNINITIALIZED
      t('diagnostics.error_code_uninitialized')
    when DiagnosticsErrorCodes::ERROR_CODE_TIMESTAMP_UNINITIALIZED
      t('diagnostics.error_code_timestamp_uninitialized')
    when DiagnosticsErrorCodes::ERROR_CODE_OCSP_CONNECTION_ERROR
      t('diagnostics.error_code_ocsp_connection_error')
    when DiagnosticsErrorCodes::ERROR_CODE_OCSP_FAILED
      t('diagnostics.error_code_ocsp_failed')
    when DiagnosticsErrorCodes::ERROR_CODE_OCSP_RESPONSE_INVALID
      t('diagnostics.error_code_ocsp_response_invalid')
    when DiagnosticsErrorCodes::ERROR_CODE_OCSP_UNINITIALIZED
      t('diagnostics.error_code_ocsp_uninitialized')
    when DiagnosticsErrorCodes::ERROR_CODE_LOGMANAGER_UNAVAILABLE
      t('diagnostics.error_code_logmanager_unavailable')
    else
      t('diagnostics.error_code_unknown')
    end
  end
end
