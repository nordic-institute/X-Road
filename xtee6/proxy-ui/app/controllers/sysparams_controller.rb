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

java_import Java::ee.ria.xroad.asyncdb.AsyncSenderConf
java_import Java::ee.ria.xroad.common.conf.globalconf.ConfigurationAnchor
java_import Java::ee.ria.xroad.common.conf.serverconf.model.TspType
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.common.conf.InternalSSLKey

class SysparamsController < ApplicationController

  def index
    authorize!(:view_sys_params)
  end

  def sysparams
    authorize!(:view_sys_params)

    sysparams = {}

    if can?(:view_anchor)
      sysparams[:anchor] = read_anchor
    end

    if can?(:view_tsps)
      sysparams[:tsps] = read_tsps
    end

    if can?(:view_async_params)
      sysparams[:async] = read_async_params
    end

    if can?(:view_internal_ssl_cert)
      sysparams[:internal_ssl_cert] = {
        :hash => CommonUi::CertUtils.cert_hash(read_internal_ssl_cert)
      }
    end

    render_json(sysparams)
  end

  def anchor_upload
    authorize!(:upload_anchor)

    validate_params({
      :file_upload => [:required]
    })

    anchor_details =
      save_temp_anchor_file(params[:file_upload].read)

    render_json(anchor_details)
  end

  def anchor_apply
    audit_log("Upload configuration anchor", audit_log_data = {})

    authorize!(:upload_anchor)

    validate_params

    anchor_details = get_temp_anchor_details

    audit_log_data[:anchorFileHash] = anchor_details[:hash]
    audit_log_data[:anchorFileHashAlgorithm] = anchor_details[:hash_algorithm]
    audit_log_data[:generatedAt] = anchor_details[:generated_at]

    apply_temp_anchor_file

    render_json
  end

  def anchor_download
    authorize!(:download_anchor)

    generated_at = read_anchor[:generated_at].gsub(" ", "_")

    send_file(SystemProperties::getConfigurationAnchorFile, :filename =>
      "configuration_anchor_#{generated_at}.xml")
  end

  def tsps_approved
    authorize!(:view_tsps)

    render_json(read_approved_tsps)
  end

  def tsp_add
    audit_log("Add timestamping service", audit_log_data = {})

    authorize!(:add_tsp)

    validate_params({
      :name => [:required],
      :url => [:required]
    })

    GlobalConf::verifyValidity

    added_tsp = {
      :name => params[:name],
      :url => params[:url]
    }

    audit_log_data[:tspName] = params[:name]
    audit_log_data[:tspUrl] = params[:url]

    existing_tsps = read_tsps
    approved_tsps = read_approved_tsps

    if existing_tsps.include?(added_tsp)
      raise t('sysparams.tsp_exists')
    end

    if !approved_tsps.include?(added_tsp)
      raise t('sysparams.tsp_not_approved')
    end

    tsp = TspType.new
    tsp.name = added_tsp[:name]
    tsp.url = added_tsp[:url]

    serverconf.tsp.add(tsp)
    serverconf_save

    render_json(read_tsps)
  end

  def tsp_delete
    audit_log("Delete timestamping service", audit_log_data = {})

    authorize!(:delete_tsp)

    validate_params({
      :name => [:required]
    })

    GlobalConf::verifyValidity

    deleted_tsp = nil

    serverconf.tsp.each do |tsp|
      if tsp.name == params[:name]
        deleted_tsp = tsp
      end
    end

    audit_log_data[:tspName] = deleted_tsp.name
    audit_log_data[:tspUrl] = deleted_tsp.url

    serverconf.tsp.remove(deleted_tsp)
    serverconf_save

    render_json(read_tsps)
  end

  def async_params_edit
    authorize!(:edit_async_params)

    validate_params({
      :base_delay => [:required, :int],
      :max_delay => [:required, :int],
      :max_senders => [:required, :int]
    })

    async_sender_conf = AsyncSenderConf.new
    async_sender_conf.baseDelay = params[:base_delay].to_i
    async_sender_conf.maxDelay = params[:max_delay].to_i
    async_sender_conf.maxSenders = params[:max_senders].to_i
    async_sender_conf.save

    render_json(read_async_params)
  end

  def internal_ssl_cert_details
    authorize!(:view_internal_ssl_cert)

    cert_obj = read_internal_ssl_cert

    render_json({
      :dump => CommonUi::CertUtils.cert_dump(cert_obj),
      :hash => CommonUi::CertUtils.cert_hash(cert_obj)
    })
  end

  def internal_ssl_cert_export
    authorize!(:export_internal_ssl_cert)

    data = export_cert(read_internal_ssl_cert)

    send_data(data, :filename => "certs.tar.gz")
  end

  def internal_ssl_generate
    audit_log("Generate new internal TLS key and certificate", audit_log_data = {})

    authorize!(:generate_internal_ssl)

    script_path = "/usr/share/xroad/scripts/generate_certificate.sh"

    output = %x[#{script_path} -n internal -f -S -p 2>&1]

    if $?.exitstatus != 0
      logger.warn(output)
      raise t('sysparams.key_generation_failed', :msg => output.split('\n')[-1])
    end

    restart_service("xroad-proxy")

    if x55_installed?
      export_v6_internal_tls_key
      restart_service("xtee55-clientemediator")
    end

    cert_hash = CommonUi::CertUtils.cert_hash(read_internal_ssl_cert)
    audit_log_data[:certHash] = cert_hash
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    render_json({
      :hash => cert_hash
    })
  end

  def generate_csr
    audit_log("Generate certificate request for TLS", audit_log_data = {})

    authorize!(:generate_internal_cert_req)

    audit_log_data[:subjectName] = params[:subject_name]

    kp = CertUtils::readKeyPairFromPemFile(SystemProperties::getConfPath() + InternalSSLKey::PK_FILE_NAME)
    csr = CertUtils::generateCertRequest(kp.getPrivate(), kp.getPublic(), params[:subject_name])

    csr_file = SecureRandom.hex(4)

    File.open(CommonUi::IOUtils.temp_file(csr_file), 'wb') do |f|
      f.write(csr)
    end

    render_json({
                    :tokens => view_context.columns(SignerProxy::getTokens),
                    :redirect => csr_file
                })
  end

  def download_csr
    validate_params({
                        :csr => [:required, :filename],
                        :key_usage => [:required]
                    })

    file = CommonUi::IOUtils.temp_file(params[:csr])

    # file name parts
    date = Time.now.strftime("%Y%m%d")

    send_file(file, :filename => "tls_cert_request_#{date}.p10")
  end

  def import_cert
    audit_log("Import TLS certificate from file", audit_log_data = {})

    validate_params({
                        :file_upload => [:required]
                    })

    cert_bytes = params[:file_upload].read.to_java_bytes

    # check that this is valid x509 certificate
    cert_obj = CommonUi::CertUtils.cert_object(cert_bytes)

    audit_log_data[:certFileName] = params[:file_upload].original_filename
    audit_log_data[:certHash] = CommonUi::CertUtils.cert_hash(cert_bytes)
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    # write the uploaded certificate to file
    CertUtils::writePemToFile(cert_bytes, SystemProperties::getConfPath() + "ssl/internal.crt")

    # create pkcs12 keystore
    CertUtils::createPkcs12(
        SystemProperties::getConfPath() + InternalSSLKey::PK_FILE_NAME,
        SystemProperties::getConfPath() + InternalSSLKey::CRT_FILE_NAME,
        SystemProperties::getConfPath() + InternalSSLKey::KEY_FILE_NAME)

    notice(t('sysparams.cert_loaded'))

    restart_service("xroad-proxy")

    if x55_installed?
      export_v6_internal_tls_key
      restart_service("xtee55-clientemediator")
    end

    cert_hash = CommonUi::CertUtils.cert_hash(read_internal_ssl_cert)
    audit_log_data[:certHash] = cert_hash
    audit_log_data[:certHashAlgorithm] = CommonUi::CertUtils.cert_hash_algorithm

    render_json({
                    :hash => cert_hash
                })

  rescue
    error(t('sysparams.cert_invalid'))
    render_json
  end

  private

  def export_v6_internal_tls_key
    if exporter = SystemProperties::getInternalTlsKeyExporterCommand
      output = %x["#{exporter}" 2>&1]

      if $?.exitstatus != 0
        logger.error(output)
        error(t('sysparams.internal_tls_key_export_failed'))
      end
    else
      logger.warn("Internal TLS key exporter unspecified, skipping")
    end
  end

  def read_anchor
    file = SystemProperties::getConfigurationAnchorFile
    content = IO.read(file)

    hash = CryptoUtils::hexDigest(
      CryptoUtils::DEFAULT_ANCHOR_HASH_ALGORITHM_ID, content.to_java_bytes)

    anchor = ConfigurationAnchor.new(file)
    generated_at = Time.at(anchor.getGeneratedAt.getTime / 1000).utc

    return {
      :hash => hash.upcase.scan(/.{1,2}/).join(':'),
      :generated_at => format_time(generated_at, true)
    }
  end

  def read_approved_tsps
    approved_tsps = []

    GlobalConf::getApprovedTsps(xroad_instance).each do |tsp|
      approved_tsps << {
        :name => GlobalConf::getApprovedTspName(xroad_instance, tsp),
        :url => tsp
      }
    end

    approved_tsps
  end

  def read_tsps
    tsps = []

    serverconf.tsp.each do |tsp|
      tsps << {
        :name => tsp.name,
        :url => tsp.url
      }
    end

    tsps
  end

  def read_async_params
    async_sender_conf = AsyncSenderConf.new

    data = {
      :base_delay => async_sender_conf.baseDelay,
      :max_delay => async_sender_conf.maxDelay,
      :max_senders => async_sender_conf.maxSenders
    }
  end

end
