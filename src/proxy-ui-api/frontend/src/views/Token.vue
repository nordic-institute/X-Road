<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle :title="$t('keys.tokenDetails')" @close="close" />
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="edit-row">
        <div>{{$t('keys.friendlyName')}}</div>
        <ValidationProvider
          rules="required"
          name="keys.friendlyName"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            v-model="token.name"
            single-line
            class="code-input"
            name="keys.friendlyName"
            type="text"
            :maxlength="255"
            :error-messages="errors"
            @input="touched = true"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div>
        <h3 class="info-title">{{$t('keys.tokenInfo')}}</h3>
        <div class="info-row">
          <div class="row-title">{{$t('keys.tokenId')}}</div>
          <div class="row-data">{{token.id}}</div>
        </div>
        <div class="info-row">
          <div class="row-title">{{$t('keys.type')}}</div>
          <div class="row-data">{{token.type}}</div>
        </div>
      </div>

      <v-card flat>
        <div class="footer-button-wrap">
          <large-button @click="close()" outlined>{{$t('action.cancel')}}</large-button>
          <large-button
            class="save-button"
            :loading="saveBusy"
            @click="save()"
            :disabled="!touched || invalid"
          >{{$t('action.save')}}</large-button>
        </div>
      </v-card>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of a token.
 */
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LargeButton from '@/components/LargeButton.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    LargeButton,
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      touched: false,
      saveBusy: false,
      token: {
        id: '999056789ABCDEF0123456789ABCDEF0123456789ABCDEF',
        name: 'softToken-2',
        type: 'SOFTWARE',
      },
    };
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      // TODO will be implemented later
      this.saveBusy = true;
    },

    fetchData(id: string): void {
      // TODO will be implemented later
    },
  },
  created() {
    this.fetchData(this.id);
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/detail-views';
</style>

