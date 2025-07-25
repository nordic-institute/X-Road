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
  <div class="xrd-tab-max-width xrd-main-wrap">
    <div class="pa-4">
      <xrd-sub-view-title :title="groupCode" @close="close" />

      <div class="cert-hash" data-test="local-group-title">
        {{ $t('localGroup.localGroup') }}
        <xrd-button
          v-if="showDelete"
          outlined
          data-test="delete-local-group-button"
          @click="deleteGroup()"
        >
          {{ $t('action.delete') }}
        </xrd-button>
      </div>
    </div>

    <div class="px-4 description-field">
      <template v-if="canEditDescription">
        <v-text-field
          v-model="value"
          variant="outlined"
          :label="$t('localGroup.description')"
          data-test="local-group-edit-description-input"
          :error-messages="errors"
          @change="saveDescription"
        ></v-text-field>
      </template>
      <template v-else>
        <div>{{ value }}</div>
      </template>
    </div>

    <div class="group-members-row px-4">
      <div class="row-title">{{ $t('localGroup.groupMembers') }}</div>
      <div class="row-buttons">
        <xrd-button
          v-if="canEditMembers"
          :disabled="!hasMembers"
          outlined
          data-test="remove-all-members-button"
          @click="removeAllMembers()"
          >{{ $t('action.removeAll') }}</xrd-button
        >

        <xrd-button
          v-if="canEditMembers"
          class="add-members-button"
          outlined
          data-test="add-members-button"
          @click="addMembers()"
          >{{ $t('localGroup.addMembers') }}</xrd-button
        >
      </div>
    </div>

    <v-card flat>
      <table
        data-test="group-members-table"
        class="xrd-table group-members-table"
      >
        <thead>
          <tr>
            <th>{{ $t('localGroup.memberName') }}</th>
            <th>{{ $t('localGroup.subsystemName') }}</th>
            <th>{{ $t('localGroup.id') }}</th>
            <th>{{ $t('localGroup.accessDate') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template v-if="group && group.members && group.members.length > 0">
            <tr v-for="groupMember in group.members" :key="groupMember.id">
              <td>{{ groupMember.member_name }}</td>
              <td>
                <subsystem-name :name="getSubsystemDisplayName(groupMember)" />
              </td>
              <td data-test="client-id">{{ groupMember.id }}</td>
              <td>{{ $filters.formatDateTime(groupMember.created_at) }}</td>

              <td>
                <div class="button-wrap">
                  <xrd-button
                    v-if="canEditMembers"
                    text
                    :outlined="false"
                    @click="removeMember(groupMember)"
                    >{{ $t('action.remove') }}</xrd-button
                  >
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>

      <div class="close-button-wrap">
        <xrd-button data-test="local-group-close-button" @click="close()">{{
          $t('action.close')
        }}</xrd-button>
      </div>
    </v-card>

    <!-- Confirm dialog delete group -->
    <xrd-confirm-dialog
      v-if="confirmGroup"
      title="localGroup.deleteTitle"
      text="localGroup.deleteText"
      @cancel="confirmGroup = false"
      @accept="doDeleteGroup()"
    />

    <!-- Confirm dialog remove member -->
    <xrd-confirm-dialog
      v-if="confirmMember"
      title="localGroup.removeTitle"
      text="localGroup.removeText"
      @cancel="confirmMember = false"
      @accept="doRemoveMember()"
    />

    <!-- Confirm dialog remove all members -->
    <xrd-confirm-dialog
      v-if="confirmAllMembers"
      title="localGroup.removeAllTitle"
      text="localGroup.removeAllText"
      @cancel="confirmAllMembers = false"
      @accept="doRemoveAllMembers()"
    />

    <!-- Add new members dialog -->
    <addMembersDialog
      v-if="group"
      :dialog="addMembersDialogVisible"
      :filtered="group.members"
      @cancel="closeMembersDialog"
      @members-added="doAddMembers"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import { GroupMember, LocalGroup } from '@/openapi-types';
import AddMembersDialog from './AddMembersDialog.vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useField } from 'vee-validate';
import SubsystemName from '@/components/client/SubsystemName.vue';

export default defineComponent({
  components: {
    SubsystemName,
    AddMembersDialog,
  },
  props: {
    clientId: {
      type: String,
      required: true,
    },
    groupId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { meta, setValue, value, errors } = useField<string>(
      'description',
      'required|max:255',
    );
    return { meta, setValue, value, errors };
  },
  data() {
    return {
      confirmGroup: false,
      confirmMember: false,
      confirmAllMembers: false,
      selectedMember: undefined as GroupMember | undefined,
      group: undefined as LocalGroup | undefined,
      groupCode: '',
      addMembersDialogVisible: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
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
      if (this.group && this.group.members && this.group.members.length > 0) {
        return true;
      }
      return false;
    },
  },
  created() {
    this.fetchData(this.clientId, this.groupId);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.back();
    },

    getSubsystemDisplayName(groupMember: GroupMember): string {
      return groupMember.subsystem_name ?? groupMember.id.substring(groupMember.id.lastIndexOf(':') + 1);
    },

    saveDescription(): void {
      if (this.meta.valid) {
        api
          .patch<LocalGroup>(
            `/local-groups/${encodePathParameter(this.groupId)}`,
            {
              description: this.value,
            },
          )
          .then((res) => {
            this.showSuccess(this.$t('localGroup.descSaved'));
            this.group = res.data;
            this.groupCode = res.data.code;
            this.setValue(res.data.description);
          })
          .catch((error) => {
            this.showError(error);
          });
      }
    },

    fetchData(clientId: string, groupId: number | string): void {
      api
        .get<LocalGroup>(`/local-groups/${encodePathParameter(groupId)}`)
        .then((res) => {
          this.group = res.data;
          this.groupCode = res.data.code;
          this.setValue(res.data.description);
        })
        .catch((error) => {
          this.showError(error);
        });
    },

    addMembers(): void {
      this.addMembersDialogVisible = true;
    },

    doAddMembers(selectedIds: string[]): void {
      this.addMembersDialogVisible = false;

      api
        .post(`/local-groups/${encodePathParameter(this.groupId)}/members`, {
          items: selectedIds,
        })
        .then(() => {
          this.fetchData(this.clientId, this.groupId);
        })
        .catch((error) => {
          this.showError(error);
        });
    },

    closeMembersDialog(): void {
      this.addMembersDialogVisible = false;
    },

    removeAllMembers(): void {
      this.confirmAllMembers = true;
    },

    doRemoveAllMembers(): void {
      if (!this.group?.members) {
        return;
      }
      const ids: string[] = [];

      this.group.members.forEach((member: GroupMember) => {
        ids.push(member.id);
      });
      this.removeArrayOfMembers(ids);

      this.confirmAllMembers = false;
    },

    removeMember(member: GroupMember): void {
      this.confirmMember = true;
      this.selectedMember = member as GroupMember;
    },
    doRemoveMember() {
      if (!this.selectedMember) {
        return;
      }
      const member: GroupMember = this.selectedMember;

      if (member && member.id) {
        this.removeArrayOfMembers([member.id]);
      }

      this.confirmMember = false;
      this.selectedMember = undefined;
    },

    removeArrayOfMembers(members: string[]) {
      api
        .post(
          `/local-groups/${encodePathParameter(this.groupId)}/members/delete`,
          {
            items: members,
          },
        )
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.fetchData(this.clientId, this.groupId);
        });
    },

    deleteGroup(): void {
      this.confirmGroup = true;
    },
    doDeleteGroup(): void {
      this.confirmGroup = false;

      api
        .remove(`/local-groups/${encodePathParameter(this.groupId)}`)
        .then(() => {
          this.showSuccess(this.$t('localGroup.groupDeleted'), true);
          this.$router.back();
        })
        .catch((error) => {
          this.showError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/tables';
@use '@niis/shared-ui/src/assets/colors';

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}
.row-title {
  width: 100%;
  justify-content: space-between;
  color: colors.$Black100;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.row-buttons {
  display: flex;
}

.cert-hash {
  margin-top: 50px;
  display: flex;
  justify-content: space-between;
  color: colors.$Black100;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.group-members-table {
  margin-top: 10px;
}

.add-members-button {
  margin-left: 20px;
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.close-button-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  padding: 20px;
  background-color: colors.$WarmGrey10;
  height: 72px;
}

.description-field {
  width: 404px;
  margin-top: 20px;
}
</style>
