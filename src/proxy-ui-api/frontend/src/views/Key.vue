<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle v-if="key.type == 'SIGN'" :title="$t('keys.signDetailsTitle')" @close="close" />
      <subViewTitle v-if="key.type == 'AUTH'" :title="$t('keys.authDetailsTitle')" @close="close" />
      <div class="delete-wrap">
        <large-button
          v-if="showDelete"
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
      @accept="doDeleteServiceDesc()"
    />
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of a key
 */
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import LargeButton from '@/components/LargeButton.vue';

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
      // TODO: mock data will be removed later
      key: {
        id: '09CDEF0123456789ABCDEF',
        name: 'nimi name',
        label: 'olooi',
        type: 'SIGN',
        status: 'REGISTERED',
        read_only: true,
        certificates: [
          {
            issuer_common_name: 'adr.com',
            ocsp_status: 'SUCCESS',
            not_after: '2018-12-15T00:00:00.001Z',
            hash: '8890ABCDEF',
            client_id: 'FI:GOV:123:ABC',
            status: 'DISABLE',
          },
          {
            issuer_common_name: 'popo.com',
            ocsp_status: 'SUCCESS',
            not_after: '2018-12-15T00:00:00.001Z',
            hash: '0997890ABCDEF',
            client_id: 'FI:GOV:123:ABC',
            status: 'IN_USE',
          },
          {
            issuer_common_name: 'iiop.com',
            ocsp_status: 'SUCCESS',
            not_after: '2018-10-15T00:00:00.001Z',
            hash: '89897890ABCDEF',
            client_id: 'FI:GOV:123:ABC',
            status: 'DISABLED',
          },
        ],
      },
    };
  },
  computed: {
    showDelete(): boolean {
      return this.$store.getters.hasPermission(Permissions.DELETE_WSDL);
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      // TODO will be implemented on later task
      this.saveBusy = true;
    },

    fetchData(id: string): void {
      // TODO will be implemented on later task
    },
    doDeleteServiceDesc(): void {
      this.confirmDelete = false;
      // TODO will be implemented on later task
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

