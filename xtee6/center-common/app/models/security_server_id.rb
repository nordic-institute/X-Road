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
