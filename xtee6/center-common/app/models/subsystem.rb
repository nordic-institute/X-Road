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

class Subsystem < SecurityServerClient
  validates_presence_of :xroad_member_id
  validates_presence_of :subsystem_code

  belongs_to :xroad_member,
      :class_name => "XroadMember",
      :foreign_key => "xroad_member_id"

  def name
    self.xroad_member.name
  end

  # Finds a subsystem by member class, member code and subsystem code.
  # Returns nil, if not found.
  def self.find_by_code(member_class, member_code, subsystem_code)
    subsystems = Subsystem\
        .joins(:xroad_member => :member_class)\
        .where(
          :subsystem_code => subsystem_code,
          :xroad_members_security_server_clients => {
            :member_code => member_code},
          :member_classes => {
            :code => member_class})
    if subsystems.empty? then nil else subsystems[0] end
  end

  private

  def save_identifier(record)
    xroad_member = record.xroad_member
    identifier = ClientId.from_parts(
        SystemParameter.instance_identifier,
        xroad_member.member_class.code,
        xroad_member.member_code,
        record.subsystem_code)
    record.server_client = identifier
    record.save!

    SecurityServerClientName.create!(
        :name => xroad_member.name,
        :client_identifier_id => identifier.id)
  end

  def update_name_mapping(record)
    mapping = SecurityServerClientName.
        where(:client_identifier_id => record.server_client.id).first

    if mapping && record.xroad_member.changed?
      mapping.update_attributes!(:name => record.xroad_member.name)
    end
  end
end
