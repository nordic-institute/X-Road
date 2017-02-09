#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

module SecurityserversHelper

  private

  def get_all_servers_as_json
    servers = SecurityServer.find(:all)
    servers_as_json = []

    servers.each do |server|
      servers_as_json << get_full_server_data_as_json(server)
    end

    servers_as_json
  end

  def get_full_server_data_as_json(server)
    return {
      :server_code => server.server_code,
      :owner_name => server.owner.name,
      :owner_class => server.owner.member_class.code,
      :owner_code => server.owner.member_code,
      :registered => format_time(server.created_at.localtime),
      :address => server.address,
      :identifier => server.get_identifier
    }
  end

  def get_short_server_data_as_json(server)
    return {
      :server_code => server.server_code,
      :owner_name => server.owner.name,
      :owner_class => server.owner.member_class.code,
      :owner_code => server.owner.member_code
    }
  end
end
