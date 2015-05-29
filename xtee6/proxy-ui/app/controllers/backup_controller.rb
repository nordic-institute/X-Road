java_import Java::ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx

class BackupController < BaseBackupController

  skip_around_filter :transaction, :only => [:restore]
  skip_before_filter :check_conf, :read_server_id, :read_owner_name, :only => [:restore]

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
      :activate_hardware_tokens => !!@hardware_tokens_exist
    }
  end
end
