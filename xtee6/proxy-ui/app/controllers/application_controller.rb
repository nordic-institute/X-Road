require "net/http"
require "conf_helper"
require "management_request_helper"

java_import Java::java.lang.System
java_import Java::ee.cyber.sdsb.common.SystemProperties
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.common.identifier.SecurityServerId

class ApplicationController < BaseController

  SDSB_INSTALLED_FILE = "/usr/xtee/etc/sdsb_installed"
  SDSB_PROMOTED_FILE = "/usr/xtee/etc/sdsb_promoted"

  SDSB_PROMOTED_PRIVILEGES = [
    :send_client_del_req,
    :delete_client,
    :add_wsdl,
    :enable_disable_wsdl,
    :refresh_wsdl,
    :delete_wsdl_missing_services,
    :delete_wsdl,
    :edit_service_params,
    :edit_service_acl,
    :import_export_service_acl,
    :edit_subject_open_services,
    :import_export_subject_acl
  ]

  include ConfHelper
  include ManagementRequestHelper

  before_filter :demote_sdsb
  before_filter :check_conf, :except => :menu
  before_filter :read_server_id, :except => :menu

  # Workaround to make lib directory auto-loadable. TODO - is there better way
  # to do this? XXX Comment it out when building war!

#    Dir["lib/**/*.rb"].each do |path|
#      require_dependency path
#    end

  def index
  end

  private

  def check_conf
    unless globalconf.exists? && serverconf.exists? && serverconf.root.owner &&
        !serverconf.root.globalConfDistributor.empty

      redirect_to :controller => :init
    end
  rescue Java::java.lang.Exception
    render_java_error($!)
  end

  def serverconf
    @serverconf ||= Conf.new(SystemProperties::getServerConfFile(),
      Java::ee.cyber.sdsb.common.conf.serverconf.ObjectFactory)
  end

  def globalconf
    @globalconf ||= Conf.new(SystemProperties::getGlobalConfFile(),
      Java::ee.cyber.sdsb.common.conf.globalconf.ObjectFactory)
  end

  def conf_changed
    proxy_reload_uri = URI("http://127.0.0.1:5566/reload")
    Net::HTTP.get(proxy_reload_uri)
  rescue Exception
    logger.error("Proxy conf reload failed: #{$!.message}")
  end

  def read_server_id
    return unless serverconf.exists?
    return @server_id if @server_id

    owner = owner_identifier
    server_code = serverconf.root.serverCode

    @server_id = SecurityServerId.create(
      owner.sdsbInstance, owner.memberClass,
      owner.memberCode, server_code)
  end

  def owner_identifier
    serverconf.root.owner.identifier
  end

  ##
  # Converts ClientId to it's globalconf member id, by omitting the
  # subsystem code.
  #
  def to_member_id(client_id)
    ClientId.create(client_id.sdsbInstance,
      client_id.memberClass, client_id.memberCode, nil)
  end

  ##
  # Converts a globalconf member (or a reference to member) to ClientId.
  #
  def globalconf_member_to_client_id(globalconf_member)
    if globalconf_member.java_kind_of?(Java::javax.xml.bind.JAXBElement)
      globalconf_member = globalconf_member.getValue
    end

    if globalconf_member.java_kind_of?(
        Java::ee.cyber.sdsb.common.conf.globalconf.MemberType)
      return ClientId.create(
        globalconf.root.instanceIdentifier,
        globalconf_member.memberClass,
        globalconf_member.memberCode, nil)
    else
      globalconf.root.member.each do |member|
        member.subsystem.each do |subsystem|
          if subsystem.id == globalconf_member.id
            return ClientId.create(
              globalconf.root.instanceIdentifier,
              member.memberClass, member.memberCode,
              subsystem.subsystemCode)
          end
        end
      end
    end
  end

  ##
  # Extracts SecurityServerId from globalconf's SecurityServerType.
  #
  def extract_server_id(server)
    owner_id = globalconf_member_to_client_id(server.owner)

    SecurityServerId.create(
      owner_id.sdsbInstance, owner_id.memberClass,
      owner_id.memberCode, server.serverCode)
  end

  def import_services
    if sdsb_promoted?
      logger.info("SDSB promoted, skipping services import")
      return 
    end

    if importer = SystemProperties::getServiceImporterCommand
      logger.info("Importing services from 5.0 to SDSB")

      output = %x["#{importer}" 2>&1]

      if $?.exitstatus != 0
        error("Failed to import services")
        logger.error(output)
      end
    else
      logger.warn("Service importer unspecified, skipping import")
    end
  end

  def export_services
    unless sdsb_promoted?
      logger.info("SDSB not promoted, skipping services export")
      return 
    end

    if exporter = SystemProperties::getServiceExporterCommand
      logger.info("Exporting services from SDSB to 5.0")

      output = %x["#{exporter}" 2>&1]

      if $?.exitstatus != 0
        logger.warn(output)
        output = output[-200, 200] if output.length > 200
        error("Failed to export services: #{output}")
      end
    else
      logger.warn("Service exporter unspecified, skipping export")
    end
  end

  def demote_sdsb
    if sdsb_demoted?
      current_user.privileges -= SDSB_PROMOTED_PRIVILEGES
    end
  end

  def sdsb_demoted?
    demoted = File.exists?(SDSB_INSTALLED_FILE) && !File.exists?(SDSB_PROMOTED_FILE)
    logger.debug("SDSB demoted = #{demoted}")
    demoted
  end

  def sdsb_promoted?
    promoted = File.exists?(SDSB_PROMOTED_FILE)
    logger.debug("SDSB promoted = #{promoted}")
    promoted
  end
end
