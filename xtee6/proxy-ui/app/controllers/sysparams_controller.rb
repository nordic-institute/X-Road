java_import Java::ee.cyber.sdsb.common.conf.serverconf.GlobalConfDistributorType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.AsyncSenderType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.TspType

class SysparamsController < ApplicationController
  include CertTransformationHelper
  def index
    authorize!(:view_sys_params)
  end

  def get_distributors
    authorize!(:get_global_distributors)
    render_json(get_dists)
  end

  def add_distributor
    authorize!(:add_global_distributor)

    validate_params({
      :dist_address => [],
      :dist_certificate => [],
      :dist_dn => [],
      :dist_serial => []
    })

    distributor = GlobalConfDistributorType.new
    cert = OpenSSL::X509::Certificate.new(get_temp_cert_from_session(params[:dist_certificate])).to_pem
    distributor.verificationCert = cert.bytes.to_a
    distributor.url = params[:dist_address]

    serverconf.root.globalConfDistributor.add(distributor)
    serverconf.write

    render_json(get_dists)
  end

  def upload_distributor_cert

    authorize!(:add_global_distributor)

    validate_params({
      :dist_certificate => []
    })

    cert_data = upload_cert(params[:dist_certificate])

    upload_success(cert_data, "certUploadCallback")

  end

  def delete_distributor
    authorize!(:delete_global_distributor)

    validate_params({
      :dist_address => [],
      :dist_certificate => []
    })

    distributor = nil

    serverconf.root.getGlobalConfDistributor.each do |dist|
      if params[:dist_address] == dist.getUrl && params[:dist_certificate] == get_cert_data_from_bytes(dist.getVerificationCert.to_s, nil)[:issuer]
        distributor = dist
      end
    end

    serverconf.root.globalConfDistributor.remove(distributor)
    serverconf.write

    render_json(get_dists)
  end

  def get_timestamps
    authorize!(:get_tsps)

    tsps = []
    globalconf.root.getApprovedTsp.each do |tsp|
      tsps << { :tsp_name => tsp.getName }
    end
    render_json(tsps)
  end

  def get_timestamping_services
    authorize!(:get_tsps)

    render_json(get_tsps)
  end

  def add_timestamping_service
    authorize!(:add_tsp)

    validate_params({
      :tsp_name => []
    })
    
    tsp = TspType.new

    globalconf.root.getApprovedTsp.each do |timestamp|
      t = { :tsp_name => timestamp.getName, :tsp_url => timestamp.getUrl }
      if get_tsps.include?(t) == false && timestamp.getName == params[:tsp_name]
        tsp.name = timestamp.getName
        tsp.url = timestamp.getUrl
      end
    end

    unless tsp.name.nil?
      serverconf.root.tsp.add(tsp)
      serverconf.write
    end
    render_json(get_tsps)
  end

  def delete_timestamping_service
    authorize!(:delete_tsp)

    validate_params({
      :tsp_name => []
    })

    tsp_ = nil
    serverconf.root.getTsp.each do |tsp|
      if tsp.getName == params[:tsp_name]
        tsp_ = tsp
      end
    end
    serverconf.root.tsp.remove(tsp_)
    serverconf.write

    render_json(get_tsps)
  end

  def edit_timestamping_service
    authorize!(:edit_tsp)
    validate_params({
      :tsp_name => [],
      :tsp_url => []
    })

    tsp = nil
    serverconf.root.getTsp.each do |tsp_|
      if tsp_.getName == params[:tsp_name]
        tsp = tsp_
      end
    end

    serverconf.root.tsp.remove(tsp)
    tsp = TspType.new
    tsp.name = params[:tsp_name]
    tsp.url = params[:tsp_url]

    serverconf.root.tsp.add(tsp)
    serverconf.write

    render_json(get_tsps)
  end

  def get_tsps
    tsps = []
    serverconf.root.getTsp.each do |tsp|
      tsps << { :tsp_name => tsp.getName, :tsp_url => tsp.getUrl } unless tsp.getName.nil? && tsp.getUrl.nil?
    end
    tsps
  end

  def get_async_requests
    authorize!(:get_async_requests)

    render_json(get_async)
  end

  def edit_async_requests
    authorize!(:edit_async_requests)
    
    validate_params({
      :base_delay => [],
      :max_delay => [],
      :max_senders => []
    })

    async = AsyncSenderType.new
    async.baseDelay = params[:base_delay].to_i
    async.maxDelay = params[:max_delay].to_i
    async.maxSenders = params[:max_senders].to_i

    serverconf.root.setAsyncSender(async)
    serverconf.write

    render_json(get_async)
  end

  def get_async
    async_sender = serverconf.root.getAsyncSender
    data = {
      :base_delay => async_sender.getBaseDelay,
      :max_delay => async_sender.getMaxDelay,
      :max_senders => async_sender.getMaxSenders
    }
  end

  def get_dists
    data = []
    serverconf.root.getGlobalConfDistributor.each do |distributor|
      data << {
        :url => distributor.getUrl,
        :certificate => get_cert_data_from_bytes(distributor.getVerificationCert.to_s, cert_id = nil)[:issuer]
      }
    end
    data
  end

  def generate_ssl
    authorize!(:gen_ssl)
    net = nil
    Socket.ip_address_list.each do |addr|
      next unless addr.ip_address.include? '192.168'
      net = { :ip => addr.ip_address, :host => Socket.gethostname }
    end
    cert = create_cert(net[:host], net[:ip])
    serverconf.root.setInternalSSLCert(cert.to_pem.bytes.to_a)
    serverconf.write
    render_json(true)
  end

  def export_ssl_cert
    authorize!(:export_ssl)
    raw_cert = OpenSSL::X509::Certificate.new(serverconf.root.getInternalSSLCert.to_s)
    data = export_cert(raw_cert)
    send_data data, :filename => "certs.tar.gz"
  end

  def get_cert
    authorize!(:get_ssl)

    cert = nil

    unless serverconf.root.getInternalSSLCert.to_s.empty? || serverconf.root.getInternalSSLCert.nil?
      raw_cert = OpenSSL::X509::Certificate.new(serverconf.root.getInternalSSLCert.to_s)
      cert = get_cert_data_from_bytes(raw_cert)
      cert[:fingerprint] = get_fingerprint(raw_cert.to_der)
    else
      cert = { :fingerprint => '' }
    end
    render_json(cert)
  end

  def cert_details
    authorize!(:get_ssl)

    raw_cert = OpenSSL::X509::Certificate.new(serverconf.root.getInternalSSLCert.to_s)
    digest = CryptoUtils::certHash(raw_cert.to_der.to_java_bytes)
    details = {
      :cert_dump => %x[echo "#{raw_cert.to_s}" | openssl x509 -text -noout 2>&1],
      :cert_hash => CryptoUtils::encodeBase64(digest)
    }

    render_json(details)
  end

end