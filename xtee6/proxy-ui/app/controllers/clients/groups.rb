java_import Java::ee.ria.xroad.common.conf.serverconf.model.GroupMemberType
java_import Java::ee.ria.xroad.common.conf.serverconf.model.LocalGroupType
java_import Java::ee.ria.xroad.common.identifier.LocalGroupId

module Clients::Groups

  def client_groups
    authorize!(:view_client_local_groups)

    validate_params({
      :client_id => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_groups(client))
  end

  def group_add
    authorize!(:edit_local_group_members)

    validate_params({
      :client_id => [:required],
      :add_group_code => [:required],
      :add_group_description => [:required]
    })

    client = get_client(params[:client_id])

    client.localGroup.each do |group|
      if group.groupCode == params[:add_group_code]
        raise t('clients.group_exists', :code => params[:add_group_code])
      end
    end

    group = LocalGroupType.new
    group.groupCode = params[:add_group_code]
    group.description = params[:add_group_description]
    group.updated = Date.new

    client.localGroup.add(group)

    serverconf_save

    render_json(read_groups(client))
  end

  def group_delete
    authorize!(:delete_local_group)

    validate_params({
      :client_id => [:required],
      :group_code => [:required]
    })

    client = get_client(params[:client_id])

    deleted_group = nil
    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]
      deleted_group = group
    end

    client.localGroup.remove(deleted_group)

    group_id = LocalGroupId.create(params[:group_code])
    
    client.acl.each do |acl|
      remove_subject(acl.authorizedSubject, group_id)
    end

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_groups(client))
  end

  def group_members
    authorize!(:view_client_local_groups)

    validate_params({
      :client_id => [:required],
      :group_code => [:required]
    })

    client = get_client(params[:client_id])

    render_json(read_group_members(client, params[:group_code]))
  end

  def group_members_add
    authorize!(:edit_local_group_members)

    validate_params({
      :client_id => [:required],
      :group_code => [:required],
      :member_ids => [:required]
    })

    client = get_client(params[:client_id])
    group = nil

    client.localGroup.each do |local_group|
      if local_group.groupCode == params[:group_code]
        group = local_group
        break
      end
    end

    now = Date.new

    params[:member_ids].each do |member_id|
      groupMember = GroupMemberType.new
      groupMember.groupMemberId = get_cached_subject_id(member_id)
      groupMember.added = now
      group.groupMember.add(groupMember)
    end

    group.updated = now

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_group_members(client, params[:group_code]))
  end

  def group_members_remove
    authorize!(:edit_local_group_members)

    validate_params({
      :client_id => [:required],
      :group_code => [:required],
      :member_ids => []
    })

    client = get_client(params[:client_id])
    group = nil

    client.localGroup.each do |local_group|
      if local_group.groupCode == params[:group_code]
        group = local_group
        break
      end
    end

    removed_members = []

    if params[:member_ids]
      params[:member_ids].each do |member_id|
        group.groupMember.each do |member|
          cached_id = get_cached_subject_id(member_id)

          if member.groupMemberId.equals(cached_id)
            removed_members << member
          end
        end
      end

      group.groupMember.removeAll(removed_members)
    else
      group.groupMember.clear
    end

    group.updated = Date.new

    serverconf_save

    after_commit do
      export_services
    end

    render_json(read_group_members(client, params[:group_code]))
  end

  def group_description_edit
    authorize!(:edit_local_group_desc)

    validate_params({
      :client_id => [:required],
      :group_code => [:required],
      :description => [:required]
    })

    client = get_client(params[:client_id])

    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]

      group.description = params[:description]
    end

    serverconf_save

    render_json(read_groups(client))
  end

  private

  def read_groups(client)
    groups = []

    client.localGroup.each do |group|
      groups << {
        :code => group.groupCode,
        :description => group.description,
        :member_count => group.groupMember.size,
        :updated => format_time(group.updated)
      }
    end

    groups
  end

  def read_group_members(client, group_code)
    clear_cached_subject_ids

    members = []
    client.localGroup.each do |group|
      next unless group.groupCode == group_code

      group.groupMember.each do |member|
        member_id = member.groupMemberId
        cache_subject_id(member_id)

        members << {
          :member_id => member_id.toString,
          :name => GlobalConf::getMemberName(member_id),
          :code => member_id.memberCode,
          :class => member_id.memberClass,
          :subsystem => member_id.subsystemCode,
          :instance => member_id.xRoadInstance,
          :type => member_id.objectType.toString,
          :added => format_time(member.added)
        }
      end
    end

    members
  end
end
