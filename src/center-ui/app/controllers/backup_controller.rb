#
# The MIT License
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

require 'base64'

class BackupController < BaseBackupController

  BACKUP_SCRIPT_NAME = "backup_xroad_center_configuration.sh"
  RESTORE_SCRIPT_NAME = "restore_xroad_center_configuration.sh"

  skip_around_filter :wrap_in_transaction, :only => [:restore]
  skip_before_filter :check_conf, :only => [:restore]

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
    ActiveRecord::Base.remove_connection
  end

  def after_restore
    ActiveRecord::Base.establish_connection
  end

  def after_restore_success
    # NOP
  end

  def backup_restore_script_options
    script_options = []
    wrap_in_transaction do
      # Send input in base64 because we have a problem with passing parameters
      # using spaces.
      script_options << "-b"
      script_options << "-i" <<
          "#{Base64.strict_encode64(SystemParameter.instance_identifier)}"
      if CommonSql.ha_configured?
        script_options << "-n" << "#{Base64.strict_encode64(CommonSql.ha_node_name)}"
      end
    end
    return script_options
  end

end
