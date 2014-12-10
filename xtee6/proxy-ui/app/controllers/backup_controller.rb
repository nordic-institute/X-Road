java_import Java::ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx

class BackupController < BaseBackupController

  skip_around_filter :transaction, :only => [:restore]
  skip_before_filter :check_conf, :read_server_id, :read_owner_name, :only => [:restore]

  private

  def before_restore
  end

  def after_restore
    ServerConfDatabaseCtx.get.closeSessionFactory
  end
end
