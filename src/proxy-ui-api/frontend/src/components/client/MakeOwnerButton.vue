<template>
  <div>
    <LargeButton
      data-test="make-owner-button"
      outlined
      @click="confirmMakeOwner = true"
      >{{ $t('client.action.makeOwner.button') }}</LargeButton
    >

    <!-- Confirm dialog for make owner -->
    <simpleDialog
      :dialog="confirmMakeOwner"
      :loading="makeOwnerLoading"
      saveButtonText="client.action.makeOwner.button"
      title="client.action.makeOwner.confirmTitle"
      @cancel="confirmMakeOwner = false"
      @save="makeOwner()"
    >
      <div slot="content">
        {{ $t('client.action.makeOwner.confirmText1') }}
        <br />
        <br />
        <b>{{ id }}</b>
        <br />
        <br />
        {{ $t('client.action.makeOwner.confirmText2') }}
      </div>
    </simpleDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import LargeButton from '@/components/ui/LargeButton.vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

export default Vue.extend({
  components: {
    LargeButton,
    SimpleDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmMakeOwner: false as boolean,
      makeOwnerLoading: false as boolean,
    };
  },

  methods: {
    makeOwner(): void {
      this.makeOwnerLoading = true;

      api
        .put(`/clients/${this.id}/make-owner`, {})
        .then(
          () => {
            this.$store.dispatch(
              'showSuccess',
              'client.action.makeOwner.success',
            );
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmMakeOwner = false;
          this.makeOwnerLoading = false;
        }); 
    },
  },
});
</script>
