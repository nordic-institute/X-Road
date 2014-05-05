class CentralService < ActiveRecord::Base
  include Validators

  validates :service_code, :unique => true, :present => true

  belongs_to :target_service,
    :class_name => "ServiceId",
    :autosave => true,
    :dependent => :destroy

  def remove_target_service
    if target_service
      target_service.destroy
      update_attributes!(:target_service => nil)
    end
  end

  def self.save(service_code, target_service_code, provider_id)
    logger.info(
        "CentralService.save(#{service_code}, #{target_service_code}, #{provider_id})")

    check_presence_of_provider(target_service_code, provider_id)

    CentralService.create!(
        :service_code => service_code,
        :target_service =>
            get_target_service_id(target_service_code, provider_id)
    )
  end

  def self.update(service_code, target_service_code, provider_id)
    logger.info(
        "CentralService.update(#{service_code}, #{target_service_code}, #{provider_id})")

    service = find_by_code(service_code)

    raise "Service with code '#{service_code}' not found" unless service

    check_presence_of_provider(target_service_code, provider_id)

    service.remove_target_service()

    service.update_attributes!(
        :service_code => service_code,
        :target_service =>
            get_target_service_id(target_service_code, provider_id)
    )
  end

  def self.delete(service_code)
    logger.info("CentralService.delete(#{service_code})")

    get_by_code_relation(service_code).delete_all
  end

  def self.delete_target_service(service_code)
    logger.info("CentralService.delete_target_service(#{service_code})")

    service = find_by_code(service_code)

    service.remove_target_service() if service
  end

  def self.get_central_services(query_params, advanced_search_params = nil)
    logger.info("get_central_services('#{query_params}', '#{advanced_search_params}')")

    searchable = advanced_search_params ? advanced_search_params :
        query_params.search_string

    get_search_relation(searchable).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_service_count(searchable)
    get_search_relation(searchable).count
  end

  private

  def self.find_by_code(service_code)
    get_by_code_relation(service_code).first
  end

  def self.get_by_code_relation(service_code)
    CentralService.where(:service_code => service_code)
  end

  def self.get_search_relation(searchable)
    sql_generator = searchable.is_a?(AdvancedSearchParams) ?
        AdvancedSearchSqlGenerator.new(map_advanced_search_params(searchable)):
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    CentralService.
        where(sql_generator.sql, *sql_generator.params).
        includes(:target_service)
  end

  def self.map_advanced_search_params(searchable)
    {
        "central_services.service_code" => searchable.central_service_code,
        "identifiers.service_code" => searchable.service_code,
        "identifiers.member_class" => searchable.member_class,
        "identifiers.member_code" => searchable.member_code,
        "identifiers.subsystem_code" => searchable.subsystem_code
    }
  end

  def self.get_searchable_columns
    [   "central_services.service_code",
        "identifiers.service_code",
        "identifiers.member_class",
        "identifiers.member_code",
        "identifiers.subsystem_code"
    ]
  end

  def self.get_target_service_id(target_service_code, provider_id)
    target_service_code ?
        ServiceId.from_parts(provider_id, target_service_code):
        nil
  end

  def self.check_presence_of_provider(target_service_code, provider_id)
    if target_service_code && !SecurityServerClient.find_by_id(provider_id)
      raise I18n.t("central_services.provider_not_found",
          :provider_id => provider_id)
    end
  end
end
