<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle :title="groupCode" @close="close" />

      <template>
        <div class="cert-hash">
          {{ $t('localGroup.localGroup') }}
          <large-button v-if="showDelete" @click="deleteGroup()" outlined>
            {{ $t('action.delete') }}
          </large-button>
        </div>
      </template>
    </div>

    <div class="edit-row">
      <template v-if="canEditDescription">
        <div>{{ $t('localGroup.editDesc') }}</div>
        <v-text-field
          v-model="description"
          @change="saveDescription"
          single-line
          hide-details
          class="description-input"
        ></v-text-field>
      </template>
      <template v-else>
        <div>{{ description }}</div>
      </template>
    </div>

    <div class="group-members-row">
      <div class="row-title">{{ $t('localGroup.groupMembers') }}</div>
      <div class="row-buttons">
        <large-button
          :disabled="!hasMembers"
          v-if="canEditMembers"
          @click="removeAllMembers()"
          outlined
          >{{ $t('action.removeAll') }}</large-button
        >

        <large-button
          class="add-members-button"
          v-if="canEditMembers"
          @click="addMembers()"
          outlined
          >{{ $t('localGroup.addMembers') }}</large-button
        >
      </div>
    </div>

    <v-card flat>
      <table class="xrd-table group-members-table">
        <tr>
          <th>{{ $t('localGroup.name') }}</th>
          <th>{{ $t('localGroup.id') }}</th>
          <th>{{ $t('localGroup.accessDate') }}</th>
          <th></th>
        </tr>
        <template v-if="group && group.members && group.members.length > 0">
          <tr v-for="groupMember in group.members" v-bind:key="groupMember.id">
            <td>{{ groupMember.name }}</td>
            <td>{{ groupMember.id }}</td>
            <td>{{ groupMember.created_at }}</td>

            <td>
              <div class="button-wrap">
                <v-btn
                  v-if="canEditMembers"
                  small
                  outlined
                  rounded
                  color="primary"
                  class="xrd-small-button"
                  @click="removeMember(groupMember)"
                  >{{ $t('action.remove') }}</v-btn
                >
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="close-button-wrap">
        <large-button @click="close()">{{ $t('action.close') }}</large-button>
      </div>
    </v-card>

    <!-- Confirm dialog delete group -->
    <confirmDialog
      :dialog="confirmGroup"
      title="localGroup.deleteTitle"
      text="localGroup.deleteText"
      @cancel="confirmGroup = false"
      @accept="doDeleteGroup()"
    />

    <!-- Confirm dialog remove member -->
    <confirmDialog
      :dialog="confirmMember"
      title="localGroup.removeTitle"
      text="localGroup.removeText"
      @cancel="confirmMember = false"
      @accept="doRemoveMember()"
    />

    <!-- Confirm dialog remove all members -->
    <confirmDialog
      :dialog="confirmAllMembers"
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
      @membersAdded="doAddMembers"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions } from '@/global';
import { GroupMember, LocalGroup } from '@/openapi-types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import AddMembersDialog from './AddMembersDialog.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    SubViewTitle,
    AddMembersDialog,
    ConfirmDialog,
    LargeButton,
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
  data() {
    return {
      confirmGroup: false,
      confirmMember: false,
      confirmAllMembers: false,
      selectedMember: undefined as GroupMember | undefined,
      description: undefined as string | undefined,
      group: undefined as LocalGroup | undefined,
      groupCode: '',
      addMembersDialogVisible: false,
    };
  },
  computed: {
    showDelete(): boolean {
      return this.$store.getters.hasPermission(Permissions.DELETE_LOCAL_GROUP);
    },
    canEditDescription(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_LOCAL_GROUP_DESC,
      );
    },

    canEditMembers(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_LOCAL_GROUP_MEMBERS,
      );
    },

    hasMembers(): boolean {
      if (this.group && this.group.members && this.group.members.length > 0) {
        return true;
      }
      return false;
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    saveDescription(): void {
      api
        .patch<LocalGroup>(
          `/local-groups/${encodePathParameter(this.groupId)}`,
          {
            description: this.description,
          },
        )
        .then((res) => {
          this.$store.dispatch('showSuccess', 'localGroup.descSaved');
          this.group = res.data;
          this.groupCode = res.data.code;
          this.description = res.data.description;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    fetchData(clientId: string, groupId: number | string): void {
      api
        .get<LocalGroup>(`/local-groups/${encodePathParameter(groupId)}`)
        .then((res) => {
          this.group = res.data;
          this.groupCode = res.data.code;
          this.description = res.data.description;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch('showSuccess', 'localGroup.groupDeleted');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
  },
  created() {
    this.fetchData(this.clientId, this.groupId);
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/tables';

.edit-row {
  display: flex;
  align-content: center;
  align-items: baseline;
  margin-top: 30px;

  .description-input {
    margin-left: 60px;
  }
}

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}
.row-title {
  width: 100%;
  justify-content: space-between;
  color: $XRoad-Black;
  font-family: Roboto;
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
  color: $XRoad-Black;
  font-family: Roboto;
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
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}
</style>
