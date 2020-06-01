<template>
  <div>
    <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
      <div v-for="item in csrForm" v-bind:key="item.id" class="row-wrap">
        <div class="label">
          {{ $t('certificateProfile.' + item.label_key) }}
        </div>

        <div>
          <ValidationProvider
            :name="item.id"
            :rules="item.required && 'required'"
            v-slot="{ errors }"
          >
            <v-text-field
              class="form-input"
              :name="item.id"
              type="text"
              v-model="item.default_value"
              :disabled="item.read_only"
              :error-messages="errors"
              data-test="dynamic-csr-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </div>
      <div class="generate-row">
        <div>{{ $t('csr.saveInfo') }}</div>
        <large-button
          @click="generateCsr"
          :disabled="invalid || !disableDone"
          data-test="generate-csr-button"
          >{{ $t('csr.generateCsr') }}</large-button
        >
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
            :disabled="!disableDone"
            >{{ $t('action.previous') }}</large-button
          >
          <large-button
            @click="done"
            :disabled="disableDone"
            data-test="save-button"
            >{{ $t(saveButtonText) }}</large-button
          >
        </div>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.done',
    },
    // Creating Key + CSR or just CSR
    keyAndCsr: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    ...mapGetters(['csrForm']),
  },
  data() {
    return {
      disableDone: true,
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
      const tokenId = this.$store.getters.csrTokenId;

      if (this.keyAndCsr) {
        // Create key and CSR
        this.$store.dispatch('generateKeyAndCsr', tokenId).then(
          (response) => {
            this.disableDone = false;
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
      } else {
        // Create only CSR
        this.$store.dispatch('generateCsr').then(
          (response) => {
            this.disableDone = false;
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.generate-row {
  margin-top: 40px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
</style>
