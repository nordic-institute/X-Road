<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <XrdElevatedViewFixedWidth
    data-test="service-description-details-dialog"
    :loading="loadingLocalGroup"
    :translated-title="localGroup?.code"
    :breadcrumbs="breadcrumbs"
    closeable
    @close="close"
  >
    <template #footer>
      <v-spacer />
      <XrdBtn
        v-if="showDelete"
        data-test="delete-local-group-button"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        @click="deleteGroup()"
      />
    </template>

    <XrdFormBlock>
      <XrdFormBlockRow full-length>
        <v-text-field
          v-model="value"
          data-test="local-group-edit-description-input"
          class="xrd"
          :loading="updatingDescription"
          :label="$t('localGroup.description')"
          :readonly="!canEditDescription"
          :error-messages="errors"
          @change="saveDescription"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>

    <XrdContainerTitle title="localGroup.groupMembers" class="mt-4 mb-4">
      <XrdBtn
        v-if="canEditMembers"
        data-test="remove-all-members-button"
        variant="outlined"
        text="action.removeAll"
        prepend-icon="delete_forever"
        :disabled="!hasMembers"
        @click="removeAllMembers()"
      />

      <XrdBtn
        v-if="canEditMembers"
        data-test="add-members-button"
        class="add-members-button ml-2"
        text="localGroup.addMembers"
        prepend-icon="add_circle"
        @click="addMembers()"
      />
    </XrdContainerTitle>

    <v-data-table
      data-test="group-members-table"
      class="xrd xrd-rounded-12 border"
      items-per-page="-1"
      hide-default-footer
      :headers="headers"
      :items="localGroup?.members"
    >
      <template #item.member_name="{ value: cellValue }">
        <XrdLabelWithIcon icon="id_card" :label="cellValue" />
      </template>
      <template #item.subsystem_name="{ item }">
        <subsystem-name :name="getSubsystemDisplayName(item)" />
      </template>
      <template #item.created_at="{ value: cellValue }">
        <XrdDateTime :value="cellValue" />
      </template>
      <template #item.actions="{ item }">
        <XrdBtn v-if="canEditMembers" variant="text" color="tertiary" text="action.remove" @click="memberToDelete = item" />
      </template>
    </v-data-table>

    <!-- Confirm dialog delete group -->
    <XrdConfirmDialog
      v-if="confirmGroup"
      title="localGroup.deleteTitle"
      text="localGroup.deleteText"
      :loading="deletingLocalGroup"
      @cancel="confirmGroup = false"
      @accept="doDeleteGroup()"
    />

    <!-- Confirm dialog remove member -->
    <XrdConfirmDialog
      v-if="memberToDelete"
      title="localGroup.removeTitle"
      text="localGroup.removeText"
      :loading="deletingLocalGroupMembers"
      @cancel="memberToDelete = undefined"
      @accept="doRemoveMember()"
    />

    <!-- Confirm dialog remove all members -->
    <XrdConfirmDialog
      v-if="confirmAllMembers"
      title="localGroup.removeAllTitle"
      text="localGroup.removeAllText"
      :loading="deletingLocalGroupMembers"
      @cancel="confirmAllMembers = false"
      @accept="doRemoveAllMembers()"
    />

    <!-- Add new members dialog -->
    <AddMembersDialog
      v-if="localGroup && addMembersDialogVisible"
      :local-group-id="localGroup.id || ''"
      :filtered="localGroup.members"
      @cancel="closeMembersDialog"
      @members-added="doAddMembers"
    />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import { GroupMember } from '@/openapi-types';
import AddMembersDialog from './AddMembersDialog.vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useField } from 'vee-validate';
import SubsystemName from '@/components/client/SubsystemName.vue';
import {
  XrdDateTime,
  XrdElevatedViewFixedWidth,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  useNotifications,
  XrdContainerTitle,
  XrdLabelWithIcon,
  XrdConfirmDialog,
} from '@niis/shared-ui';
import { useLocalGroups } from '@/store/modules/local-groups';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { clientTitle } from '@/util/ClientUtil';
import { useClient } from '@/store/modules/client';

function getSubsystemDisplayName(groupMember: GroupMember): string {
  return groupMember.subsystem_name ?? groupMember.id.substring(groupMember.id.lastIndexOf(':') + 1);
}

export default defineComponent({
  components: {
    XrdBtn,
    XrdElevatedViewFixedWidth,
    XrdFormBlock,
    XrdFormBlockRow,
    SubsystemName,
    AddMembersDialog,
    XrdDateTime,
    XrdContainerTitle,
    XrdLabelWithIcon,
    XrdConfirmDialog,
  },
  props: {
    groupId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const { meta, setValue, value, errors } = useField<string>('description', 'required|max:255');
    return { meta, setValue, value, errors, addError, addSuccessMessage };
  },
  data() {
    return {
      confirmGroup: false,
      confirmAllMembers: false,
      memberToDelete: undefined as GroupMember | undefined,
      addMembersDialogVisible: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClient, ['client', 'clientLoading']),
    ...mapState(useLocalGroups, [
      'localGroup',
      'loadingLocalGroup',
      'updatingDescription',
      'deletingLocalGroup',
      'deletingLocalGroupMembers',
    ]),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_LOCAL_GROUP);
    },
    canEditDescription(): boolean {
      return this.hasPermission(Permissions.EDIT_LOCAL_GROUP_DESC);
    },

    canEditMembers(): boolean {
      return this.hasPermission(Permissions.EDIT_LOCAL_GROUP_MEMBERS);
    },
    hasMembers(): boolean {
      return !!(this.localGroup && this.localGroup.members && this.localGroup.members.length > 0);
    },

    headers() {
      return [
        { title: this.$t('localGroup.memberName'), key: 'member_name' },
        {
          title: this.$t('localGroup.subsystemName'),
          key: 'subsystem_name',
          sortRaw(member1, member2) {
            return getSubsystemDisplayName(member1).localeCompare(getSubsystemDisplayName(member2));
          },
        },
        { title: this.$t('localGroup.id'), key: 'id' },
        { title: this.$t('localGroup.accessDate'), key: 'created_at' },
        { title: '', key: 'actions', sortable: false },
      ] as DataTableHeader<GroupMember>[];
    },

    breadcrumbs() {
      if (!this.client || !this.localGroup) {
        return [];
      }
      return [
        {
          title: this.$t('tab.main.clients'),
          to: { name: RouteName.Clients },
        },
        {
          title: clientTitle(this.client, this.clientLoading),
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.client.id },
          },
        },
        {
          title: this.$t('tab.client.localGroups'),
          to: {
            name: RouteName.SubsystemLocalGroups,
            params: { id: this.client.id },
          },
        },
        {
          title: this.localGroup.code,
        },
      ] as BreadcrumbItem[];
    },
  },
  created() {
    this.fetchData(this.groupId);
  },
  methods: {
    ...mapActions(useClient, ['fetchClient']),
    ...mapActions(useLocalGroups, ['updateDescription', 'fetchLocalGroup', 'deleteLocalGroup', 'deleteLocalGroupMembers']),
    close(): void {
      this.$router.push({
        name: RouteName.SubsystemLocalGroups,
        params: { id: this.localGroup?.client_id },
      });
    },

    getSubsystemDisplayName,

    saveDescription(): void {
      if (this.meta.valid) {
        this.updateDescription(this.groupId, this.value)
          .then((res) => this.setValue(res.description))
          .then(() => this.addSuccessMessage('localGroup.descSaved'))
          .catch((error) => this.addError(error));
      }
    },

    fetchData(groupId: string): void {
      this.fetchLocalGroup(groupId)
        .then((res) => {
          this.setValue(res.description);
          return this.fetchClient(res.client_id || '');
        })
        .catch((error) => this.addError(error, { navigate: true }));
    },

    addMembers(): void {
      this.addMembersDialogVisible = true;
    },

    doAddMembers(): void {
      this.addMembersDialogVisible = false;
      this.fetchData(this.groupId);
    },

    closeMembersDialog(): void {
      this.addMembersDialogVisible = false;
    },

    removeAllMembers(): void {
      this.confirmAllMembers = true;
    },

    doRemoveAllMembers(): void {
      if (!this.localGroup?.members) {
        return;
      }

      const ids = this.localGroup.members.map((member) => member.id);
      this.removeArrayOfMembers(ids);
    },
    doRemoveMember() {
      if (!this.memberToDelete) {
        return;
      }

      this.removeArrayOfMembers([this.memberToDelete.id]);
    },

    removeArrayOfMembers(members: string[]) {
      this.deleteLocalGroupMembers(this.groupId, members)
        .catch((error) => this.addError(error))
        .finally(() => (this.memberToDelete = undefined))
        .finally(() => (this.confirmAllMembers = false))
        .finally(() => this.fetchData(this.groupId));
    },

    deleteGroup(): void {
      this.confirmGroup = true;
    },
    doDeleteGroup(): void {
      const clientId = this.localGroup?.client_id;
      this.deleteLocalGroup(this.groupId)
        .then(() => {
          this.addSuccessMessage('localGroup.groupDeleted', {}, true);
          this.$router.push({
            name: RouteName.SubsystemLocalGroups,
            params: { id: clientId },
          });
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.confirmGroup = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
