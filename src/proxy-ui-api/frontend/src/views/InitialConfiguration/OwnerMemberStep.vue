<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
        <div v-if="memberName" data-test="selected-member-name">{{memberName}}</div>
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
import { Key, Token, Client } from '@/types';
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
      memberName: '',
    };
  },
  computed: {
    ...mapGetters(['memberClasses', 'initExistingMembers']),

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

    checkClient(): void {
      // Find if the selectable clients array has a match
      const tempClient = this.initExistingMembers.find((client: Client) => {
        return (
          client.member_code === this.memberCode &&
          client.member_class === this.memberClass
        );
      });
      // Fill the name "field" if it's available
      if (tempClient?.member_name) {
        this.memberName = tempClient.member_name;
      } else {
        // Clear the "field" if not
        this.memberName = '';
      }
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
        this.checkClient();
      }
    },

    memberCode(val) {
      if (val) {
        // Update member name when info changes
        this.checkClient();
      }
    },
  },
  created() {
    this.$store.dispatch('fetchExistingMembers');
  }
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

