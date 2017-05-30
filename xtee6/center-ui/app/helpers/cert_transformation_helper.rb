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

module CertTransformationHelper

  private

  def upload_cert(file_param, include_dump = nil)
    if !file_param || !file_param.original_filename ||
          file_param.original_filename.empty?
      raise t("common.filename_empty")
    end

    uploaded_bytes = file_param.read

    cert_obj = CommonUi::CertUtils.cert_object(uploaded_bytes)

    cert_id = add_temp_cert_to_session(cert_obj.to_der)

    cert_data = get_cert_data(cert_obj, cert_id)

    if include_dump
      cert_data[:cert_dump] = CommonUi::CertUtils.cert_dump(uploaded_bytes)
    end

    cert_data
  rescue OpenSSL::OpenSSLError
    raise t("validation.invalid_cert")
  end

  def read_temp_cert(id)
    temp_cert = get_temp_cert_from_session(id)

    if temp_cert == nil
      raise("Temp cert with id '#{id}' not found")
    end

    get_cert_data_from_bytes(temp_cert, id)
  end

  def get_cert_data_from_bytes(raw_cert, cert_id = nil)
    get_cert_data(CommonUi::CertUtils.cert_object(raw_cert), cert_id)
  end

  def get_cert_data(cert_obj, cert_id = nil)
    {
      :csp => CommonUi::CertUtils.cert_csp(cert_obj),
      :serial_number => cert_obj.serial.to_s,
      :subject => cert_obj.subject.to_s,
      :issuer => cert_obj.issuer.to_s,
      :valid_from => format_time(cert_obj.not_before), # TODO: valid_not_before
      :expires => format_time(cert_obj.not_after),     # TODO: valid_not_after
      :temp_cert_id => cert_id
    }
  end

  def add_temp_cert_to_session(cert_bytes)
    session[:temp_certs] ||= []
    session[:temp_certs] << cert_bytes

    session[:temp_certs].size - 1
  end

  def clear_all_temp_certs_from_session
    session[:temp_certs] = nil
  end

  def get_temp_cert_from_session(cert_id)
    if cert_id == nil || cert_id.to_s.empty?
      return nil
    end

    id = cert_id.is_a?(Integer) ? cert_id :cert_id.to_i

    result = session[:temp_certs][id]

    unless result
      raise("Cached certificate has been lost from session, "\
          "please retry Your last action!")
    end

    return result
  end
end
