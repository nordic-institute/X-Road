class ServiceId < Identifier
  include Validators

  validates :service_code, :present => true

    # Constructs a ClientId object from provider and service code.
  def self.from_parts(provider_id, service_code)
    if !service_code || service_code.empty?
      raise I18n.t("identifiers.service_code_empty")
    end

    ServiceId.new(
        :object_type => "SERVICE",
        :sdsb_instance => provider_id.sdsb_instance,
        :member_class => provider_id.member_class,
        :member_code => provider_id.member_code,
        :subsystem_code => provider_id.subsystem_code,
        :service_code => service_code)
  end

  def to_s
    "#{object_type}:#{sdsb_instance}/#{member_class}/#{member_code}#{subsystem_code ? '/' + subsystem_code : ''}/#{service_code}"
  end
end