require 'net/http'

java_import Java::ee.cyber.sdsb.common.conf.serverconf.AsyncSenderType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.ClientType
java_import Java::ee.cyber.sdsb.common.conf.serverconf.GlobalConfDistributorType
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.proxyui.SignerProxy

class InitController < ApplicationController

  skip_before_filter :check_conf, :read_server_id

  def index
    authorize!(:init_config)

    if globalconf.exists? && serverconf.exists? && serverconf.root.owner &&
        !serverconf.root.globalConfDistributor.empty
      raise "Security Server already initialized"
    end

    if !globalconf.exists? || !serverconf.exists? ||
        serverconf.root.globalConfDistributor.empty
      @init_globalconf = true
    end

  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def init_globalconf
    authorize!(:init_config)

    validate_params({
      :globalconf_url => [RequiredValidator.new],
      :globalconf_cert => [RequiredValidator.new]
    })

    unless serverconf.exists? && serverconf.root.owner
      @init_serverconf = true
    end

    unless serverconf.exists?
      sct = serverconf.factory.createServerConfType
      sc = serverconf.factory.createServerConf(sct)
      serverconf.init(sc)
    end

    distributor = GlobalConfDistributorType.new
    distributor.url = params[:globalconf_url]
    distributor.verificationCert = params[:globalconf_cert].read.to_java_bytes

    serverconf.root.globalConfDistributor.clear
    serverconf.root.globalConfDistributor.add(distributor)

    serverconf.write

    download_globalconf

    notice("Successfully downloaded globalconf")

    upload_success({:init_serverconf => @init_serverconf})

  rescue Exception
    render_error($!)
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def init_serverconf
    authorize!(:init_config)

    raise "serverconf exists" if serverconf.exists? && serverconf.root.owner

    validate_params({
      :owner_class => [RequiredValidator.new],
      :owner_code => [RequiredValidator.new],
      :server_code => [RequiredValidator.new],
      :pin => [RequiredValidator.new],
      :pin_repeat => [RequiredValidator.new]
    })

    if params[:pin] != params[:pin_repeat]
      raise "Software token pins do not match"
    else
      pin = Array.new
      params[:pin].bytes do |b|
        pin << b
      end

      SignerProxy::initSoftwareToken(pin.to_java(:char))
    end

    unless serverconf.exists?
      sct = serverconf.factory.createServerConfType
      sc = serverconf.factory.createServerConf(sct)
      serverconf.init(sc)
    end

    owner_name = get_member_name(params[:owner_class], params[:owner_code])

    unless owner_name
      raise "Member not found in globalconf"
    end

    owner_id = ClientId.create(
      globalconf.root.instanceIdentifier,
      params[:owner_class],
      params[:owner_code], nil)

    owner = ClientType.new
    owner.identifier = owner_id
    owner.fullName = owner_name
    owner.clientStatus = ClientsController::STATE_SAVED
    owner.isAuthentication = "NOSSL"
    owner.id = "owner"

    async_sender = AsyncSenderType.new
    async_sender.baseDelay = 300
    async_sender.maxDelay = 1800
    async_sender.maxSenders = 1000

    serverconf.root.client.add(owner)
    serverconf.root.owner = owner
    serverconf.root.serverCode = params[:server_code]
    serverconf.root.asyncSender = async_sender

    serverconf.write

    begin
      import_services
    rescue
      error("Failed to import services: #{$!.message}")
    end

    render_json

  rescue Exception
    render_error($!)
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def member_classes
    authorize!(:init_config)

    classes = []

    if globalconf.exists?
      globalconf.root.memberClass.each do |memberClass|
        classes << memberClass.code
      end
    end

    render_json(classes)
  end

  def member_codes
    authorize!(:init_config)

    codes = []

    if globalconf.exists?
      globalconf.root.member.each do |member|
        codes << member.memberCode
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

  def download_globalconf
    logger.info("Starting globalconf download")

    port = SystemProperties::getDistributedFilesAdminPort()
    uri = URI("http://localhost:#{port}/execute")

    output = Net::HTTP.get(uri)
    logger.info(output)
  end

  def get_member_name(member_class, member_code)
    logger.debug("finding member name for: #{member_class}, #{member_code}")

    globalconf.root.member.each do |member|
      if member_class == member.memberClass && member_code == member.memberCode
        return member.name
      end
    end if member_class && member_code

    return nil
  end
end
