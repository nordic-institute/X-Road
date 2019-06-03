<template>
  <div class="xr-tab-max-width">
    <div>
      <subViewTitle :title="groupCode" @close="close"/>

      <template>
        <div class="cert-hash">
          {{$t('localGroup.localGroup')}}
          <v-btn
            v-if="showDelete"
            outline
            round
            color="primary"
            class="xr-big-button"
            type="file"
            @click="deleteGroup()"
          >{{$t('localGroup.delete')}}</v-btn>
        </div>
      </template>
    </div>

    <div class="edit-row">
      <template v-if="canEditDescription()">
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
          v-if="showRemove()"
          outline
          color="primary"
          class="xr-big-button"
          type="file"
          @click="removeAllMembers()"
        >{{$t('localGroup.removeAll')}}</v-btn>

        <v-btn
          v-if="showAddMembers()"
          outline
          color="primary"
          class="xr-big-button"
          type="file"
          @click="addMembers()"
        >{{$t('localGroup.add')}}</v-btn>
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
                  v-if="showRemove()"
                  small
                  outline
                  round
                  color="primary"
                  class="xr-small-button"
                  @click="removeMember(groupMember)"
                >{{$t('localGroup.remove')}}</v-btn>
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
          type="file"
          @click="close()"
        >{{$t('localGroup.close')}}</v-btn>
      </div>
    </v-card>

    <!-- Confirm dialog delete group -->
    <v-dialog v-model="confirmGroup" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">{{$t('localGroup.deleteTitle')}}</v-card-title>
        <v-card-text>{{$t('localGroup.deleteText')}}</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirmGroup = false">{{$t('localGroup.cancel')}}</v-btn>
          <v-btn color="darken-1" flat @click="doDeleteGroup()">{{$t('localGroup.yes')}}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Confirm dialog remove member-->
    <v-dialog v-model="confirmMember" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">{{$t('localGroup.removeTitle')}}</v-card-title>
        <v-card-text>{{$t('localGroup.removeText')}}</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirmMember = false">{{$t('localGroup.cancel')}}</v-btn>
          <v-btn color="darken-1" flat @click="doRemoveMember()">{{$t('localGroup.yes')}}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    code: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmGroup: false,
      confirmMember: false,
      selectedMember: undefined,
      description: undefined,
      group: undefined,
      groupCode: '',
      members: [],
    };
  },
  computed: {
    ...mapGetters(['tlsCertificates']),
    showDelete() {
      return true;
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    canEditDescription(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_LOCAL_GROUP_DESC,
      );
    },

    showRemove(): boolean {
      // TODO placeholder. will be done in future task
      return true;
    },

    showAddMembers(): boolean {
      // TODO placeholder. will be done in future task
      return true;
    },

    saveDescription(): void {
      axios
        .put(
          `/clients/${this.id}/groups/${this.code}?description=${
            this.description
          }`,
        )
        .then((res) => {
          this.group = res.data;
          this.groupCode = res.data.code;
          this.description = res.data.description;
          this.$bus.$emit('show-success', this.$t('localGroup.descSaved'));
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    fetchData(clientId: string, code: string): void {
      axios
        .get(`/clients/${clientId}/groups/${code}`)
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
      // TODO placeholder. will be done in future task
    },

    removeAllMembers(): void {
      // TODO placeholder. will be done in future task
    },

    removeMember(member: any): void {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveMember() {
      this.confirmMember = false;
      this.selectedMember = undefined;
      // TODO placeholder. will be done in future task
    },

    deleteGroup(): void {
      this.confirmGroup = true;
    },
    doDeleteGroup(): void {
      this.confirmGroup = false;
      // TODO placeholder. will be done in future task
    },
  },
  created() {
    this.fetchData(this.id, this.code);
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

