require 'base64'

java_import Java::ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx
java_import Java::ee.ria.xroad.common.identifier.SecurityServerId

class BackupController < BaseBackupController

  BACKUP_SCRIPT_NAME = "backup_xroad_proxy_configuration.sh"
  RESTORE_SCRIPT_NAME = "restore_xroad_proxy_configuration.sh"

  skip_around_filter :transaction, :only => [:restore]
  skip_before_filter :check_conf, :read_server_id, :read_owner_name, :only => [:restore]

  upload_callbacks({
    :upload_new => "XROAD_BACKUP.uploadCallback"
  })

  def backup_script_name
    return BACKUP_SCRIPT_NAME
  end

  def restore_script_name
    return RESTORE_SCRIPT_NAME
  end

  def backup_script_options
    return backup_restore_script_options
  end

  def restore_script_options
    return backup_restore_script_options
  end

  private

  def before_restore
    SignerProxy::getTokens.each do |token|
      if token.id != SignerProxy::SSL_TOKEN_ID
        @hardware_tokens_exist = true
        break
      end
    end
  rescue
  end

  def after_restore
    ServerConfDatabaseCtx.get.closeSessionFactory
  end

  def after_restore_success
    @extra_data = {
      :hardware_tokens_exist => !!@hardware_tokens_exist
    }
  end

  def backup_restore_script_options
    script_options = []
    transaction do
      owner = serverconf.owner.identifier
      server_code = serverconf.serverCode
      server_id = SecurityServerId.create(
        owner.xRoadInstance, owner.memberClass,
        owner.memberCode, server_code)
      # Send input in base64 because we have a problem with passing parameters
      # using spaces.
      script_options << "-b"
      script_options << "-s" << "#{Base64.strict_encode64(server_id.toShortString)}"
    end
    return script_options
  end

end
