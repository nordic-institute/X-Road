<template>
  <div data-test="finish-content">
    <p>
      {{$t('wizard.finish.infoLine1')}}
      <br />
      {{$t('wizard.finish.infoLine2')}}
    </p>
    <br />
    <p>{{$t('wizard.finish.todo1')}}</p>
    <p>{{$t('wizard.finish.todo2')}}</p>
    <p>{{$t('wizard.finish.todo3')}}</p>
    <br />
    <br />
    <p>{{$t('wizard.finish.note')}}</p>
    <p></p>

    <div v-if="showRegisterOption" class="row-wrap">
      <FormLabel labelText="wizard.member.register" />
      <v-checkbox
        v-model="registerChecked"
        color="primary"
        class="register-checkbox"
        data-test="register-member-checkbox"
      ></v-checkbox>
    </div>

    <div class="button-footer">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          :disabled="disableCancel"
          data-test="cancel-button"
        >{{$t('action.cancel')}}</large-button>
      </div>

      <div>
        <large-button
          @click="previous"
          outlined
          :disabled="disableCancel"
          class="previous-button"
          data-test="previous-button"
        >{{$t('action.previous')}}</large-button>

        <large-button
          @click="done"
          data-test="submit-button"
          :loading="submitLoading"
        >{{$t('action.submit')}}</large-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import FormLabel from '@/components/ui/FormLabel.vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { AddMemberWizardModes } from '@/global';
import { createClientId } from '@/util/helpers';

export default Vue.extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    FormLabel,
  },
  computed: {
    ...mapGetters([
      'addMemberWizardMode',
      'memberClass',
      'memberCode',
      'reservedMember',
    ]),

    showRegisterOption() {
      if (
        this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
      ) {
        return true;
      }
      return false;
    },
  },
  data() {
    return {
      disableCancel: false as boolean,
      registerChecked: true as boolean,
      submitLoading: false as boolean,
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
      this.submitLoading = true;

      this.$store.dispatch('createMember').then(
        (response) => {
          if (
            this.addMemberWizardMode ===
              AddMemberWizardModes.CERTIFICATE_EXISTS &&
            this.registerChecked
          ) {
            this.registerClient();
          } else if (
            this.addMemberWizardMode === AddMemberWizardModes.CERTIFICATE_EXISTS
          ) {
            this.disableCancel = false;
            this.submitLoading = false;
            this.$emit('done');
          } else {
            this.generateCsr();
          }
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.disableCancel = false;
          this.submitLoading = false;
        },
      );
    },

    generateCsr(): void {
      const tokenId = this.$store.getters.csrTokenId;

      this.$store
        .dispatch('generateKeyAndCsr', tokenId)
        .then(
          (response) => {
            this.$emit('done');
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },

    registerClient(): void {
      const clientId = createClientId(
        this.reservedMember.instanceId,
        this.memberClass,
        this.memberCode,
      );

      this.$store
        .dispatch('registerClient', clientId)
        .then(
          () => {
            this.$emit('done');
          },
          (error) => {
            this.$store.dispatch('showError', error);
            this.$emit('done');
          },
        )
        .finally(() => {
          this.disableCancel = false;
          this.submitLoading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>

