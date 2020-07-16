<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle :title="$t('endpoints.details')" @close="close" />
      <div class="delete-wrap">
        <large-button
          v-if="showDelete"
          @click="showDeletePopup()"
          outlined
          data-test="delete-endpoint"
          >{{ $t('action.delete') }}</large-button
        >
      </div>
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="dlg-edit-row">
        <div class="dlg-row-title long-row-title">
          {{ $t('endpoints.httpRequestMethod') }}
        </div>
        <v-select
          class="dlg-row-input"
          @input="touched = true"
          data-test="endpoint-method"
          v-model="endpoint.method"
          :items="methods"
        />
      </div>

      <div class="dlg-edit-row">
        <div class="dlg-row-title long-row-title">
          {{ $t('endpoints.path') }}
        </div>
        <ValidationProvider
          rules="required"
          ref="path"
          name="path"
          class="validation-provider dlg-row-input"
          v-slot="{ errors }"
        >
          <v-text-field
            v-model="endpoint.path"
            single-line
            :error-messages="errors"
            name="path"
            data-test="endpoint-path"
            @input="touched = true"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="dlg-edit-row helper-text">
        <div class="dlg-row-title long-row-title"></div>
        <div>
          <div>{{ $t('endpoints.endpointHelp1') }}</div>
          <div>{{ $t('endpoints.endpointHelp2') }}</div>
          <div>{{ $t('endpoints.endpointHelp3') }}</div>
          <div>{{ $t('endpoints.endpointHelp4') }}</div>
        </div>
      </div>

      <v-card flat>
        <div class="footer-button-wrap">
          <large-button @click="close()" outlined>{{
            $t('action.cancel')
          }}</large-button>
          <large-button
            class="save-button"
            :loading="saveBusy"
            @click="saveEndpoint()"
            :disabled="!touched || invalid"
            >{{ $t('action.save') }}</large-button
          >
        </div>
      </v-card>
    </ValidationObserver>

    <!-- Confirm dialog delete REST -->
    <confirmDialog
      :dialog="confirmDelete"
      title="endpoints.deleteTitle"
      text="endpoints.deleteEndpointText"
      @cancel="confirmDelete = false"
      @accept="deleteEndpoint(id)"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { Permissions } from '@/global';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { Endpoint } from '@/openapi-types';

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
      endpoint: {} as Endpoint,
      confirmDelete: false,
      saveBusy: false,
      touched: false,
      methods: [
        { text: this.$t('endpoints.all'), value: '*' },
        { text: 'GET', value: 'GET' },
        { text: 'POST', value: 'POST' },
        { text: 'PUT', value: 'PUT' },
        { text: 'PATCH', value: 'PATCH' },
        { text: 'DELETE', value: 'DELETE' },
        { text: 'HEAD', value: 'HEAD' },
        { text: 'OPTIONS', value: 'OPTIONS' },
        { text: 'TRACE', value: 'TRACE' },
      ],
    };
  },
  computed: {
    showDelete(): boolean {
      return this.$store.getters.hasPermission(Permissions.DELETE_ENDPOINT);
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },
    showDeletePopup(): void {
      this.confirmDelete = true;
    },
    deleteEndpoint(id: string): void {
      api
        .remove(`/endpoints/${id}`)
        .then(() => {
          this.$store.dispatch('showSuccess', 'endpoints.deleteSuccess');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
          this.confirmDelete = false;
        });
    },
    saveEndpoint(): void {
      api
        .patch(`/endpoints/${this.endpoint.id}`, this.endpoint)
        .then(() => {
          this.$store.dispatch('showSuccess', 'endpoints.editSuccess');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    fetchData(id: string): void {
      api
        .get<Endpoint>(`/endpoints/${id}`)
        .then((endpoint) => {
          this.endpoint = endpoint.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        });
    },
  },
  created(): void {
    this.fetchData(this.id);
  },
});
</script>

<style lang="scss" scoped>
@import 'src/assets/dialogs';

.delete-wrap {
  margin-top: 50px;
  display: flex;
  justify-content: flex-end;
}

.dlg-edit-row .dlg-row-title {
  min-width: 200px;
}

.dlg-row-input {
  max-width: 400px;
}

.footer-button-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}

.save-button {
  margin-left: 20px;
}

.helper-text {
  color: $XRoad-Grey60;
}
</style>
