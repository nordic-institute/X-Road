<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <div>
      <subViewTitle
        v-if="key.usage == 'SIGNING'"
        :title="$t('keys.signDetailsTitle')"
        @close="close"
      />
      <subViewTitle
        v-if="key.usage == 'AUTHENTICATION'"
        :title="$t('keys.authDetailsTitle')"
        @close="close"
      />
      <div class="details-view-tools">
        <large-button
          v-if="canDelete"
          @click="confirmDelete = true"
          outlined
        >{{$t('action.delete')}}</large-button>
      </div>
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="edit-row">
        <div>{{$t('fields.keys.friendlyName')}}</div>
        <ValidationProvider
          rules="required"
          name="keys.friendlyName"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            v-model="key.name"
            single-line
            class="code-input"
            name="keys.friendlyName"
            type="text"
            :maxlength="255"
            :error-messages="errors"
            :disabled="!canEdit"
            @input="touched = true"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div>
        <h3 class="info-title">{{$t('keys.keyInfo')}}</h3>
        <div class="info-row">
          <div class="row-title">{{$t('keys.keyId')}}</div>
          <div class="row-data">{{key.id}}</div>
        </div>
        <div class="info-row">
          <div class="row-title">{{$t('keys.label')}}</div>
          <div class="row-data">{{key.label}}</div>
        </div>
        <div class="info-row">
          <div class="row-title">{{$t('keys.readOnly')}}</div>
          <div class="row-data">{{key.read_only}}</div>
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

    <!-- Confirm dialog delete Key -->
    <confirmDialog
      :dialog="confirmDelete"
      title="keys.deleteTitle"
      text="keys.deleteKeyText"
      @cancel="confirmDelete = false"
      @accept="deleteKey()"
    />
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of a key
 */
import Vue from 'vue';
import _ from 'lodash';
import * as api from '@/util/api';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Permissions, UsageTypes } from '@/global';
import { Key } from '@/types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    ConfirmDialog,
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
      confirmDelete: false,
      touched: false,
      saveBusy: false,
      key: {} as Key,
    };
  },
  computed: {
    canEdit(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_KEY_FRIENDLY_NAME,
      );
    },
    canDelete(): boolean {
      if (this.key.usage === UsageTypes.SIGNING) {
        return this.$store.getters.hasPermission(Permissions.DELETE_SIGN_KEY);
      }

      if (this.key.usage === UsageTypes.AUTHENTICATION) {
        return this.$store.getters.hasPermission(Permissions.DELETE_AUTH_KEY);
      }

      return this.$store.getters.hasPermission(Permissions.DELETE_KEY);
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      this.saveBusy = true;

      api
        .patch(`/keys/${this.id}`, this.key)
        .then((res: any) => {
          this.saveBusy = false;
          this.$bus.$emit('show-success', 'keys.keySaved');
          this.close();
        })
        .catch((error: any) => {
          this.saveBusy = false;
          this.$store.dispatch('showError', error);
        });
    },

    fetchData(id: string): void {
      api
        .get(`/keys/${id}`)
        .then((res: any) => {
          this.key = res.data;
        })
        .catch((error: any) => {
          this.$store.dispatch('showError', error);
        });
    },
    deleteKey(): void {
      this.confirmDelete = false;

      api
        .remove(`/keys/${this.id}`)
        .then((res: any) => {
          this.$bus.$emit('show-success', 'keys.keyDeleted');
          this.close();
        })
        .catch((error: any) => {
          this.$store.dispatch('showError', error);
        });
    },
  },
  created() {
    this.fetchData(this.id);
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/detail-views';
</style>

