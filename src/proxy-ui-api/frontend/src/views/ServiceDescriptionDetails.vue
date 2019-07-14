<template>
  <div class="xr-tab-max-width">
    <div>
      <subViewTitle title="WSDL details" @close="close" />
      <template>
        <div class="delete-wrap">
          <v-btn
            v-if="showDelete"
            outline
            round
            color="primary"
            class="xr-big-button"
            @click="confirmDelete = true"
          >{{$t('action.delete')}}</v-btn>
        </div>
      </template>
    </div>

    <div class="edit-row">
      <template>
        <div>Edit URL</div>
        <v-text-field
          v-if="serviceDesc"
          v-model="serviceDesc.url"
          single-line
          hide-details
          class="url-input"
          v-validate="'required|url'"
          data-vv-as="field"
          name="url_field"
          type="text"
          :error-messages="errors.collect('url_field')"
          @change="canSave = true"
        ></v-text-field>
      </template>
    </div>

    <div class="edit-row">
      <template v-if="serviceDesc && serviceDesc.type === 'REST'">
        <div>Edit Service code</div>
        <v-text-field
          v-model="serviceDesc.code"
          single-line
          hide-details
          class="code-input"
          :maxlength="255"
          v-validate="'required'"
        ></v-text-field>
      </template>
    </div>

    <v-card flat>
      <div class="close-button-wrap">
        <v-btn outline round color="primary" class="xr-big-button" @click="close()">cancel</v-btn>
        <v-btn
          round
          color="primary"
          class="xr-big-button elevation-0"
          @click="save()"
          :disabled="!canSave"
        >save</v-btn>
      </div>
    </v-card>

    <!-- Confirm dialog delete WSDL -->
    <confirmDialog
      :dialog="confirmDelete"
      title="services.deleteTitle"
      text="services.deleteWsdlText"
      @cancel="confirmDelete = false"
      @accept="doDeleteServiceDesc()"
    />
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of REST or WSDL service description.
 * Both use the same api.
 */
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    ConfirmDialog,
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
      canSave: false,
      serviceDesc: undefined,
    };
  },
  computed: {
    showDelete(): boolean {
      // TODO
      /*

      return this.$store.getters.hasPermission(Permissions.DELETE_LOCAL_GROUP);
      */
      return true;
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      axios
        .put(`/service-descriptions/${this.id}`, this.serviceDesc)
        .then((res) => {
          this.$bus.$emit('show-success', 'localGroup.descSaved');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    fetchData(id: string): void {
      axios
        .get(`/service-descriptions/${id}`)
        .then((res) => {
          this.serviceDesc = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    doDeleteServiceDesc(): void {
      this.confirmDelete = false;

      axios
        .delete(`/service-descriptions/${this.id}`)
        .then(() => {
          this.$bus.$emit('show-success', 'localGroup.groupDeleted');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
  created() {
    this.fetchData(this.id);
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.edit-row {
  display: flex;
  align-content: center;
  align-items: baseline;
  margin-top: 30px;

  .code-input {
    margin-left: 60px;
  }
  .url-input {
    margin-left: 60px;
  }
}

.delete-wrap {
  margin-top: 50px;
  display: flex;
  justify-content: flex-end;
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.close-button-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}
</style>

