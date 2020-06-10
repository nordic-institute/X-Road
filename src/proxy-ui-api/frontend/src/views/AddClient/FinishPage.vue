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

        <large-button
          @click="done"
          :loading="submitLoading"
          data-test="submit-button"
          >{{ $t('action.submit') }}</large-button
        >
      </div>
    </div>
    <!-- Accept warnings -->
    <warningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localizationParent="wizard.warning"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import WarningDialog from '@/views/InitialConfiguration/WarningDialog.vue';

export default Vue.extend({
  components: {
    LargeButton,
    WarningDialog,
  },
  computed: {
    ...mapGetters(['csrForm']),
  },
  data() {
    return {
      disableCancel: false,
      submitLoading: false as boolean,
      warningInfo: [] as string[],
      warningDialog: false as boolean,
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
      this.createClient(false);
    },
    createClient(ignoreWarnings: boolean): void {
      this.disableCancel = true;
      this.submitLoading = true;

      this.$store.dispatch('createClient', ignoreWarnings).then(
        () => {
          this.generateCsr();
        },
        (error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.$store.dispatch('showError', error);
            this.disableCancel = false;
            this.submitLoading = false;
          }
        },
      );
    },
    cancelSubmit(): void {
      this.disableCancel = false;
      this.submitLoading = false;
      this.warningDialog = false;
    },
    acceptWarnings(): void {
      this.createClient(true);
    },

    generateCsr(): void {
      const tokenId = this.$store.getters.csrTokenId;

      this.$store.dispatch('generateKeyAndCsr', tokenId).then(
        () => {
          this.disableCancel = false;
          this.submitLoading = false;
          this.$emit('done');
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.disableCancel = false;
          this.submitLoading = false;
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
