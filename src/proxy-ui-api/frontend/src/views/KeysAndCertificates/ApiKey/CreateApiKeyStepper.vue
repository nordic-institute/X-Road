<template>
  <v-container class="xrd-view-common justify-center wrapper">
    <sub-view-title :title="$t('apiKey.createApiKey.title')" :show-close="true" @close="close"></sub-view-title>
    <v-stepper :alt-labels="true" v-model="step" class="stepper mt-2">
      <v-stepper-header class="stepper-header">
        <v-stepper-step :complete="step > 1" step="1">{{$t('apiKey.createApiKey.step.roles.name')}}</v-stepper-step>
        <v-divider />
        <v-stepper-step :complete="step > 2" step="2"
          >{{$t('apiKey.createApiKey.step.keyDetails.name')}}</v-stepper-step
        >
      </v-stepper-header>
      <v-stepper-items class="stepper-items-wrapper">
        <v-stepper-content step="1" class="pa-0">
          <v-row class="mb-5">
            <v-col>
              <h3>{{$t('apiKey.createApiKey.step.roles.description')}}</h3>
            </v-col>
          </v-row>
          <v-row no-gutters v-for="role in roles" :key="role">
            <v-col class="checkbox-wrapper">
              <v-checkbox height="10px" :value="role" :label="$t(`apiKey.role.${role}`)"/>
            </v-col>
          </v-row>
          <v-row class="stepper-item-footer mt-12" no-gutters>
            <v-col>
              <large-button outlined @click="close">
                {{$t('action.cancel')}}
              </large-button>
            </v-col>
            <v-col class="text-right">
              <large-button>{{$t('action.next')}}</large-button>
            </v-col>
          </v-row>
        </v-stepper-content>
        <v-stepper-content step="2"> </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { Roles } from '@/global';

export default Vue.extend({
  name: 'CreateApiKeyStepper',
  components: {
    LargeButton,
    SubViewTitle,
  },
  data() {
    return {
      step: 1,
      roles: Roles,
    };
  },
  methods: {
    close(): void {
      this.$router.back();
    }
  }
});
</script>

<style scoped lang="scss">
@import '../../../assets/detail-views';
@import '../../../assets/colors';
.wrapper {
  max-width: 850px;
  height: 100%;
  width: 100%;
}
.stepper {
  box-shadow: unset;
}
.stepper-header {
  box-shadow: unset;
  width: 50%;
  margin: auto;
}
.stepper-item-footer {
  margin-top: 20px;
  padding-top: 30px;
  border-top: 1px solid $XRoad-Grey40;
}
.stepper-items-wrapper {

}
.checkbox-wrapper {
  border-bottom: solid 1px $XRoad-Grey10;
}
h3 {
  color: $XRoad-Grey60;
  font-weight: 400;
}
</style>
