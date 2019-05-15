<template>
  <div>
    <div class="table-toolbar">
      <v-text-field v-model="search" label="Search" single-line hide-details class="search-input">
        <v-icon slot="append" small>fas fa-search</v-icon>
      </v-text-field>
      <v-btn
        v-if="showAddGroup()"
        color="primary"
        @click="addGroup"
        elevation-0
        outline
        round
        class="ma-0 rounded-button elevation-0"
      >Add group</v-btn>
    </div>

    <v-card flat>
      <table class="certificate-table details-certificates">
        <tr>
          <th>Code</th>
          <th>Description</th>
          <th>Member Count</th>
          <th>Updated</th>
        </tr>
        <template v-if="groups && groups.length > 0">
          <tr v-for="group in filtered()" v-bind:key="group.code">
            <td>
              <span class="cert-name" @click="viewGroup(group)">{{group.code}}</span>
            </td>
            <td>{{group.description}}</td>
            <td>{{group.member_count}}</td>
            <td>{{group.updated_at}}</td>
          </tr>
        </template>
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';

import { mapGetters } from 'vuex';
import { Permissions } from '@/global';

export default Vue.extend({
  components: {},
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
      certificate: null,
      groups: [
        {
          id: 'group123',
          code: 'groupcode',
          description: 'description',
          member_count: 10,
          updated_at: '2018-12-15T00:00:00.001Z',
        },
        {
          id: 'group345',
          code: 'groupcode2',
          description: 'description',
          member_count: 5,
          updated_at: '2018-12-15T00:00:00.001Z',
        },
        {
          id: 'ryhmy9',
          code: 'ryhmy9',
          description: 'kiksd',
          member_count: 5,
          updated_at: '2018-12-15T00:00:00.001Z',
        },
      ],
    };
  },
  computed: {
    ...mapGetters(['client', 'certificates']),
  },
  created() {
    this.fetchGroups(this.id);
  },
  methods: {
    addGroup() {
      // TODO will be done in XRDDEV-519
      console.log('add');
    },

    filtered() {
      const mysearch = this.search.toString().toLowerCase();
      if (mysearch.trim() === '') {
        return this.groups;
      }

      console.log(mysearch);

      const re = new RegExp(mysearch, 'i');
      let filtered = this.groups.filter((g) => {
        // Check the grop code
        if (g.code.includes(mysearch)) {
          return true;
        }

        // Check also description
        if (g.description.includes(mysearch)) {
          return true;
        }

        return false;
      });

      return filtered;
    },

    showAddGroup() {
      return true;
    },

    viewGroup(group: any) {
      // TODO will be done in XRDDEV-520
      console.log(group);
    },

    fetchGroups(id: string) {
      return axios
        .get(`/clients/${id}/groups`)
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

<style lang="scss" >
@import '../assets/tables';

.cert-name {
  text-decoration: underline;
  cursor: pointer;
}

.details-certificates {
  margin-top: 40px;
}

// TODO put this in some shared place ?
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

