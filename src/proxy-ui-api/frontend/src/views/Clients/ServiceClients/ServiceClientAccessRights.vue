<template>
  <div class="xrd-tab-max-width">
    <subViewTitle :title="id" @close="close" />

    <v-card flat>
      <table class="xrd-table service-clients-table">
        <tr>
          <th>{{$t('serviceClients.name')}}</th>
          <th>{{$t('serviceClients.id')}}</th>
        </tr>
        <tr>
          <td>{{acl.name}}</td>
          <td>{{acl.id}}</td>
        </tr>
      </table>
    </v-card>

    <h3></h3>

  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {AccessRight} from '@/types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    serviceClientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      accessRights: [] as AccessRight[],
    };
  },
  methods: {
    fetchData() {
      api // /clients/{id}/service-clients/{sc_id}/access-rights
        .get(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`)
        .then( (response: any) => this.accessRights = response.data)
        .catch( (error: any) =>
          this.$store.dispatch('showError', error));


    },
    close() {
      this.$router.go(-1);
    },
  },
  created() {
    this.fetchData();
  },

});
</script>

<style lang="scss" scoped>

</style>
