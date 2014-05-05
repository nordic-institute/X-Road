java_import Java::java.util.GregorianCalendar
java_import Java::javax.xml.datatype.DatatypeFactory

java_import Java::ee.cyber.sdsb.common.identifier.ClientId
java_import Java::ee.cyber.sdsb.common.identifier.GlobalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.LocalGroupId
java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType

module SubjectsSearch

  def subjects_search
    authorize!(:view_subjects)

    validate_params({
      :client_id => [],
      :subject_search_class => [],
      :subject_search_sdsb => [],
      :subject_search_type => [],
      :subject_search_description => [],
      :subject_search_code => [],
      :subject_search_subsystem_code => [],
      :subject_search_all => [],
      :members_only => []
    })

    globalconf_sdsb = globalconf.root.instanceIdentifier

    type = params[:subject_search_type] || ""
    members_only = params[:members_only]

    subjects = []

    globalconf.root.member.each do |member|
      if SdsbObjectType::MEMBER.toString.include?(type)
        subject_id = ClientId.create(globalconf_sdsb,
          member.memberClass, member.memberCode, nil)

        cache_subject_id(subject_id)

        subjects << {
          :subject_id => subject_id.toString,
          :name_description => member.name,
          :member_class => member.memberClass,
          :member_group_code => member.memberCode,
          :subsystem_code => nil,
          :sdsb => subject_id.sdsbInstance,
          :type => subject_id.objectType.toString
        }
      end

      member.subsystem.each do |subsystem|
        subject_id = ClientId.create(globalconf_sdsb,
          member.memberClass, member.memberCode, subsystem.subsystemCode)

        cache_subject_id(subject_id)

        subjects << {
          :subject_id => subject_id.toString,
          :name_description => member.name,
          :member_class => member.memberClass,
          :member_group_code => member.memberCode,
          :subsystem_code => subsystem.subsystemCode,
          :sdsb => subject_id.sdsbInstance,
          :type => subject_id.objectType.toString
        }
      end if SdsbObjectType::SUBSYSTEM.toString.include?(type)
    end

    globalconf.root.globalGroup.each do |group|
      subject_id = GlobalGroupId.create(globalconf_sdsb, group.groupCode)

      cache_subject_id(subject_id)

      subjects << {
        :subject_id => subject_id.toString,
        :name_description => group.description,
        :member_class => nil,
        :member_group_code => group.groupCode,
        :subsystem_code => nil,
        :sdsb => subject_id.sdsbInstance,
        :type => SdsbObjectType::GLOBALGROUP.toString
      }
    end if SdsbObjectType::GLOBALGROUP.toString.include?(type) && !members_only

    serverconf.root.client.each do |client|
      if params[:client_id] &&
          client.identifier != get_cached_client_id(params[:client_id])
        next
      end

      client.localGroup.each do |group|
        subject_id = LocalGroupId.create(group.groupCode)

        cache_subject_id(subject_id)

        subjects << {
          :subject_id => subject_id.toString,
          :name_description => group.description,
          :member_class => nil,
          :member_group_code => group.groupCode,
          :subsystem_code => nil,
          :sdsb => nil,
          :type => SdsbObjectType::LOCALGROUP.toString
        }
      end
    end if SdsbObjectType::LOCALGROUP.toString.include?(type) && !members_only

    # filter by sdsb, class, code, subsystem_code, name
    if params[:subject_search_all]
      filter = params[:subject_search_all]
      subjects.select! do |subject|
        match(subject[:type], filter) ||
          match(subject[:sdsb], filter) ||
          match(subject[:member_group_code], filter) ||
          match(subject[:name_description], filter) ||
          match(subject[:member_class], filter) ||
          match(subject[:subsystem_code], filter)
      end
    else
      subjects.select! do |subject|
        match(subject[:sdsb], params[:subject_search_sdsb], true) &&
          match(subject[:member_group_code], params[:subject_search_code]) &&
          match(subject[:name_description], params[:subject_search_description]) &&
          match(subject[:member_class], params[:subject_search_class], true) &&
          match(subject[:subsystem_code], params[:subject_search_subsystem_code])
      end
    end

    render_json(subjects)
  end

  private

  def match(data, filter, exact = false)
    # no filter applied
    return true if !filter || filter.empty?

    # do not match null/empty data if filter is present
    return false if !data || data.empty?

    return exact ? data == filter : data.downcase.include?(filter.downcase)
  end

  def subject_hash(subject_id, rights_given = nil)
    descriptions = {}

    globalconf.root.globalGroup.each do |group|
      descriptions[group.groupCode] = group.description
    end

    globalconf.root.member.each do |member|
      descriptions[member.memberCode] = member.name
    end

    # TODO: can we assume localGroups have unique codes?
    serverconf.root.client.each do |client|
      client.localGroup.each do |group|
        descriptions[group.groupCode] = group.description
      end
      descriptions[client.getIdentifier().memberCode] = client.fullName
    end
    subject = {
      :subject_id => subject_id.toString,
      :type => subject_id.objectType.toString,
      :sdsb => subject_id.sdsbInstance,
      :rights_given => format_xml_time(rights_given)
    }

    if subject_id.objectType == SdsbObjectType::MEMBER
      subject[:member_class] = subject_id.memberClass
      subject[:member_group_code] = subject_id.memberCode
      subject[:name_description] = descriptions[subject_id.memberCode]
      subject[:subsystem_code] = nil
    end

    if subject_id.objectType == SdsbObjectType::SUBSYSTEM
      subject[:member_class] = subject_id.memberClass
      subject[:name_description] = descriptions[subject_id.memberCode]
      subject[:member_group_code] = subject_id.memberCode
      subject[:subsystem_code] = subject_id.subsystemCode
    end

    if subject_id.objectType == SdsbObjectType::GLOBALGROUP
      subject[:member_class] = nil
      subject[:name_description] = descriptions[subject_id.groupCode]
      subject[:member_group_code] = subject_id.groupCode
      subject[:subsystem_code] = nil
    end

    if subject_id.objectType == SdsbObjectType::LOCALGROUP
      subject[:sdsb] = globalconf.root.instanceIdentifier
      subject[:member_class] = nil
      subject[:name_description] = descriptions[subject_id.groupCode]
      subject[:member_group_code] = subject_id.groupCode
      subject[:subsystem_code] = nil
    end

    subject
  end

  def cache_subject_id(subject_id)
    session[:subject_ids] ||= {}
    session[:subject_ids][subject_id.toString] = subject_id
  end

  def get_cached_subject_id(key)
    session[:subject_ids][key]
  end

  def clear_cached_subject_ids
    session[:subject_ids] = {}
  end

  def format_xml_time(time)
    return nil unless time

    time_in_millis = time.toGregorianCalendar.getTimeInMillis
    Time.at(time_in_millis / 1000).strftime("%F")
  end

  def now_xml
    DatatypeFactory.newInstance.newXMLGregorianCalendar(GregorianCalendar.new)
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

    authorized_subjects.remove(subject_to_remove)
  end
end
