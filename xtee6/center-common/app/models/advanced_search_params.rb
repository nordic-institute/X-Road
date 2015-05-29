class AdvancedSearchParams
  attr_reader :name, :xroad_instance, :member_class, :member_code,
      :subsystem_code, :object_type, :service_code, :service_version,
      :central_service_code, :server_code

  def initialize(params = {})
    @name = nil_or_empty?(params[:name]) ?
        nil : params[:name].downcase
    @xroad_instance = nil_or_empty?(params[:xroad_instance]) ?
        nil : params[:xroad_instance].downcase
    @member_class = nil_or_empty?(params[:member_class]) ?
        nil : params[:member_class].downcase
    @member_code = nil_or_empty?(params[:member_code]) ?
        nil : params[:member_code].downcase
    @subsystem_code = nil_or_empty?(params[:subsystem_code]) ?
        nil : params[:subsystem_code].downcase
    @object_type = nil_or_empty?(params[:object_type]) ?
        nil : params[:object_type].downcase
    @service_code = nil_or_empty?(params[:service_code]) ?
        nil : params[:service_code].downcase
    @service_version = nil_or_empty?(params[:service_version]) ?
        nil : params[:service_version].downcase
    @central_service_code = nil_or_empty?(params[:central_service_code]) ?
        nil : params[:central_service_code].downcase
    @server_code = nil_or_empty?(params[:server_code]) ?
        nil : params[:server_code].downcase
  end

  private

  def nil_or_empty?(param)
    param == nil || param.empty?
  end

  def to_s
    string = "AdvancedSearchParams:\n"

    string << "\tName: '#@name'\n" if @name
    string << "\tX-Road instance: '#@xroad_instance'\n" if @xroad_instance
    string << "\tMember class: '#@member_class'\n" if @member_class
    string << "\tMember code: '#@member_code'\n" if @member_code
    string << "\tSubsystem code: '#@subsystem_code'\n" if @subsystem_code
    string << "\tObject type: '#@object_type'\n" if @object_type
    string << "\tService code: '#@service_code'\n" if @service_code
    string << "\tService version: '#@service_version'\n" if @service_version
    string << "\tCentral service code: '#@central_service_code'\n" if @central_service_code
    string << "\tServer code: '#@server_code'\n" if @server_code

    string
  end
end
