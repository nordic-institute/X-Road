<template>
  <subViewTitle :title="asdfadsf" @close="close" />

  <h3>header</h3>
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
    client_id: {
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
      api
        .get(`/service-client/${this.id}`)
        .then( (response: any) => this.accessRights = response.data)
        .catch( (error: any) =>
          this.$store.dispatch('showError', error));
    },
  },
  created() {
    // this.fetchData();
  },

});
</script>

<style lang="scss" scoped>

</style>
