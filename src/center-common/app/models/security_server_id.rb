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

class SecurityServerId < Identifier
  # Constructs a SecurityServerId object from identifier parts.
  def self.from_parts(xroad_instance, member_class, member_code, server_code)
    SecurityServerId.new(
        :object_type => "SERVER",
        :xroad_instance => xroad_instance,
        :member_class => member_class,
        :member_code => member_code,
        :server_code => server_code)
  end

  def clean_copy
    SecurityServerId.new(
        :object_type => object_type,
        :xroad_instance => xroad_instance,
        :member_class => member_class,
        :member_code => member_code,
        :server_code => server_code)
  end

  def matches_client_id(client_id)
    return xroad_instance == client_id.xroad_instance &&
        member_class == client_id.member_class &&
        member_code == client_id.member_code &&
        client_id.subsystem_code == nil  # Owner cannot be subsystem
  end

  # Returns ClientId corresponding to owner of this security server.
  def owner_id
    ClientId.from_parts(xroad_instance, member_class, member_code)
  end

  def to_s
    "#{object_type}:#{xroad_instance}/#{member_class}/#{member_code}/#{server_code}"
  end
end
