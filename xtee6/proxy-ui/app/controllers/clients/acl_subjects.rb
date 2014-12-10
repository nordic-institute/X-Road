java_import Java::java.util.Date

java_import Java::ee.cyber.sdsb.common.conf.serverconf.model.AuthorizedSubjectType
java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.common.identifier.GlobalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.LocalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType

module Clients::AclSubjects

  def client_acl_subjects
    authorize!(:view_client_acl_subjects)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])
    
    render_json(read_acl_subjects(client))
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
          :instance => subject.id.sdsbInstance,
          :type => subject.id.objectType.toString
        } if SdsbObjectType::MEMBER.toString.include?(type)
      else
        subjects << {
          :subject_id => subject.id.toString,
          :name_description => subject.name,
          :member_class => subject.id.memberClass,
          :member_group_code => subject.id.memberCode,
          :subsystem_code => subject.id.subsystemCode,
          :instance => subject.id.sdsbInstance,
          :type => subject.id.objectType.toString
        } if SdsbObjectType::SUBSYSTEM.toString.include?(type)
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
        :instance => subject.id.sdsbInstance,
        :type => SdsbObjectType::GLOBALGROUP.toString
      }
    end if SdsbObjectType::GLOBALGROUP.toString.include?(type) && !members_only

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
        :type => SdsbObjectType::LOCALGROUP.toString
      }
    end if SdsbObjectType::LOCALGROUP.toString.include?(type) && !members_only

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
         subject[:type] == SdsbObjectType::LOCALGROUP.toString) &&
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
    authorize!(:edit_acl_subject_open_services)

    validate_params({
      :client_id => [:required],
      :subject_id => [:required],
      :service_codes => [:required]
    })

    client = get_client(params[:client_id])
    subject_id = get_cached_subject_id(params[:subject_id])

    now = Date.new

    client.acl.each do |acl|
      if params[:service_codes].include?(acl.serviceCode)

        if !contains_subject(acl.authorizedSubject, subject_id)
          authorized_subject = AuthorizedSubjectType.new
          authorized_subject.subjectId = subject_id
          authorized_subject.rightsGiven = now

          acl.authorizedSubject.add(authorized_subject)
        end

        params[:service_codes].delete(acl.serviceCode)
      end
    end

    # create new acl for services which did not have one
    params[:service_codes].each do |service_code|
      acl = AclType.new
      acl.serviceCode = service_code

      authorized_subject = AuthorizedSubjectType.new
      authorized_subject.subjectId = subject_id
      authorized_subject.rightsGiven = now

      acl.authorizedSubject.add(authorized_subject)
      client.acl.add(acl)
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_subject_services(client, subject_id))
  end

  def acl_subject_open_services_remove
    authorize!(:edit_acl_subject_open_services)

    validate_params({
      :client_id => [:required],
      :subject_id => [:required],
      :service_codes => []
    })

    client = get_client(params[:client_id])
    subject_id = get_cached_subject_id(params[:subject_id])

    client.acl.each do |acl|
      if params[:service_codes]
        if params[:service_codes].include?(acl.serviceCode)
          remove_subject(acl.authorizedSubject, subject_id)
        end
      else
        remove_subject(acl.authorizedSubject, subject_id)
      end
    end

    serverconf_save

    after_commit do
      export_services
    end

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

    client.acl.each do |acl|
      next if service_code && acl.serviceCode != service_code

      acl.authorizedSubject.each do |authorized_subject|
        subject_id = authorized_subject.subjectId

        cache_subject_id(subject_id)

        subject = {
          :subject_id => subject_id.toString,
          :type => subject_id.objectType.toString,
          :instance => subject_id.sdsbInstance,
          :rights_given => format_time(authorized_subject.rightsGiven)
        }

        if subject_id.objectType == SdsbObjectType::MEMBER
          subject[:name_description] = GlobalConf::getMemberName(subject_id)
          subject[:member_group_code] = subject_id.memberCode
          subject[:member_class] = subject_id.memberClass
          subject[:subsystem_code] = nil
        end

        if subject_id.objectType == SdsbObjectType::SUBSYSTEM
          subject[:name_description] = GlobalConf::getMemberName(subject_id)
          subject[:member_group_code] = subject_id.memberCode
          subject[:member_class] = subject_id.memberClass
          subject[:subsystem_code] = subject_id.subsystemCode
        end

        if subject_id.objectType == SdsbObjectType::GLOBALGROUP
          subject[:name_description] =
            GlobalConf::getGlobalGroupDescription(subject_id)
          subject[:member_group_code] = subject_id.groupCode
          subject[:member_class] = nil
          subject[:subsystem_code] = nil
        end

        if subject_id.objectType == SdsbObjectType::LOCALGROUP
          subject[:name_description] = localgroup_descs[subject_id.groupCode]
          subject[:member_group_code] = subject_id.groupCode
          subject[:member_class] = nil
          subject[:subsystem_code] = nil
        end

        subjects[subject_id.toString] = subject
      end
    end

    subjects.values
  end

  def read_subject_services(client, subject_id)
    services = []

    client.acl.each do |acl|
      next unless subject = contains_subject(acl.authorizedSubject, subject_id)

      services << {
        :service_code => acl.serviceCode,
        :title => get_service_title(client, acl.serviceCode),
        :rights_given => format_time(subject.rightsGiven)
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

  def contains_subject(authorized_subjects, subject_id)
    authorized_subjects.each do |authorized_subject|
      return authorized_subject if authorized_subject.subjectId == subject_id
    end

    nil
  end

  def remove_subject(authorized_subjects, subject_id)
    subject_to_remove = nil

    authorized_subjects.each do |authorized_subject|
      if authorized_subject.subjectId == subject_id
        subject_to_remove = authorized_subject
      end
    end

    authorized_subjects.remove(subject_to_remove) if subject_to_remove
  end
end
