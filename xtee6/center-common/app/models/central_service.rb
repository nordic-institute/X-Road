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

class CentralService < ActiveRecord::Base
  validates_with Validators::MaxlengthValidator
  validates :service_code, :uniqueness => true, :presence => true

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

  # Target service is hash consisting of :code and :version.
  def self.save(service_code, target_service, provider_id)
    logger.info(
        "CentralService.save(#{service_code}, #{target_service}, #{provider_id})")

    target_service_code = get_target_service_code(target_service)
    check_presence_of_provider(target_service_code, provider_id)

    CentralService.create!(
        :service_code => service_code,
        :target_service =>
            get_target_service_id(target_service, provider_id)
    )
  end

  # Target service is hash consisting of :code and :version.
  def self.update(service_code, target_service, provider_id)
    logger.info(
        "CentralService.update(#{service_code}, #{target_service}, #{provider_id})")

    service = find_by_code(service_code)

    raise "Service with code '#{service_code}' not found" unless service

    target_service_code = get_target_service_code(target_service)
    check_presence_of_provider(target_service_code, provider_id)

    service.remove_target_service()

    service.update_attributes!(
        :service_code => service_code,
        :target_service =>
            get_target_service_id(target_service, provider_id)
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

  def self.get_central_services(query_params)
    logger.info("get_central_services('#{query_params}')")

    get_search_relation(query_params.search_string).
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
    sql_generator =
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    CentralService.
        where(sql_generator.sql, *sql_generator.params).
        includes(:target_service)
  end

  def self.get_searchable_columns
    [   "central_services.service_code",
        "identifiers.service_code",
        "identifiers.service_version",
        "identifiers.member_class",
        "identifiers.member_code",
        "identifiers.subsystem_code"
    ]
  end

  def self.get_target_service_id(target_service, provider_id)
    target_service_code = get_target_service_code(target_service)
    target_service_version = get_target_service_version(target_service)

    return target_service_code ?
        ServiceId.from_parts(
            provider_id, target_service_code, target_service_version):
        nil
  end

  def self.get_target_service_code(target_service)
    Rails.logger.info(target_service.inspect)
    return target_service.is_a?(Hash) ? target_service[:code] : nil
  end

  def self.get_target_service_version(target_service)
    Rails.logger.info(target_service.inspect)
    return target_service.is_a?(Hash) ? target_service[:version] : nil
  end

  def self.check_presence_of_provider(target_service_code, provider_id)
    if target_service_code && !SecurityServerClient.find_by_id(provider_id)
      raise I18n.t("central_services.provider_not_found",
          :provider_id => provider_id)
    end
  end
end
