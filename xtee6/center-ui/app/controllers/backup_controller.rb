class BackupController < BaseBackupController

  skip_around_filter :wrap_in_transaction, :only => [:restore]

  upload_callbacks({
    :upload_new => "XROAD_BACKUP.uploadCallback"
  })

  private

  def before_restore
    ActiveRecord::Base.remove_connection
  end

  def after_restore
    ActiveRecord::Base.establish_connection
  end

  def after_restore_success
  end
end
