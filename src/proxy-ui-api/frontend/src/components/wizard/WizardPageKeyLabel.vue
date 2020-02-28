<template>
  <div class="content">
    {{$t('wizard.signKey.info')}}
    <div class="row-wrap">
      <FormLabel labelText="wizard.signKey.keyLabel" />
      <v-text-field class="form-input" type="text" v-model="keyLabel"></v-text-field>
    </div>
    <div class="button-footer">
      <div class="button-group">
        <large-button outlined @click="cancel" :disabled="!disableDone">{{$t('action.cancel')}}</large-button>
      </div>
      <large-button @click="done">{{$t('action.next')}}</large-button>
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
@import '../../assets/colors';
@import '../../assets/shared';

.generate-row {
  margin-top: 40px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}

.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.label {
  width: 230px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.form-input {
  width: 300px;
}

.button-footer {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  border-top: solid 1px $XRoad-Grey40;
  margin-top: 40px;
  padding-top: 30px;
}

.button-group {
  display: flex;
  flex-direction: row;
  align-items: baseline;

  :not(:last-child) {
    margin-right: 20px;
  }
}
</style>

