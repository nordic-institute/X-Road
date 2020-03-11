
<template>
  <div class="view-wrap">
    <subViewTitle class="view-title" :title="$t('wizard.subsystem.title')" :showClose="false" />

    <SubsystemDetailsPage
      @cancel="cancel"
      @done="clientDetailsReady"
      saveButtonText="action.submit"
    >
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
        >{{$t('wizard.subsystem.selectSubsystem')}}</large-button>
      </div>
    </SubsystemDetailsPage>
    <SelectClientDialog :dialog="showSelectClient" @cancel="showSelectClient = false" />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import SubsystemDetailsPage from './SubsystemDetailsPage.vue';
import SelectClientDialog from '@/views/AddClient/SelectClientDialog.vue';

import { Key, Token } from '@/types';
import { RouteName, UsageTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
    SubsystemDetailsPage,
    SelectClientDialog,
  },
  props: {
    clientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      currentStep: 1,
      showSelectClient: false,
    };
  },
  computed: {
    ...mapGetters(['localMembersIds']),
  },
  methods: {
    save(): void {
      this.$store.dispatch('fetchCsrForm').then(
        (response) => {
          this.currentStep = 2;
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    cancel(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },

    clientDetailsReady(): void {
      this.$store.dispatch('showSuccess', 'subsystem_code');
      /*

      this.$bus.$emit(
        'show-indefinite',
        'viesti',
        this.$store.dispatch('fetchCsrForm'),
      ); */
    },

    done(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },
    fetchData(): void {
      // Fetch "parent" client from backend
      this.$store.dispatch('fetchClient', this.clientId).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
  },

  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.view-wrap {
  width: 100%;
  max-width: 850px;
  margin: 10px;
}

.view-title {
  width: 100%;
  max-width: 100%;
  margin-bottom: 30px;
}

.stepper-content {
  width: 100%;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.stepper {
  width: 100%;
}

.noshadow {
  -webkit-box-shadow: none;
  -moz-box-shadow: none;
  box-shadow: none;
}
</style>