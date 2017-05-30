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

require 'uri'

class SecurityServer < ActiveRecord::Base

  class DuplicateSecurityServerValidator < ActiveModel::Validator
    def validate(new_record)
      xroad_member = XroadMember.find(new_record.owner_id)

      server_code = new_record.server_code
      member_code = xroad_member.member_code
      member_class_code = xroad_member.member_class.code

      potentially_existing_server =
        SecurityServer.find_server(server_code, member_code, member_class_code)

      if potentially_existing_server
        raise I18n.t("validation.securitserver_exists",
                  :member_class => member_class.code,
                  :member_code => member_code,
                  :server_code => server_code)
      end
    end
  end

  class UriValidator < ActiveModel::EachValidator
    def validate_each(record, attribute, value)
      validationResult = value =~ URI.regexp

      unless validationResult
        raise I18n.t("validation.invalid_uri",
            :attribute => attribute, :uri => value)
      end
    end
  end

  # Finds server by given ServerId.
  def self.find_server_by_id(server_id)
    Rails.logger.info("find_server_by_id(#{server_id})")

    find_server(
        server_id.server_code,
        server_id.member_code,
        server_id.member_class)
  end

  # Finds server by name components.
  def self.find_server(server_code, member_code, member_class_code)
    potential_servers = SecurityServer.where(:server_code => server_code)

    potential_servers.find do|server|
      owner = server.owner
      owner.member_code == member_code &&
          owner.member_class.code == member_class_code
    end
  end

  validates_with Validators::MaxlengthValidator
  validates_presence_of :owner_id
  validates_with DuplicateSecurityServerValidator, :on => :create

  belongs_to :owner, :class_name => "XroadMember", :foreign_key => "owner_id"

  has_and_belongs_to_many :security_categories,
      :join_table => "security_servers_security_categories"
  has_and_belongs_to_many :security_server_clients,
      :join_table => "server_clients"
  has_many :auth_certs

  before_destroy do |record|
    register_client_deletion_requests(record)
    clear_server_auth_certs(record)
    record.security_categories.clear
    record.security_server_clients.clear
    update_request_owner_names(record)
    update_server_owners_group(record)
  end

  def get_identifier
    "SERVER:#{SystemParameter.instance_identifier}/#{owner.member_class.code}/"\
        "#{owner.member_code}/#{self.server_code}"
  end

  def get_server_id
    SecurityServerId.from_parts(
      SystemParameter.instance_identifier,
      owner.member_class.code,
      owner.member_code,
      server_code)
  end

  def self.get_servers(query_params)
    return get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_server_count(searchable = "")
    return get_search_relation(searchable).count
  end

  # Server is hash including keys :member_class, :member_code and :server_code.
  def self.get_management_requests(server, query_params)
    return get_management_requests_relation(server).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_management_requests_count(server)
    return get_management_requests_relation(server).count()
  end

  private

  def self.get_search_relation(searchable)
    sql_generator = searchable.is_a?(AdvancedSearchParams) ?
        AdvancedSearchSqlGenerator.new(map_advanced_search_params(searchable)):
        SimpleSearchSqlGenerator.new(get_searchable_columns, searchable)

    SecurityServer.
        joins(:owner => :member_class).
        where(sql_generator.sql, *sql_generator.params)
  end

  def self.get_management_requests_relation(server)
    return Request.
        joins(:security_server).
        where(:identifiers => server)
  end

  def self.map_advanced_search_params(searchable)
    return {
        "member_classes.code" => searchable.member_class,
        "security_server_clients.name" => searchable.name,
        "security_server_clients.member_code" => searchable.member_code,
        "security_servers.server_code" => searchable.server_code
    }
  end

  def self.get_searchable_columns
    return [
      "security_servers.server_code",
      "security_server_clients.name",
      "member_classes.code",
      "security_server_clients.member_code"
    ]
  end

  # -- Before destroy operations - start ---

  def register_client_deletion_requests(security_server)
    owner = security_server.owner

    security_server.security_server_clients.each do |each|
      client_id = each.server_client.clean_copy()
      server_id = security_server.get_server_id()
      comment = "'#{server_id.to_s}' deletion"

      ClientDeletionRequest.new(
       :security_server => server_id,
       :sec_serv_user => client_id,
       :comments => comment,
       :origin => Request::CENTER).register()
    end
  end

  def update_request_owner_names(security_server)
    requests = Request.find_by_server(security_server.get_server_id())

    requests.each do |each|
      each.update_attributes!(:server_owner_name => security_server.owner.name)
    end
  end

  def update_server_owners_group(security_server)
    owner = security_server.owner
    return if owner.owned_servers.size > 1

    GlobalGroup.security_server_owners_group.remove_member(owner.server_client)
  end

  def clear_server_auth_certs(security_server)
    server_id = security_server.get_server_id()
    comment = "'#{server_id}' deletion"

    security_server.auth_certs.each do |each|
      request = AuthCertDeletionRequest.new(
        :security_server => server_id,
        :auth_cert => each.cert,
        :comments => comment,
        :origin => Request::CENTER).register()
    end
  end

  # -- Before destroy operations - end ---
end
