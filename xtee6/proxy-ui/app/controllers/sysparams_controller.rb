java_import Java::ee.cyber.sdsb.asyncdb.AsyncSenderConf
java_import Java::ee.cyber.sdsb.common.conf.globalconf.ConfigurationAnchor
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.TspType

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
      :anchor_upload_file => [:required]
    })

    anchor_details =
      save_temp_anchor_file(params[:anchor_upload_file].read)

    upload_success(anchor_details)
  end

  def anchor_apply
    authorize!(:upload_anchor)

    validate_params

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
    authorize!(:add_tsp)

    validate_params({
      :name => [:required],
      :url => [:required]
    })

    added_tsp = {
      :name => params[:name],
      :url => params[:url]
    }

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
    authorize!(:delete_tsp)

    validate_params({
      :name => [:required]
    })

    deleted_tsp = nil

    serverconf.tsp.each do |tsp|
      if tsp.name == params[:name]
        deleted_tsp = tsp
      end
    end

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
    authorize!(:generate_internal_ssl)

    script_path = "/usr/share/sdsb/scripts/generate_certificate.sh"

    output = %x[#{script_path} -n internal -f -S -p 2>&1]

    if $?.exitstatus != 0
      logger.warn(output)
      raise t('sysparams.key_generation_failed', :msg => output.split('\n')[-1])
    end

    export_internal_ssl

    reload_nginx
    restart_service("xroad-proxy")

    if x55_installed?
      restart_service("xtee55-servicemediator")
    end

    render_json({
      :hash => CommonUi::CertUtils.cert_hash(read_internal_ssl_cert)
    })
  end

  private

  def read_anchor
    file = SystemProperties::getConfigurationAnchorFile
    content = IO.read(file)

    hash = CryptoUtils::hexDigest(
      CryptoUtils::SHA224_ID, content.to_java_bytes)

    anchor = ConfigurationAnchor.new(file)
    generated_at = Time.at(anchor.getGeneratedAt.getTime / 1000).utc

    return {
      :hash => hash.upcase.scan(/.{1,2}/).join(':'),
      :generated_at => format_time(generated_at, true)
    }
  end

  def reload_nginx
    output = %x[sudo invoke-rc.d nginx reload 2>&1]

    if $?.exitstatus != 0
      error(t('application.restart_service_failed',
              :name => "nginx", :output => output))
    end
  end

  def read_approved_tsps
    approved_tsps = []

    GlobalConf::getApprovedTsps(sdsb_instance).each do |tsp|
      approved_tsps << {
        :name => GlobalConf::getApprovedTspName(sdsb_instance, tsp),
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
