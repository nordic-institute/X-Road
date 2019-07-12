<template>
  <div class="xr-tab-max-width">
    <div>
      <subViewTitle :title="groupCode" @close="close" />

      <template>
        <div class="cert-hash">
          {{$t('localGroup.localGroup')}}
          <v-btn
            v-if="showDelete"
            outline
            round
            color="primary"
            class="xr-big-button"
            @click="deleteGroup()"
          >{{$t('action.delete')}}</v-btn>
        </div>
      </template>
    </div>

    <div class="edit-row">
      <template v-if="canEditDescription">
        <div>{{$t('localGroup.editDesc')}}</div>
        <v-text-field
          v-model="description"
          @change="saveDescription"
          single-line
          hide-details
          class="description-input"
        ></v-text-field>
      </template>
      <template v-else>
        <div>{{description}}</div>
      </template>
    </div>

    <div class="group-members-row">
      <div class="row-title">{{$t('localGroup.groupMembers')}}</div>
      <div class="row-buttons">
        <v-btn
          v-if="canEditMembers"
          outline
          color="primary"
          class="xr-big-button"
          :disabled="!hasMembers"
          @click="removeAllMembers()"
        >{{$t('action.removeAll')}}</v-btn>
        <v-btn
          v-if="canEditMembers"
          outline
          color="primary"
          class="xr-big-button"
          @click="addMembers()"
        >{{$t('localGroup.addMembers')}}</v-btn>
      </div>
    </div>

    <v-card flat>
      <table class="xrd-table group-members-table">
        <tr>
          <th>{{$t('localGroup.name')}}</th>
          <th>{{$t('localGroup.id')}}</th>
          <th>{{$t('localGroup.accessDate')}}</th>
          <th></th>
        </tr>
        <template v-if="group && group.members && group.members.length > 0">
          <tr v-for="groupMember in group.members" v-bind:key="groupMember.id">
            <td>{{groupMember.name}}</td>
            <td>{{groupMember.id}}</td>
            <td>{{groupMember.created_at}}</td>

            <td>
              <div class="button-wrap">
                <v-btn
                  v-if="canEditMembers"
                  small
                  outline
                  round
                  color="primary"
                  class="xr-small-button"
                  @click="removeMember(groupMember)"
                >{{$t('action.remove')}}</v-btn>
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="close-button-wrap">
        <v-btn
          round
          color="primary"
          class="xr-big-button elevation-0"
          @click="close()"
        >{{$t('action.close')}}</v-btn>
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
      :dialog="addMembersDialogVisible"
      :groupId="groupId"
      @cancel="closeMembersDialog()"
      @membersAdded="membersAdded()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import AddMembersDialog from '@/components/AddMembersDialog.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    AddMembersDialog,
    ConfirmDialog,
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
      selectedMember: undefined,
      description: undefined,
      group: undefined,
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
      const tempGroup: any = this.group;

      if (tempGroup && tempGroup.members && tempGroup.members.length > 0) {
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
      axios
        .put(`/groups/${this.groupId}?description=${this.description}`)
        .then((res) => {
          this.$bus.$emit('show-success', 'localGroup.descSaved');
          this.group = res.data;
          this.groupCode = res.data.code;
          this.description = res.data.description;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    fetchData(clientId: string, groupId: number | string): void {
      axios
        .get(`/groups/${groupId}`)
        .then((res) => {
          this.group = res.data;
          this.groupCode = res.data.code;
          this.description = res.data.description;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    addMembers(): void {
      this.addMembersDialogVisible = true;
    },

    membersAdded(): void {
      this.addMembersDialogVisible = false;
      this.fetchData(this.clientId, this.groupId);
    },

    closeMembersDialog(): void {
      this.addMembersDialogVisible = false;
    },

    removeAllMembers(): void {
      this.confirmAllMembers = true;
    },

    doRemoveAllMembers(): void {
      const ids: any = [];
      const tempGroup: any = this.group;
      tempGroup.members.forEach((member: any) => {
        ids.push(member.id);
      });

      this.removeArrayOfMembers(ids);
      this.confirmAllMembers = false;
    },

    removeMember(member: any): void {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveMember() {
      const member: any = this.selectedMember;

      if (member && member.id) {
        this.removeArrayOfMembers([member.id]);
      }

      this.confirmMember = false;
      this.selectedMember = undefined;
    },

    removeArrayOfMembers(members: any) {
      axios
        .post(`/groups/${this.groupId}/members/delete`, {
          items: members,
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
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

      axios
        .delete(`/groups/${this.groupId}`)
        .then(() => {
          this.$bus.$emit('show-success', 'localGroup.groupDeleted');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
  created() {
    this.fetchData(this.clientId, this.groupId);
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

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
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.row-buttons {
  display: flex;
}

.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding-top: 60px;
  height: 100%;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid #9b9b9b;
  color: #4a4a4a;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;
}

.cert-hash {
  margin-top: 50px;
  display: flex;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.group-members-table {
  margin-top: 10px;
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

