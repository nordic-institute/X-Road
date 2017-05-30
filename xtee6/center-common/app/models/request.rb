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

class Request < ActiveRecord::Base

  before_create do |rec|
    Request.set_server_owner_name(rec)

    server = rec.security_server

    return 1 unless server # To keep following callbacks running

    rec.server_owner_class = server.member_class
    rec.server_owner_code = server.member_code
    rec.server_code = server.server_code
  end

  # origin -- submitted by security server
  SECURITY_SERVER = "SECURITY_SERVER"
  # origin -- entered from the central server user interface
  CENTER = "CENTER"

  validates_with Validators::MaxlengthValidator

  belongs_to :security_server,
    :class_name => "SecurityServerId",
    :autosave => true,
    :foreign_key => "security_server_id",
    :dependent => :destroy
  belongs_to :sec_serv_user,
    :class_name => "ClientId",
    :autosave => true,
    :foreign_key => "sec_serv_user_id",
    :dependent => :destroy
  belongs_to :request_processing

  # Save the request and perform the corresponding actions.
  # This method can be overriden in child class.
  def register()
    # Verify correctness of the request
    verify_origin()
    verify_request()

    # Execute the action associated with the request
    execute()

    save!
  end

  # Checks that the origin is present and is either CENTER or SECURITY_SERVER
  def verify_origin()
    if origin == nil
      raise "Origin must be present"
    end
    if origin != CENTER && origin != SECURITY_SERVER
      raise "Origin must be either #{CENTER} or #{SECURITY_SERVER}"
    end
  end

  # Performs verification specific to the current request.
  # Child classes can override this method.
  def verify_request()
    # No validation is done in the base class
  end

  # Throws exception is security server client with client_id does not exist
  def require_client(client_id)
    if SecurityServerClient.find_by_id(client_id) == nil
      raise I18n.t("requests.client_not_found",
          :client => client_id.to_s)
    end
  end

  # Throws exception is security server with server_id does not exist
  def require_security_server(server_id)
    if SecurityServer.find_server_by_id(server_id) == nil
      raise I18n.t("requests.server_not_found",
          :server => server_id.to_s)
    end
  end

  def from_center?
    origin == CENTER
  end

  # Perform the action associated with the request.
  def execute()
    throw "This method must be reimplemented in a subclass"
  end

  def get_complementary_id()
    complementary_id = ""

    if has_processing?()
      other_request = request_processing.get_other_request(self);
      complementary_id = other_request.id if other_request
    end

    complementary_id
  end

  def get_status()
    has_processing?() ? request_processing.status : ""
  end

  def get_revoking_request_id()
    nil
  end

  def has_processing?
    respond_to?(:request_processing) && request_processing
  end

  def update_server_owner_name(current_server_owner_name)
    return if self.server_owner_name == current_server_owner_name

    update_attributes!(:server_owner_name => current_server_owner_name)
  end

  def update_server_user_name(current_server_user_name)
    return if self.server_user_name == current_server_user_name

    update_attributes!(:server_user_name => current_server_user_name)
  end

  # Static database-related methods

  def self.get_requests(query_params, converted_search_params = [])
    logger.info("get_requests('#{query_params}', '#{converted_search_params}'")

    get_search_relation(query_params.search_string, converted_search_params).
        order("#{query_params.sort_column} #{query_params.sort_direction}").
        limit(query_params.display_length).
        offset(query_params.display_start)
  end

  def self.get_request_count(searchable = "", converted_search_params = [])
    searchable.empty? && converted_search_params.empty? ?
        Request.count :
        get_search_relation(searchable, converted_search_params).count
  end

  def self.update_names(member_class, member_code, member_name)
    identifier_search_params = { :identifiers => {
      :xroad_instance => SystemParameter.instance_identifier,
      :member_class => member_class,
      :member_code => member_code}
    }

    get_requests_with_owned_servers(identifier_search_params).\
        find_each do |each|
      each.update_attributes!(:server_owner_name => member_name)
    end

    get_requests_with_used_servers(identifier_search_params).\
        find_each do |each|
      each.update_attributes!(:server_user_name => member_name)
    end
  end

  private

  def self.get_multivalue_search_regex(values)
    # Assumes that 'type' and 'origin' are always filled in the database.
    return "\s" if values.empty?

    first = true;
    result = "("

    values.each do |each|
      each.strip!

      if first
        first = false
      else
        result << "|"
      end

      result << "#{each}"
    end

    result << ")"
    result
  end

  def self.get_search_relation(searchable, converted_search_params)

    search_params = get_search_sql_params(
        searchable.downcase(), converted_search_params)

    Request.
        where(get_search_sql, *search_params)
  end

  def self.get_search_sql
    "CAST(requests.id AS TEXT) LIKE ?
    OR #{CommonSql.turn_timestamp_into_text("requests.created_at")} LIKE ?
    OR (requests.type) SIMILAR TO ?
    OR (requests.origin) SIMILAR TO ?
    OR lower(requests.server_owner_name) LIKE ?
    OR lower(requests.server_owner_class) LIKE ?
    OR lower(requests.server_owner_code) LIKE ?
    OR lower(requests.server_code) LIKE ?
    OR lower(requests.processing_status) LIKE ?"
  end

  def self.get_search_sql_params(searchable, converted_params)
    multivalue_regex = get_multivalue_search_regex(converted_params)
    ["%#{searchable}%", "%#{searchable}%","%#{multivalue_regex}%",
        "%#{multivalue_regex}%",  "%#{searchable}%", "%#{searchable}%",
        "%#{searchable}%", "%#{searchable}%", "%#{searchable}%"]
  end

  def self.find_by_server_and_client(clazz, server_id, client_id,
      processing_status = nil)
    logger.info("find_by_server_and_client(#{clazz}, #{server_id}, #{client_id}, #{processing_status})")

    if processing_status == nil
      processing_status = RequestProcessing::WAITING
    end

    requests = clazz \
        .joins(:security_server, :sec_serv_user, :request_processing)\
        .where(
          :identifiers => { # association security_server
            :xroad_instance => server_id.xroad_instance,
            :member_class => server_id.member_class,
            :member_code => server_id.member_code,
            :server_code => server_id.server_code},
          :sec_serv_users_requests => { # association sec_serv_user
            :xroad_instance => client_id.xroad_instance,
            :member_class => client_id.member_class,
            :member_code => client_id.member_code,
            :subsystem_code => client_id.subsystem_code},
          :request_processings => {:status => processing_status})

    # Filter for subsystem codes in sec_serv_user because this is cumbersome
    # to do with the SQL query.
    requests.select { |req|
        req.sec_serv_user.subsystem_code == client_id.subsystem_code
    }

    logger.debug("Requests returned: #{requests.inspect}")
    requests
  end

  def self.find_by_server(server_id)
    logger.info("find_by_server(#{server_id})")

    identifier_search_params = {
      :identifiers => {
        :xroad_instance => server_id.xroad_instance,
        :member_class => server_id.member_class,
        :member_code => server_id.member_code,
        :server_code => server_id.server_code}
    }

    requests = get_requests_with_owned_servers(identifier_search_params)

    logger.debug("Requests returned: #{requests.inspect}")
    requests
  end

  def self.get_requests_with_owned_servers(identifier_search_params)
    return get_requests_with_servers(identifier_search_params, :security_server)
  end

  def self.get_requests_with_used_servers(identifier_search_params)
    return get_requests_with_servers(identifier_search_params, :sec_serv_user)
  end

  def self.get_requests_with_servers(identifier_search_params, attribute)
    return Request.readonly(false).joins(attribute).where(identifier_search_params)
  end

  def self.set_server_owner_name(rec)
    server = rec.security_server
    return 1 if server == nil # To keep following callbacks running

    rec.server_owner_name = get_name(server)
  end

  def self.set_server_user_name(rec)
    client = rec.sec_serv_user
    return 1 if client == nil # To keep following callbacks running

    rec.server_user_name = get_name(client)
  end

  def self.get_name(id)
    XroadMember.get_name(id.member_class, id.member_code)
  end
end
