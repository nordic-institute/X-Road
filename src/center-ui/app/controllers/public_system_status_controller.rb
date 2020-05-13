#
# The MIT License
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

require 'date'
require 'time'

# This controller contains actions for public requests for the status of
# the system. No authentication is required for making the requests.
# XXX The actions in BaseController are accessible via this controller, too.
class PublicSystemStatusController < BaseController

  def index
    # NOP
  end

  def check_ha_cluster_status
    return render :json => { :ha_node_status => ha_node_status }
  end

  private

  # Return the status information of the HA nodes.
  def ha_node_status
    if !CommonSql.ha_configured?
      return { :ha_configured => false }
    end

    status_info = {
      :ha_configured => true,
      :node_name => CommonSql.ha_node_name,
      :nodes => [],
    }

    all_nodes_ok = true
    conf_expire = SystemParameter.conf_expire_interval_seconds
    now = Time.now
    node_status_result = ActiveRecord::Base.connection.execute(%q(select ha_node_name, address, configuration_generated from ha_cluster_status))

    node_status_result.each do |node_info|
      status = node_status(node_info["configuration_generated"], now, conf_expire)
      if status != "OK"
        all_nodes_ok = false
      end
      status_info[:nodes] << {
        node_name: node_info["ha_node_name"],
        node_address: node_info["address"],
        configuration_generated: node_info["configuration_generated"],
        status: status
      }
    end

    status_info[:all_nodes_ok] = all_nodes_ok

    return status_info
  end

  def node_status(seen, now, conf_expire)
    return "UNKNOWN" if !seen
    diff = now - DateTime.parse(seen).to_time
    return "ERROR" if diff > conf_expire
    # should compare to global configuration generation interval (by default every minute)
    return "WARN" if diff > 70 or diff < 0
    "OK"
  end

end
