java_import Java::ee.cyber.sdsb.common.conf.serverconf.AuthorizedSubjectType
java_import Java::ee.cyber.sdsb.common.identifier.SdsbObjectType

class QuicklistController < ApplicationController

  include SubjectsSearch

  def index
    authorize!(:view_subjects)

    @instances = [globalconf.root.instanceIdentifier]

    @member_classes = []
    globalconf.root.memberClass.each do |memberClass|
      @member_classes << memberClass.code
    end

    @subject_types = [
      SdsbObjectType::MEMBER.toString(),
      SdsbObjectType::SUBSYSTEM.toString(),
      SdsbObjectType::GLOBALGROUP.toString(),
      SdsbObjectType::LOCALGROUP.toString(),
    ]
  end

  def refresh
    authorize!(:view_subjects)

    render_json(read_subjects)
  end

  def subjects_add
    authorize!(:edit_subject_open_services)
    
    validate_params({
      :subject_ids => [RequiredValidator.new]
    })

    subjects = []
    params[:subject_ids].each do |subject_id|
      subjects << subject_hash(get_cached_subject_id(subject_id))
    end

    render_json(subjects)
  end

  def subject_acl
    authorize!(:view_subject_open_services)

    validate_params({
      :subject_id => [RequiredValidator.new]
    })

    subject_id = get_cached_subject_id(params[:subject_id])

    render_json(read_services(subject_id))
  end

  def acl_services_all
    authorize!(:edit_subject_open_services)

    validate_params({
      :subject_id => []
    })

    client_id = nil

    # If we are adding allowed services to localgroup, only show
    # services of the client owning the localgroup.
    # TODO: assumes unique localgroup codes among clients
    if params[:subject_id]
      subject_id = get_cached_subject_id(params[:subject_id])

      if subject_id.objectType == SdsbObjectType::LOCALGROUP
        serverconf.root.client.each do |client|
          client.localGroup.each do |group|
            if group.groupCode = subject_id.groupCode
              client_id = client.identifier
              break
            end
          end

          break if client_id
        end
      end
    end

    render_json(read_services(nil, client_id))
  end

  def acl_services_add
    authorize!(:edit_subject_open_services)

    validate_params({
      :subject_id => [RequiredValidator.new],
      :service_ids => [RequiredValidator.new]
    })

    subject_id = get_cached_subject_id(params[:subject_id])

    serverconf.root.client.each do |client|
      client.wsdl.each do |wsdl|
        wsdl.service.each do |service|
          params[:service_ids].each do |service_id|
            if service_id == get_service_id(client, wsdl, service)
              authorized_subject = AuthorizedSubjectType.new
              authorized_subject.subjectId = subject_id
              authorized_subject.rightsGiven = now_xml

              service.authorizedSubject.add(authorized_subject)
            end
          end
        end
      end
    end

    serverconf.write

    render_json(read_services(subject_id))
  end

  def acl_services_remove
    authorize!(:edit_subject_open_services)

    validate_params({
      :subject_id => [RequiredValidator.new],
      :service_ids => []
    })

    subject_id = get_cached_subject_id(params[:subject_id])

    serverconf.root.client.each do |client|
      client.wsdl.each do |wsdl|
        wsdl.service.each do |service|

          if params[:service_ids]
            params[:service_ids].each do |service_id|
              if service_id == get_service_id(client, wsdl, service)
                remove_subject(service.authorizedSubject, subject_id)
              end
            end if contains_subject(service.authorizedSubject, subject_id)

          else
            remove_subject(service.authorizedSubject, subject_id)
          end
        end
      end
    end

    serverconf.write

    render_json(read_services(subject_id))
  end

  private

  def read_subjects
    member_names = {}
    globalgroup_descs = {}

    globalconf.root.globalGroup.each do |group|
      globalgroup_descs[group.groupCode] = group.description
    end
    
    globalconf.root.member.each do |member|
      member_names[member.memberCode] = member.name
    end

    clear_cached_subject_ids

    subjects = {}

    serverconf.root.client.each do |client|
      localgroup_descs = {}
      client.localGroup.each do |group|
        localgroup_descs[group.groupCode] = group.description
      end

      client.wsdl.each do |wsdl|
        wsdl.service.each do |service|
          service.authorizedSubject.each do |authorized_subject|
            subject_id = authorized_subject.subjectId

            cache_subject_id(subject_id)

            subject = {
              :subject_id => subject_id.toString,
              :type => subject_id.objectType.toString,
              :sdsb => subject_id.sdsbInstance
            }

            if subject_id.objectType == SdsbObjectType::MEMBER
              subject[:name_description] = member_names[subject_id.memberCode]
              subject[:member_group_code] = subject_id.memberCode
              subject[:member_class] = subject_id.memberClass
              subject[:subsystem_code] = nil

            end

            if subject_id.objectType == SdsbObjectType::SUBSYSTEM
              subject[:name_description] = member_names[subject_id.memberCode]
              subject[:member_group_code] = subject_id.memberCode
              subject[:member_class] = subject_id.memberClass
              subject[:subsystem_code] = subject_id.subsystemCode

            end

            if subject_id.objectType == SdsbObjectType::GLOBALGROUP
              subject[:name_description] = globalgroup_descs[subject_id.groupCode]
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
      end
    end

    subjects.values
  end

  def cache_subject_id(subject_id)
    session[:subject_ids][subject_id.toString] = subject_id
  end

  def get_cached_subject_id(key)
    session[:subject_ids][key]
  end

  def clear_cached_subject_ids
    session[:subject_ids] = {}
  end

  def read_services(subject_id = nil, client_id = nil)
    services = []

    serverconf.root.client.each do |client|
      next if client_id && client.identifier != client_id

      client.wsdl.each do |wsdl|
        wsdl.service.each do |service|
          next if subject_id &&
            !(subject = contains_subject(service.authorizedSubject, subject_id))

          services << {
            :service_id => get_service_id(client, wsdl, service),
            :service_name => service.serviceCode,
            :title => service.title,
            :provider_name => client.fullName,
            :provider_class => client.identifier.memberClass,
            :provider_code => client.identifier.memberCode,
            :provider_subsystem => client.identifier.subsystemCode,
            :rights_given => subject ? format_xml_time(subject.rightsGiven) : nil
          }
        end
      end
    end

    services
  end

  def get_service_id(client, wsdl, service)
    "#{client.identifier.toString}:#{wsdl.url}:#{service.serviceCode}"
  end
end
