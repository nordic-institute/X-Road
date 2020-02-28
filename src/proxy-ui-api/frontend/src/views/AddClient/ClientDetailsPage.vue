<template>
  <div class="content">
    <div class="info-block">
      <div>
        {{$t('wizard.clientInfo1')}}
        <br />
        <br />
        {{$t('wizard.clientInfo2')}}
      </div>
      <div class="action-block">
        <large-button @click="showSelectClient = true" outlined>{{$t('wizard.selectClient')}}</large-button>
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
        <div v-if="selectedMember">{{selectedMember.member_name}}</div>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />

        <ValidationProvider name="addClient.memberClass" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="memberClass"
          ></v-text-field>
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
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.subsystemCode" helpText="wizard.client.subsystemCodeTooltip" />

        <ValidationProvider name="addClient.subsystemCode" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="subsystemCode"
          ></v-text-field>
        </ValidationProvider>
      </div>
      <div v-if="duplicateClient" class="duplicate-warning">Member already exists</div>
      <div class="button-footer">
        <div class="button-group">
          <large-button outlined @click="cancel">{{$t('action.cancel')}}</large-button>
        </div>
        <large-button @click="done" :disabled="invalid || duplicateClient">{{$t('action.next')}}</large-button>
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
import SelectClientDialog from './SelectClientDialog.vue';
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
  computed: {
    ...mapGetters(['localMembers']),

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
          this.$bus.$emit('show-error', error.message);
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
@import '../../assets/colors';
@import '../../assets/shared';

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

.row-wrap {
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

.duplicate-warning {
  margin-left: 230px;
  margin-top: 10px;
  color: #ff5252;
  font-size: 12px;
}
</style>

