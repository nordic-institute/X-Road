require 'uri'

class SecurityServer < ActiveRecord::Base
  include Validators

  class DuplicateSecurityServerValidator < ActiveModel::Validator
    def validate(new_record)
      sdsb_member = SdsbMember.find(new_record.sdsb_member_id)

      server_code = new_record.server_code
      member_code = sdsb_member.member_code
      member_class_code = sdsb_member.member_class.code

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
    puts "find_server_by_id(#{server_id})"
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

  validates :sdsb_member_id, :present => true
#  In principle, client servers can share address.
#  validates :address, :unique => true # Addresses are not URIs, :uri => true
  validates_with DuplicateSecurityServerValidator, :on => :create

  belongs_to :owner, :class_name => "SdsbMember", :foreign_key => "sdsb_member_id"

  # TODO: if server is removed, owner should be removed
  # from the server owners global group.
  has_and_belongs_to_many :security_categories,
      join_table: "security_servers_security_categories"
  has_and_belongs_to_many :security_server_clients,
      join_table: "server_clients"
  has_many :auth_certs

  before_destroy do |record|
    register_client_deletion_requests(record)
    clear_server_auth_certs(record)
    record.security_categories.clear
    record.security_server_clients.clear
    update_request_owner_names(record)
  end

  def get_identifier
    "SERVER:#{SystemParameter.sdsb_instance}/#{owner.member_class.code}/"\
        "#{owner.member_code}/#@server_code"
  end

  def get_server_id
    SecurityServerId.from_parts(
      SystemParameter.sdsb_instance,
      owner.member_class.code,
      owner.member_code,
      server_code)
  end

  def self.get_servers(query_params)
    get_search_relation(query_params.search_string).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_server_count(searchable)
    searchable.empty? ?
        SecurityServer.count :
        get_search_relation(searchable).count
  end

  private

  def self.get_search_relation(searchable)

    search_params = get_search_sql_params(searchable)

    SecurityServer.
        joins(:owner => :member_class).
        where(get_search_sql, *search_params)
  end

  def self.get_search_sql
    "lower(security_servers.server_code) LIKE ?
    OR lower(security_server_clients.name) LIKE ?
    OR lower(member_classes.code) LIKE ?
    OR lower(security_server_clients.member_code) LIKE ?"
  end

  def self.get_search_sql_params(searchable)
    ["%#{searchable}%", "%#{searchable}%", "%#{searchable}%", "%#{searchable}%"]
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

  def clear_server_auth_certs(security_server)
    server_id = security_server.get_server_id()
    comment = "'#{server_id}' deletion"

    security_server.auth_certs.each do |each|
      request = AuthCertDeletionRequest.new(
        :security_server => server_id,
        :auth_cert => each.certificate,
        :origin => Request::CENTER).register()
    end
  end

  # -- Before destroy operations - end ---
end
