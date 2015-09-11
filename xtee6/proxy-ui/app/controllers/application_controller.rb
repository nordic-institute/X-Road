require "management_request_helper"

java_import Java::ee.ria.xroad.common.SystemProperties
java_import Java::ee.ria.xroad.common.conf.globalconf.GlobalConf
java_import Java::ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx
java_import Java::ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl
java_import Java::ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl
java_import Java::ee.ria.xroad.common.conf.serverconf.dao.UiUserDAOImpl
java_import Java::ee.ria.xroad.common.conf.serverconf.model.UiUserType
java_import Java::ee.ria.xroad.common.db.CustomPostgreSQLDialect
java_import Java::ee.ria.xroad.common.db.HibernateUtil
java_import Java::ee.ria.xroad.common.identifier.ClientId
java_import Java::ee.ria.xroad.common.identifier.SecurityServerId
java_import Java::ee.ria.xroad.common.util.CryptoUtils
java_import Java::ee.ria.xroad.commonui.SignerProxy

class ApplicationController < BaseController

  XROAD_INSTALLED_FILE = "/usr/xtee/etc/v6_xroad_installed"
  XROAD_ACTIVATED_FILE = "/usr/xtee/etc/v6_xroad_activated"
  XROAD_PROMOTED_FILE = "/usr/xtee/etc/v6_xroad_promoted"

  XROAD_PROMOTED_PRIVILEGES = [
    :add_wsdl,
    :enable_disable_wsdl,
    :refresh_wsdl,
    :delete_wsdl_missing_services,
    :delete_wsdl,
    :edit_service_params,
    :edit_service_acl,
    :import_export_service_acl,
    :edit_subject_open_services,
    :import_export_subject_acl,
    :generate_internal_ssl,
    :add_client_internal_cert,
    :delete_client_internal_cert,
    :edit_client_internal_connection_type,
    :edit_acl_subject_open_services
  ]

  INTERNAL_SSL_CERT_PATH = "/etc/xroad/ssl/internal.crt"

  include ManagementRequestHelper

  around_filter :transaction

  before_filter :demote_xroad
  before_filter :check_conf, :except => [:menu, :alerts]
  before_filter :read_locale
  before_filter :read_server_id, :except => [:menu, :alerts]
  before_filter :read_owner_name, :except => [:menu, :alerts]

  helper_method :x55_installed?, :xroad_activated?, :xroad_dependent?, :xroad_promoted?

  def index
    if can?(:view_clients)
      redirect_to :controller => 'clients', :action => 'index'
    else
      redirect_to :controller => 'sysparams', :action => 'index'
    end
  end

  def alerts
    logger.debug("checking for alerts")

    if initialized?
      @alerts = []

      unless GlobalConf::isValid
        @alerts << t('application.globalconf_invalid')
      end

      SignerProxy::getTokens.each do |token|
        if token.id == SignerProxy::SSL_TOKEN_ID
          unless token.active
            link = url_for(:controller => :keys, :only_path => true)
            text = t('application.softtoken_pin_not_entered')

            @alerts << {
              :link => "<a href=\"#{link}\">#{text}</a>"
            }
          end
          break
        end
      end
    end

    render :json => {
      :alerts => @alerts
    }
  end

  def set_locale
    audit_log("Set UI language", audit_log_data = {})

    unless I18n.available_locales.include?(params[:locale].to_sym)
      raise "invalid locale"
    end

    audit_log_data[:locale] = params[:locale]

    ui_user = UiUserDAOImpl.getUiUser(current_user.name)

    unless ui_user
      ui_user = UiUserType.new
      ui_user.username = current_user.name
    end

    ui_user.locale = params[:locale]

    @session.save(ui_user)

    render :nothing => true
  end

  private

  def render(*args)
    if @tx && @tx.isActive && !@tx.wasCommitted
      logger.debug("committing transaction")
      @tx.commit
    end

    execute_after_commit_actions

    # Everything that can fail has been done,
    # now let's do the actual rendering.
    super
  end

  def transaction
    if @tx && @tx.isActive && !@tx.wasCommitted
      yield
      return
    end

    reset_transaction_callbacks

    begin
      begin
        logger.debug("beginning transaction")
        @session = ServerConfDatabaseCtx.getSession
        @tx = @session.beginTransaction
        set_transaction_variables

      rescue Java::java.lang.Exception
        logger.error(ExceptionUtils.getStackTrace($!))

        raise t('application.database_connection_error')
      end

      yield

      if @tx.isActive && !@tx.wasCommitted
        logger.debug("committing transaction")
        @tx.commit
      end
    rescue Exception, Java::java.lang.Exception
      logger.error("Error while executing in transaction: #{$!.message}")

      begin
        logger.debug("rolling back transaction")
        @tx.rollback
      rescue Exception, Java::java.lang.Exception
        logger.error("Error rolling back transaction: #{$!.message}")
      end

      @tx = nil

      if $!.java_kind_of?(Java::org.hibernate.exception.GenericJDBCException) &&
          ExceptionUtils.getRootCause($!).java_kind_of?(Java::java.io.EOFException)
        raise t('application.database_connection_error')
      end

      raise $!
    end
  end

  # Passes the required variables to the database engine if supported.
  # Expects the session is available in @session.
  def set_transaction_variables
    sessionFactory = HibernateUtil.getSessionFactory("serverconf")
    dialect = sessionFactory.getDialect
    if dialect.class == Java::EeRiaXroadCommonDb::CustomPostgreSQLDialect
      # If we are running on top of Postgres, the name of the logged-in
      # user must be made available within the transaction, for use
      # when updating the history table.
      # The value of user_name will go out of scope when the transaction
      # ends.
      query = @session.createSQLQuery(
        "SET LOCAL xroad.user_name='#{current_user.name}'")
      query.executeUpdate()
    end
  end

  def check_conf
    redirect_to :controller => :init unless initialized?
  end

  def initialized?
    globalconf_initialized? &&
      serverconf_initialized? &&
      software_token_initialized?
  end

  def globalconf_initialized?
    conf_anchor_file = SystemProperties::getConfigurationAnchorFile()
    logger.debug("Checking existence of configuration anchor file "\
        "'#{conf_anchor_file}'")

    File.exists?(conf_anchor_file)
  end

  def serverconf_initialized?
    serverconf &&
      serverconf.owner &&
      serverconf.serverCode
  end

  def serverconf
    serverconf_dao = ServerConfDAOImpl.new

    if !@serverconf && serverconf_dao.confExists
      @serverconf = serverconf_dao.getConf
    end

    @serverconf
  end

  def serverconf_save(serverconf = @serverconf)
    ServerConfDatabaseCtx.session.saveOrUpdate(serverconf)
  end

  def owner_identifier
    @owner_identifier ||= serverconf.owner.identifier
  end

  def xroad_instance
    GlobalConf::getInstanceIdentifier
  end

  def read_server_id
    return @server_id if @server_id
    return unless serverconf && serverconf.owner

    owner = owner_identifier
    server_code = serverconf.serverCode

    @server_id = SecurityServerId.create(
      owner.xRoadInstance, owner.memberClass,
      owner.memberCode, server_code)
  end

  def read_owner_name
    return @owner_name if @owner_name
    return unless serverconf

    id = owner_identifier
    @owner_name = get_member_name(id.memberClass, id.memberCode)
  end

  def import_services
    if xroad_promoted?
      logger.info("XROAD promoted, skipping services import")
      return
    end

    if importer = SystemProperties::getServiceImporterCommand
      logger.info("Importing services from 5.0 to XROAD")

      output = %x["#{importer}" 2>&1]

      if $?.exitstatus != 0
        logger.error(output)
        error(t('application.services_import_failed'))
      end
    else
      logger.warn("Service importer unspecified, skipping import")
    end
  end

  def export_services(delete_client_id = nil)
    unless xroad_promoted?
      logger.info("XROAD not promoted, skipping services export")
      return
    end

    if exporter = SystemProperties::getServiceExporterCommand
      logger.info("Exporting services from XROAD to 5.0")

      if delete_client_id
        if delete_client_id.subsystemCode
          subsystemCode = CryptoUtils.encodeBase64(
              delete_client_id.subsystemCode)
        else
          subsystemCode = ""
        end

        delete = "-delete " + [
          CryptoUtils.encodeBase64(delete_client_id.xRoadInstance),
          CryptoUtils.encodeBase64(delete_client_id.memberClass),
          CryptoUtils.encodeBase64(delete_client_id.memberCode),
          subsystemCode
        ].join(',')
      end

      output = %x["#{exporter}" "#{delete}" 2>&1]

      if $?.exitstatus != 0
        logger.error(output)
        error(t('application.services_export_failed'))
      end
    else
      logger.warn("Service exporter unspecified, skipping")
    end
  end

  def export_internal_ssl
    unless xroad_promoted?
      logger.info("XROAD not promoted, skipping SSL key export")
      return
    end

    if exporter = SystemProperties::getInternalSslExporterCommand
      output = %x["#{exporter}" 2>&1]

      if $?.exitstatus != 0
        logger.error(output)
        error(t('application.internal_ssl_export_failed'))
      end
    else
      logger.warn("Internal SSL exporter unspecified, skipping")
    end
  end

  def restart_service(name)
    output = %x[sudo restart #{name} 2>&1]

    if $?.exitstatus != 0
      error(t('application.restart_service_failed',
              :name => name, :output => output))
    end
  end

  def demote_xroad
    if xroad_dependent?
      current_user.privileges -= XROAD_PROMOTED_PRIVILEGES
    end
  end

  def x55_installed?
    @x55_installed ||= File.exists?(XROAD_INSTALLED_FILE)
  end

  def xroad_activated?
    activated = File.exists?(XROAD_ACTIVATED_FILE)
    logger.debug("XROAD activated = #{activated}")
    activated
  end

  def xroad_dependent?
    dependent = x55_installed? && !xroad_promoted?
    logger.debug("XROAD dependent = #{dependent}")
    dependent
  end

  def xroad_promoted?
    promoted = File.exists?(XROAD_PROMOTED_FILE)
    logger.debug("XROAD promoted = #{promoted}")
    promoted
  end

  def export_cert(cert)
    gz = 'certs.tar.gz'

    Dir.mktmpdir do |dir|
      open("#{dir}/cert.cer", "wb") do |f|
        f.print(cert.to_der)
      end

      open("#{dir}/cert.pem", "wb") do |f|
        f.print(cert.to_pem)
      end

      system("tar -zcvf /tmp/#{gz} --directory=/tmp -C #{dir} .")
    end

    file = File.open("/tmp/#{gz}", 'rb')
    file.read
  end

  def read_internal_ssl_cert
    cert = nil

    if File.exists?(INTERNAL_SSL_CERT_PATH)
      File.open(INTERNAL_SSL_CERT_PATH, 'rb') do |f|
        cert = OpenSSL::X509::Certificate.new(f)
      end
    end

    cert
  end

  def temp_anchor_file
    CommonUi::IOUtils.temp_file(
      "/#{params[:controller]}_anchor_#{request.session_options[:id]}")
  end

  def save_temp_anchor_file(content)
    File.open(temp_anchor_file, 'wb') do |file|
      file.write(content)
    end

    get_temp_anchor_details
  end

  def get_temp_anchor_details
    # TODO: add constructor for byte[]
    begin
      anchor = ConfigurationAnchor.new(temp_anchor_file)
      generated_at = Time.at(anchor.getGeneratedAt.getTime / 1000).utc
    rescue
      log_stacktrace($!)
      raise t("application.invalid_anchor_file")
    end

    content = IO.binread(temp_anchor_file)

    hash_algorithm = CryptoUtils::DEFAULT_ANCHOR_HASH_ALGORITHM_ID
    hash = CryptoUtils::hexDigest(hash_algorithm, content.to_java_bytes)

    return {
      :hash => hash.upcase.scan(/.{1,2}/).join(':'),
      :hash_algorithm => hash_algorithm,
      :generated_at => format_time(generated_at, true)
    }
  end

  def apply_temp_anchor_file
    unless File.exists?(temp_anchor_file)
      raise "Could not find temporary anchor file"
    end

    CommonUi::ScriptUtils.verify_internal_configuration(temp_anchor_file)
    File.rename(temp_anchor_file, SystemProperties::getConfigurationAnchorFile)

    download_configuration
  end

  def download_configuration
    logger.info("Starting globalconf download")

    port = SystemProperties::getConfigurationClientPort() + 1
    uri = URI("http://localhost:#{port}/execute")

    begin
      response = Net::HTTP.get_response(uri)
    rescue
      log_stacktrace($!)
      raise t('application.configuration_download_failed', :response => $!.message)
    end

    if response.code == '500'
      logger.error(response.body)
      raise t('application.configuration_download_failed', :response => response.body)
    end
  end

  def get_identifier(id)
    return nil unless id

    IdentifierDAOImpl.getIdentifier(id) || id
  end

  def get_member_name(member_class, member_code)
    if !member_class.blank? && !member_code.blank?
      return GlobalConf::getMemberName(
        ClientId.create(xroad_instance, member_class, member_code, nil))
    else
      return nil
    end
  end

  def read_locale
    return unless current_user

    transaction do
      ui_user = UiUserDAOImpl.getUiUser(current_user.name)
      I18n.locale = ui_user.locale if ui_user
    end
  end
end
