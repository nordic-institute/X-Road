class ClientId < Identifier
  # Constructs a ClientId object from identifier parts.
  def self.from_parts(sdsb_instance, member_class, member_code,
      subsystem_code = nil)
    subsystem_code = nil if !subsystem_code || subsystem_code.empty?

    ClientId.new(
        :object_type => subsystem_code ? "SUBSYSTEM" : "MEMBER",
        :sdsb_instance => sdsb_instance,
        :member_class => member_class,
        :member_code => member_code,
        :subsystem_code => subsystem_code)
  end

  def clean_copy
    ClientId.new(
        :object_type => object_type,
        :sdsb_instance => sdsb_instance,
        :member_class => member_class,
        :member_code => member_code,
        :subsystem_code => subsystem_code)
  end

  def to_s
    "#{object_type}:#{sdsb_instance}/#{member_class}/#{member_code}#{subsystem_code ? '/' + subsystem_code : ''}"
  end
end
