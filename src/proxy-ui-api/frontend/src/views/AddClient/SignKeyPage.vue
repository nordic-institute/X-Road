<template>
  <div>
    {{ $t('wizard.signKey.info') }}
    <div class="row-wrap">
      <FormLabel labelText="wizard.signKey.keyLabel" />
      <v-text-field
        class="form-input"
        type="text"
        v-model="keyLabel"
        data-test="key-label-input"
      ></v-text-field>
    </div>
    <div class="button-footer">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          :disabled="!disableDone"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >
      </div>
      <div>
        <large-button
          @click="previous"
          outlined
          class="previous-button"
          data-test="previous-button"
          >{{ $t('action.previous') }}</large-button
        >
        <large-button @click="done" data-test="next-button">{{
          $t('action.next')
        }}</large-button>
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

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  computed: {
    ...mapGetters(['csrForm']),
    keyLabel: {
      get(): string {
        return this.$store.getters.keyLabel;
      },
      set(value: string) {
        this.$store.commit('storeKeyLabel', value);
      },
    },
  },
  data() {
    return {
      disableDone: true as boolean,
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
      this.$emit('done');
    },
    generateCsr(): void {
      this.$store.dispatch('generateCsr').then(
        (response) => {
          this.disableDone = false;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
