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

class ServiceId < Identifier
  validates_presence_of :service_code

    # Constructs a ClientId object from provider and service code.
  def self.from_parts(provider_id, service_code, service_version = nil)
    if !service_code || service_code.empty?
      raise I18n.t("identifiers.service_code_empty")
    end

    ServiceId.new(
        :object_type => "SERVICE",
        :xroad_instance => provider_id.xroad_instance,
        :member_class => provider_id.member_class,
        :member_code => provider_id.member_code,
        :subsystem_code => provider_id.subsystem_code,
        :service_code => service_code,
        :service_version => service_version)
  end

  def to_s
    "#{object_type}:#{xroad_instance}/#{member_class}/#{member_code}#{subsystem_code ? '/' + subsystem_code : ''}/#{service_code}"
  end
end