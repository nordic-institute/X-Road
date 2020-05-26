<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
        <div v-if="initServerMemberName" data-test="selected-member-name">{{initServerMemberName}}</div>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />

        <ValidationProvider name="addClient.memberClass" rules="required" v-slot="{ errors }">
          <v-select
            v-model="memberClass"
            :items="memberClasses"
            data-test="member-class-input"
            class="form-input"
          ></v-select>
        </ValidationProvider>
      </div>
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberCode" helpText="wizard.client.memberCodeTooltip" />

        <ValidationProvider name="addClient.memberCode" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="memberCode"
            data-test="member-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <FormLabel
          labelText="fields.securityServerCode"
          helpText="initialConfiguration.member.serverCodeHelp"
        />

        <ValidationProvider name="securityServerCode" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="securityServerCode"
            data-test="security-server-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="button-footer">
        <v-spacer></v-spacer>
        <div>
          <large-button
            v-if="showPreviousButton"
            @click="previous"
            outlined
            class="previous-button"
            data-test="previous-button"
          >{{$t('action.previous')}}</large-button>
          <large-button
            :disabled="invalid"
            @click="done"
            data-test="save-button"
          >{{$t(saveButtonText)}}</large-button>
        </div>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import FormLabel from '@/components/ui/FormLabel.vue';
import { Key, Token } from '@/types';
import { CsrFormatTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
    ValidationObserver,
    ValidationProvider,
    FormLabel,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      csrFormatList: Object.values(CsrFormatTypes),
    };
  },
  computed: {
    ...mapGetters(['memberClasses', 'initServerMemberName']),

    memberClass: {
      get(): string {
        return this.$store.getters.initServerMemberClass;
      },
      set(value: string) {
        this.$store.commit('storeInitServerMemberClass', value);
      },
    },
    memberCode: {
      get(): string {
        return this.$store.getters.initServerMemberCode;
      },
      set(value: string) {
        this.$store.commit('storeInitServerMemberCode', value);
      },
    },

    securityServerCode: {
      get(): string {
        return this.$store.getters.initServerSSCode;
      },
      set(value: string) {
        this.$store.commit('storeInitServerSSCode', value);
      },
    },
  },
  methods: {
    done(): void {
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },
  },

  watch: {
    memberClasses(val) {
      // Set first member class selected if there is only one
      if (val && val.length === 1) {
        this.$store.commit('storeInitServerMemberClass', val[0]);
      }
    },
    memberClass(val) {
      if (val) {
        // Update member name when info changes
        this.$store.dispatch('fetchInitServerMemberName');
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.readonly-info-field {
  max-width: 300px;
  height: 60px;
  padding-top: 12px;
}
</style>

