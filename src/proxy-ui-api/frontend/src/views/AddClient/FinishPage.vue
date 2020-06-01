<template>
  <div data-test="finish-content">
    <p>
      {{ $t('wizard.finish.infoLine1') }}
      <br />
      {{ $t('wizard.finish.infoLine2') }}
    </p>
    <br />
    <p>{{ $t('wizard.finish.todo1') }}</p>
    <p>{{ $t('wizard.finish.todo2') }}</p>
    <p>{{ $t('wizard.finish.todo3') }}</p>
    <br />
    <br />
    <p>{{ $t('wizard.finish.note') }}</p>
    <p></p>

    <div class="button-footer">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          :disabled="disableCancel"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >
      </div>

      <div>
        <large-button
          @click="previous"
          outlined
          :disabled="disableCancel"
          class="previous-button"
          data-test="previous-button"
          >{{ $t('action.previous') }}</large-button
        >

        <large-button @click="done" data-test="submit-button">{{
          $t('action.submit')
        }}</large-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';

export default Vue.extend({
  components: {
    LargeButton,
  },
  computed: {
    ...mapGetters(['csrForm']),
  },
  data() {
    return {
      disableCancel: false,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.disableCancel = true;

      this.$store.dispatch('createClient').then(
        () => {
          this.generateCsr();
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.disableCancel = false;
        },
      );
    },

    generateCsr(): void {
      const tokenId = this.$store.getters.csrTokenId;

      this.$store.dispatch('generateKeyAndCsr', tokenId).then(
        () => {
          this.disableCancel = false;
          this.$emit('done');
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.disableCancel = false;
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
