<template>
  <div class="xrd-tab-max-width xrd-view-common">
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
            :loading="loading"
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
import * as api from '@/util/api';
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
      loading: false,
      token: {},
    };
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      this.saveBusy = true;

      api
        .put(`/tokens/${this.id}`, this.token)
        .then((res) => {
          this.$bus.$emit('show-success', 'keys.tokenSaved');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.loading = false;
        });
    },

    fetchData(id: string): void {
      this.loading = true;
      api
        .get(`/tokens/${this.id}`)
        .then((res) => {
          this.token = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.loading = false;
        });
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

