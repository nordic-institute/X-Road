<template>
  <div class="content">
    <div class="info-block">
      <slot>
        <div>
          {{$t('wizard.subsystem.info1')}}
          <br />
          <br />
          {{$t('wizard.subsystem.info2')}}
        </div>
        <div class="action-block">
          <large-button
            @click="showSelectClient = true"
            outlined
            data-test="select-client-button"
          >{{$t('wizard.selectClient')}}</large-button>
        </div>
      </slot>
    </div>

    <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
        <div v-if="client" data-test="selected-member-name">{{client.member_name}}</div>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />
        <div v-if="client" data-test="selected-member-name">{{client.member_class}}</div>
      </div>
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberCode" helpText="wizard.client.memberCodeTooltip" />
        <div v-if="client" data-test="selected-member-name">{{client.member_code}}</div>
      </div>

      <div v-if="showSubsystem" class="row-wrap">
        <FormLabel labelText="wizard.subsystemCode" helpText="wizard.client.subsystemCodeTooltip" />

        <ValidationProvider name="addClient.subsystemCode" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="subsystemCode"
            data-test="subsystem-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>
      <div v-if="duplicateClient" class="duplicate-warning">{{$t('wizard.client.memberExists')}}</div>
      <div class="button-footer">
        <div class="button-group">
          <large-button outlined @click="cancel" data-test="cancel-button">{{$t('action.cancel')}}</large-button>
        </div>
        <large-button
          @click="done"
          :disabled="invalid || duplicateClient"
          data-test="next-button"
        >{{$t('action.next')}}</large-button>
      </div>
    </ValidationObserver>

    <SelectClientDialog :dialog="showSelectClient" @cancel="showSelectClient = false" />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import FormLabel from '@/components/ui/FormLabel.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SelectClientDialog from '@/views/AddClient/SelectClientDialog.vue';
import { Client } from '@/types';

import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showSubsystem: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    ...mapGetters(['client', 'localMembers']),
    memberClass: {
      get(): string {
        return this.$store.getters.memberClass;
      },
      set(value: string) {
        this.$store.commit('setMemberClass', value);
      },
    },

    memberCode: {
      get(): string {
        return this.$store.getters.memberCode;
      },
      set(value: string) {
        this.$store.commit('setMemberCode', value);
      },
    },

    subsystemCode: {
      get(): string {
        return this.$store.getters.subsystemCode;
      },
      set(value: string) {
        this.$store.commit('setSubsystemCode', value);
      },
    },

    selectedMember: {
      get(): Client {
        return this.$store.getters.selectedMember;
      },
      set(value: Client) {
        this.$store.commit('setMember', value);
      },
    },

    duplicateClient(): boolean {
      if (!this.memberClass || !this.memberCode || !this.subsystemCode) {
        return false;
      }

      if (
        this.localMembers.some((e: Client) => {
          if (e.member_class.toLowerCase() !== this.memberClass.toLowerCase()) {
            return false;
          }

          if (e.member_code.toLowerCase() !== this.memberCode.toLowerCase()) {
            return false;
          }

          if (e.subsystem_code !== this.subsystemCode) {
            return false;
          }
          return true;
        })
      ) {
        return true;
      }

      return false;
    },
  },
  data() {
    return {
      disableDone: false,
      certificationService: undefined,
      filteredServiceList: [],
      showSelectClient: false as boolean,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
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
  created() {
    this.$store.dispatch('fetchMembers');
    this.$store.dispatch('fetchLocalMembers');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.info-block {
  display: flex;
  flex-direction: row;
  margin-bottom: 40px;

  .action-block {
    margin-top: 30px;
    margin-left: auto;
    margin-right: 0px;
  }
}

.duplicate-warning {
  margin-left: 230px;
  margin-top: 10px;
  color: #ff5252;
  font-size: 12px;
}
</style>

