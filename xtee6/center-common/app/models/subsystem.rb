class Subsystem < SecurityServerClient
  validates_presence_of :sdsb_member_id
  validates_presence_of :subsystem_code

  belongs_to :sdsb_member,
      :class_name => "SdsbMember",
      :foreign_key => "sdsb_member_id"

  # Finds a subsystem by member class, member code and subsystem code.
  # Returns nil, if not found.
  def self.find_by_code(member_class, member_code, subsystem_code)
    subsystems = Subsystem\
        .joins(:sdsb_member => :member_class)\
        .where(
          :subsystem_code => subsystem_code,
          :sdsb_members_security_server_clients => {
            :member_code => member_code},
          :member_classes => {
            :code => member_class})
    if subsystems.empty? then nil else subsystems[0] end
  end

  private

  def save_identifier(record)
    sdsb_member = record.sdsb_member
    identifier = ClientId.from_parts(
        SystemParameter.instance_identifier,
        sdsb_member.member_class.code,
        sdsb_member.member_code,
        record.subsystem_code)
    record.server_client = identifier
    record.save!

    SecurityServerClientName.create!(
        :name => sdsb_member.name,
        :client_identifier_id => identifier.id)
  end

  def update_name_mapping(record)
    mapping = SecurityServerClientName.
        where(:client_identifier_id => record.server_client.id).first

    if mapping && record.sdsb_member.changed?
      mapping.update_attributes!(:name => record.sdsb_member.name)
    end
  end
end
