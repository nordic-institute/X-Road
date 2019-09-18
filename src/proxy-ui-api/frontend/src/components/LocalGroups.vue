<template>
  <div>
    <div class="table-toolbar">
      <v-text-field v-model="search" label="Search" single-line hide-details class="search-input">
        <v-icon slot="append" small>fas fa-search</v-icon>
      </v-text-field>
      <v-btn
        v-if="showAddGroup"
        color="primary"
        @click="addGroup"
        outline
        round
        class="ma-0 rounded-button elevation-0"
      >{{$t('localGroups.addGroup')}}</v-btn>
    </div>

    <v-card flat>
      <table class="xrd-table details-certificates">
        <tr>
          <th>{{$t('localGroups.code')}}</th>
          <th>{{$t('localGroups.description')}}</th>
          <th>{{$t('localGroups.memberCount')}}</th>
          <th>{{$t('localGroups.updated')}}</th>
        </tr>
        <template v-if="groups && groups.length > 0">
          <tr v-for="group in filtered()" v-bind:key="group.code">
            <td>
              <span class="cert-name" @click="viewGroup(group)">{{group.code}}</span>
            </td>
            <td>{{group.description}}</td>
            <td>{{group.member_count}}</td>
            <td>{{group.updated_at | formatDate}}</td>
          </tr>
        </template>
      </table>
    </v-card>

    <newGroupDialog
      :dialog="addGroupDialog"
      :id="id"
      @cancel="closeDialog()"
      @groupAdded="groupAdded()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
import NewGroupDialog from '@/components/NewGroupDialog.vue';

import { mapGetters } from 'vuex';
import { Permissions, RouteName } from '@/global';
import { selectedFilter } from '@/util/helpers';

export default Vue.extend({
  components: {
    NewGroupDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '',
      dialog: false,
      groups: [],
      addGroupDialog: false,
    };
  },
  computed: {
    ...mapGetters(['client']),
    showAddGroup(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_LOCAL_GROUP);
    },
  },
  created() {
    this.fetchGroups(this.id);
  },
  methods: {
    addGroup(): void {
      this.addGroupDialog = true;
    },

    closeDialog(): void {
      this.addGroupDialog = false;
    },

    groupAdded(): void {
      this.fetchGroups(this.id);
      this.addGroupDialog = false;
    },

    filtered(): any[] {
      return selectedFilter(this.groups, this.search, 'id');
    },

    viewGroup(group: any): void {
      this.$router.push({
        name: RouteName.LocalGroup,
        params: { clientId: this.id, groupId: group.id.toString() },
      });
    },

    fetchGroups(id: string): void {
      axios
        .get(`/clients/${id}/local-groups`)
        .then((res) => {
          this.groups = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/tables';

.cert-name {
  text-decoration: underline;
  cursor: pointer;
}

.details-certificates {
  margin-top: 40px;
}

.table-toolbar {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-top: 40px;
  padding-left: 24px;
  margin-bottom: 24px;
}

.search-input {
  max-width: 300px;
}
</style>

