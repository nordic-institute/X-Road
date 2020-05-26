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
