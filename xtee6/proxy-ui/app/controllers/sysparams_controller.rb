java_import Java::ee.cyber.sdsb.asyncdb.AsyncSenderConf
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.CertificateType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.GlobalConfDistributorType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.TspType

class SysparamsController < ApplicationController

  def index
    authorize!(:view_sys_params)
  end

  def distributors
    authorize!(:view_distributors)

    render_json(read_distributors)
  end

  def distributor_cert_load
    authorize!(:add_distributor)

    validate_params({
      :url => [],
      :cert => [RequiredValidator.new]
    })

    uploaded_cert = pem_to_der(params[:cert].read)
    cert_obj = cert_object(uploaded_cert)

    upload_success({
      :subject => cert_obj.subject.to_s,
      :serial => cert_obj.serial.to_s,
    }, "distributorCertLoadCallback")
  end

  def distributor_add
    authorize!(:add_distributor)

    validate_params({
      :url => [RequiredValidator.new],
      :cert => [RequiredValidator.new],
    })

    uploaded_cert = pem_to_der(params[:cert].read)

    serverconf.globalConfDistributor.each do |distributor|
      if params[:url] == distributor.url && cert_hash(uploaded_cert) ==
          cert_hash(distributor.verificationCert.data)
        raise t('sysparams.distributor_exists')
      end
    end

    cert = CertificateType.new
    cert.data = uploaded_cert.to_java_bytes

    distributor = GlobalConfDistributorType.new
    distributor.verificationCert = cert
    distributor.url = params[:url]

    serverconf.globalConfDistributor.add(distributor)
    serverconf_save

    upload_success(read_distributors, "distributorAddCallback")
  end

  def distributor_delete
    authorize!(:delete_distributor)

    validate_params({
      :url => [RequiredValidator.new],
      :cert_subject => [RequiredValidator.new]
    })

    deleted_distributor = nil

    serverconf.globalConfDistributor.each do |distributor|
      if params[:url] == distributor.url && params[:cert_subject] ==
          cert_object(distributor.verificationCert.data).subject.to_s
        deleted_distributor = distributor
      end
    end

    serverconf.globalConfDistributor.remove(deleted_distributor)
    serverconf_save

    render_json(read_distributors)
  end

  def tsps_approved
    authorize!(:view_tsps)

    render_json(read_approved_tsps)
  end

  def tsps
    authorize!(:view_tsps)

    render_json(read_tsps)
  end

  def tsp_add
    authorize!(:add_tsp)

    validate_params({
      :name => [RequiredValidator.new],
      :url => [RequiredValidator.new]
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
      :name => [RequiredValidator.new]
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

  def async_params
    authorize!(:view_async_params)

    render_json(read_async_params)
  end

  def async_params_edit
    authorize!(:edit_async_params)
    
    validate_params({
      :base_delay => [RequiredValidator.new, IntValidator.new],
      :max_delay => [RequiredValidator.new, IntValidator.new],
      :max_senders => [RequiredValidator.new, IntValidator.new]
    })

    async_sender_conf = AsyncSenderConf.new
    async_sender_conf.baseDelay = params[:base_delay].to_i
    async_sender_conf.maxDelay = params[:max_delay].to_i
    async_sender_conf.maxSenders = params[:max_senders].to_i
    async_sender_conf.save

    render_json(read_async_params)
  end

  def internal_ssl_cert
    authorize!(:view_internal_ssl_cert)

    render_json({
      :hash => cert_hash(read_internal_ssl_cert)
    })
  end

  def internal_ssl_cert_details
    authorize!(:view_internal_ssl_cert)

    cert_obj = read_internal_ssl_cert

    render_json({
      :dump => cert_dump(cert_obj),
      :hash => cert_hash(cert_obj)
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
      :hash => cert_hash(read_internal_ssl_cert)
    })
  end

  private

  def reload_nginx
    output = %x[sudo invoke-rc.d nginx reload 2>&1]

    if $?.exitstatus != 0
      error(t('application.restart_service_failed',
              :name => "nginx", :output => output))
    end
  end

  def read_distributors
    distributors = []

    serverconf.globalConfDistributor.each do |distributor|
      cert_obj = cert_object(distributor.verificationCert.data)

      distributors << {
        :url => distributor.url,
        :cert_subject => cert_obj.subject.to_s
      }
    end

    distributors
  end

  def read_approved_tsps
    approved_tsps = []

    globalconf.root.approvedTsp.each do |tsp|
      approved_tsps << {
        :name => tsp.name,
        :url => tsp.url
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
