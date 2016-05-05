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

java_import Java::java.util.Date

java_import Java::ee.ria.xroad.common.conf.serverconf.model.AccessRightType
java_import Java::ee.ria.xroad.common.identifier.ClientId
java_import Java::ee.ria.xroad.common.identifier.GlobalGroupId
java_import Java::ee.ria.xroad.common.identifier.LocalGroupId
java_import Java::ee.ria.xroad.common.identifier.XroadObjectType

module Clients::AclSubjects

  def client_acl_subjects
    authorize!(:view_client_acl_subjects)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    has_services = false

    client.wsdl.each do |wsdl|
      unless wsdl.service.isEmpty
        has_services = true
        break
      end
    end

    render_json({
      :acl_subjects => read_acl_subjects(client),
      :has_services => has_services
    })
  end

  def acl_subjects_search
    authorize!(:view_client_acl_subjects)

    validate_params({
      :client_id => [:required],
      :subject_search_class => [],
      :subject_search_instance => [],
      :subject_search_type => [],
      :subject_search_description => [],
      :subject_search_code => [],
      :subject_search_subsystem_code => [],
      :subject_search_all => [],
      :members_only => []
    })

    client = get_client(params[:client_id])

    type = params[:subject_search_type] || ""
    members_only = params[:members_only]

    subjects = []

    GlobalConf::getMembers.each do |subject|
      cache_subject_id(subject.id)

      if !subject.id.subsystemCode
        subjects << {
          :subject_id => subject.id.toString,
          :name_description => subject.name,
          :member_class => subject.id.memberClass,
          :member_group_code => subject.id.memberCode,
          :subsystem_code => nil,
          :instance => subject.id.xRoadInstance,
          :type => subject.id.objectType.toString
        } if XroadObjectType::MEMBER.toString.include?(type)
      else
        subjects << {
          :subject_id => subject.id.toString,
          :name_description => subject.name,
          :member_class => subject.id.memberClass,
          :member_group_code => subject.id.memberCode,
          :subsystem_code => subject.id.subsystemCode,
          :instance => subject.id.xRoadInstance,
          :type => subject.id.objectType.toString
        } if XroadObjectType::SUBSYSTEM.toString.include?(type)
      end
    end

    GlobalConf::getGlobalGroups.each do |subject|
      cache_subject_id(subject.id)

      subjects << {
        :subject_id => subject.id.toString,
        :name_description => subject.description,
        :member_class => nil,
        :member_group_code => subject.id.groupCode,
        :subsystem_code => nil,
        :instance => subject.id.xRoadInstance,
        :type => XroadObjectType::GLOBALGROUP.toString
      }
    end if XroadObjectType::GLOBALGROUP.toString.include?(type) && !members_only

    client.localGroup.each do |group|
      subject_id = LocalGroupId.create(group.groupCode)

      cache_subject_id(subject_id)

      subjects << {
        :subject_id => subject_id.toString,
        :name_description => group.description,
        :member_class => nil,
        :member_group_code => group.groupCode,
        :subsystem_code => nil,
        :instance => nil,
        :type => XroadObjectType::LOCALGROUP.toString
      }
    end if XroadObjectType::LOCALGROUP.toString.include?(type) && !members_only

    # filter by instance, class, code, subsystem_code, name
    if params[:subject_search_all]
      filter = params[:subject_search_all]
      subjects.select! do |subject|
        match(subject[:type], filter) ||
          match(subject[:instance], filter) ||
          match(subject[:member_group_code], filter) ||
          match(subject[:name_description], filter) ||
          match(subject[:member_class], filter) ||
          match(subject[:subsystem_code], filter)
      end
    else
      subjects.select! do |subject|
        (match(subject[:instance], params[:subject_search_instance], true) ||
         subject[:type] == XroadObjectType::LOCALGROUP.toString) &&
          match(subject[:member_group_code], params[:subject_search_code]) &&
          match(subject[:name_description], params[:subject_search_description]) &&
          match(subject[:member_class], params[:subject_search_class], true) &&
          match(subject[:subsystem_code], params[:subject_search_subsystem_code])
      end
    end

    render_json(subjects)
  end

  def acl_subject_open_services
    authorize!(:view_acl_subject_open_services)

    validate_params({
      :client_id => [:required],
      :subject_id => [:required]
    })

    client = get_client(params[:client_id])
    subject_id = get_cached_subject_id(params[:subject_id])

    render_json(read_subject_services(client, subject_id))
  end

  def acl_subject_open_services_add
    audit_log("Add access rights to subject", audit_log_data = {})

    authorize!(:edit_acl_subject_open_services)

    validate_params({
      :client_id => [:required],
      :subject_id => [:required],
      :service_codes => [:required]
    })

    client = get_client(params[:client_id])
    subject_id = get_cached_subject_id(params[:subject_id])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:subjectId] = subject_id.toString
    audit_log_data[:serviceCodes] = []

    now = Date.new

    params[:service_codes].each do |service_code|
      if !contains_subject(client.acl, subject_id, service_code)
        access_right = AccessRightType.new
        access_right.subjectId = subject_id
        access_right.serviceCode = service_code
        access_right.rightsGiven = now

        audit_log_data[:serviceCodes] << access_right.serviceCode

        client.acl.add(access_right)
      end
    end

    serverconf_save

    render_json(read_subject_services(client, subject_id))
  end

  def acl_subject_open_services_remove
    audit_log("Remove access rights from subject", audit_log_data = {})

    authorize!(:edit_acl_subject_open_services)

    validate_params({
      :client_id => [:required],
      :subject_id => [:required],
      :service_codes => []
    })

    client = get_client(params[:client_id])
    subject_id = get_cached_subject_id(params[:subject_id])

    removed_access_rights =
      remove_access_rights(client.acl, subject_id, params[:service_codes])

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:subjectId] = subject_id.toString
    audit_log_data[:serviceCodes] = removed_access_rights.map do |access_right|
      access_right.serviceCode
    end

    serverconf_save

    render_json(read_subject_services(client, subject_id))
  end

  private

  def read_acl_subjects(client, service_code = nil)
    subjects = {}
    localgroup_descs = {}

    client.localGroup.each do |group|
      localgroup_descs[group.groupCode] = group.description
    end

    clear_cached_subject_ids

    client.acl.each do |access_right|
      next if service_code && access_right.serviceCode != service_code

      subject_id = access_right.subjectId

      cache_subject_id(subject_id)

      subject = {
        :subject_id => subject_id.toString,
        :type => subject_id.objectType.toString,
        :instance => subject_id.xRoadInstance,
        :rights_given => format_time(access_right.rightsGiven)
      }

      if subject_id.objectType == XroadObjectType::MEMBER
        subject[:name_description] = GlobalConf::getMemberName(subject_id)
        subject[:member_group_code] = subject_id.memberCode
        subject[:member_class] = subject_id.memberClass
        subject[:subsystem_code] = nil
      end

      if subject_id.objectType == XroadObjectType::SUBSYSTEM
        subject[:name_description] = GlobalConf::getMemberName(subject_id)
        subject[:member_group_code] = subject_id.memberCode
        subject[:member_class] = subject_id.memberClass
        subject[:subsystem_code] = subject_id.subsystemCode
      end

      if subject_id.objectType == XroadObjectType::GLOBALGROUP
        subject[:name_description] =
          GlobalConf::getGlobalGroupDescription(subject_id)
        subject[:member_group_code] = subject_id.groupCode
        subject[:member_class] = nil
        subject[:subsystem_code] = nil
      end

      if subject_id.objectType == XroadObjectType::LOCALGROUP
        subject[:name_description] = localgroup_descs[subject_id.groupCode]
        subject[:member_group_code] = subject_id.groupCode
        subject[:member_class] = nil
        subject[:subsystem_code] = nil
      end

      subjects[subject_id.toString] = subject
    end

    subjects.values
  end

  def read_subject_services(client, subject_id)
    services = []

    client.acl.each do |access_right|
      next unless access_right.subjectId == subject_id

      services << {
        :service_code => access_right.serviceCode,
        :title => get_service_title(client, access_right.serviceCode),
        :rights_given => format_time(access_right.rightsGiven)
      }
    end

    services.sort! do |x, y|
      x[:service_code] <=> y[:service_code]
    end
  end

  def get_service_title(client, service_code)
    client.wsdl.each do |wsdl|
      wsdl.service.each do |service|
        return service.title if service.serviceCode == service_code
      end
    end

    nil
  end

  def match(data, filter, exact = false)
    # no filter applied
    return true if !filter || filter.empty?

    # do not match null/empty data if filter is present
    return false if !data || data.empty?

    return exact ? data == filter :
      data.mb_chars.downcase.include?(filter.mb_chars.downcase)
  end

  def cache_subject_id(subject_id)
    session[:subject_ids] ||= {}
    session[:subject_ids][subject_id.toString] = subject_id
  end

  def get_cached_subject_id(key)
    get_identifier(session[:subject_ids][key])
  end

  def clear_cached_subject_ids
    session[:subject_ids] = {}
  end

  def format_time(time)
    return nil unless time

    Time.at(time.getTime / 1000).strftime("%F")
  end

  def contains_subject(access_right, subject_id, service_code)
    access_right.each do |access_right|
      if access_right.subjectId == subject_id &&
         access_right.serviceCode == service_code
        return access_right
      end
    end

    nil
  end

  def remove_access_rights(access_rights, subject_ids, service_codes)
    removed_access_rights = []

    subject_ids = Array(subject_ids)
    service_codes = Array(service_codes)

    access_rights.each do |access_right|
      if (subject_ids.empty? || subject_ids.include?(access_right.subjectId)) &&
         (service_codes.empty? || service_codes.include?(access_right.serviceCode))

        removed_access_rights << access_right
      end
    end

    access_rights.removeAll(removed_access_rights)
    removed_access_rights
  end
end
