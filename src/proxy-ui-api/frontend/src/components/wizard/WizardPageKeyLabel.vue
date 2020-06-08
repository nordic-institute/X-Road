<template>
  <div>
    {{ $t('wizard.signKey.info') }}
    <div class="row-wrap">
      <FormLabel labelText="wizard.signKey.keyLabel" />
      <v-text-field
        class="form-input"
        type="text"
        v-model="keyLabel"
        data-test="key-label-button"
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
      <large-button @click="done" data-test="next-button">{{
        $t('action.next')
      }}</large-button>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import FormLabel from '@/components/ui/FormLabel.vue';

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
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
      disableDone: true,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
