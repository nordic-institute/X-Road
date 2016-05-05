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
    audit_log("Add group", audit_log_data = {})

    authorize!(:edit_local_group_members)

    validate_params({
      :client_id => [:required],
      :add_group_code => [:required],
      :add_group_description => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    client.localGroup.each do |group|
      if group.groupCode == params[:add_group_code]
        raise t('clients.group_exists', :code => params[:add_group_code])
      end
    end

    group = LocalGroupType.new
    group.groupCode = params[:add_group_code]
    group.description = params[:add_group_description]
    group.updated = Date.new

    audit_log_data[:groupCode] = group.groupCode
    audit_log_data[:groupDescription] = group.description

    client.localGroup.add(group)

    serverconf_save

    render_json(read_groups(client))
  end

  def group_delete
    audit_log("Delete group", audit_log_data = {})

    authorize!(:delete_local_group)

    validate_params({
      :client_id => [:required],
      :group_code => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    deleted_group = nil
    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]
      deleted_group = group
    end

    client.localGroup.remove(deleted_group)

    remove_access_rights(client.acl, LocalGroupId.create(params[:group_code]), nil)

    audit_log_data[:groupCode] = deleted_group.groupCode
    audit_log_data[:groupDescription] = deleted_group.description

    serverconf_save

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
    audit_log("Add members to group", audit_log_data = {})

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

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:groupCode] = group.groupCode
    audit_log_data[:memberIdentifiers] = []

    now = Date.new

    params[:member_ids].each do |member_id|
      groupMember = GroupMemberType.new
      groupMember.groupMemberId = get_cached_subject_id(member_id)
      groupMember.added = now
      group.groupMember.add(groupMember)

      audit_log_data[:memberIdentifiers] << groupMember.groupMemberId.toString
    end

    group.updated = now

    serverconf_save

    render_json(read_group_members(client, params[:group_code]))
  end

  def group_members_remove
    audit_log("Remove members from group", audit_log_data = {})

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

    audit_log_data[:clientIdentifier] = client.identifier
    audit_log_data[:groupCode] = group.groupCode
    audit_log_data[:memberIdentifiers] = []

    removed_members = []

    if params[:member_ids]
      params[:member_ids].each do |member_id|
        group.groupMember.each do |member|
          cached_id = get_cached_subject_id(member_id)

          if member.groupMemberId.equals(cached_id)
            removed_members << member
            audit_log_data[:memberIdentifiers] << member.groupMemberId.toString
          end
        end
      end

      group.groupMember.removeAll(removed_members)
    else
      group.groupMember.each do |member|
        audit_log_data[:memberIdentifiers] << member.groupMemberId.toString
      end

      group.groupMember.clear
    end

    group.updated = Date.new

    serverconf_save

    render_json(read_group_members(client, params[:group_code]))
  end

  def group_description_edit
    audit_log("Edit group description", audit_log_data = {})

    authorize!(:edit_local_group_desc)

    validate_params({
      :client_id => [:required],
      :group_code => [:required],
      :description => [:required]
    })

    client = get_client(params[:client_id])

    audit_log_data[:clientIdentifier] = client.identifier

    client.localGroup.each do |group|
      next unless group.groupCode == params[:group_code]

      group.description = params[:description]

      audit_log_data[:groupCode] = group.groupCode
      audit_log_data[:groupDescription] = group.description
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
