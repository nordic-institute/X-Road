
<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle :title="service.service_code" @close="close" />

      <template>
        <div class="cert-hash">{{$t('services.serviceParameters')}}</div>
      </template>
    </div>

    <div class="apply-to-all">
      <div class="apply-to-all-text">{{$t('services.applyToAll')}}</div>
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="edit-row">
        <div class="edit-title">
          {{$t('services.serviceUrl')}}
          <helpIcon :text="$t('services.urlTooltip')" />
        </div>

        <div class="edit-input">
          <ValidationProvider
            rules="required|wsdlUrl"
            name="serviceUrl"
            class="validation-provider"
            v-slot="{ errors }"
          >
            <v-text-field
              v-model="service.url"
              @input="setTouched()"
              single-line
              class="description-input"
              name="serviceUrl"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <v-checkbox @change="setTouched()" v-model="url_all" color="primary" class="table-checkbox"></v-checkbox>
      </div>

      <div class="edit-row">
        <div class="edit-title">
          {{$t('services.timeoutSec')}}
          <helpIcon :text="$t('services.timeoutTooltip')" />
        </div>
        <div class="edit-input">
          <ValidationProvider
            :rules="{ required: true, between: { min: 0, max: 1000 } }"
            name="serviceTimeout"
            class="validation-provider"
            v-slot="{ errors }"
          >
            <v-text-field
              v-model="service.timeout"
              single-line
              @input="setTouched()"
              type="number"
              style="max-width: 200px;"
              name="serviceTimeout"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
          <!-- 0 - 1000 -->
        </div>

        <v-checkbox
          @change="setTouched()"
          v-model="timeout_all"
          color="primary"
          class="table-checkbox"
        ></v-checkbox>
      </div>

      <div class="edit-row">
        <div class="edit-title">
          {{$t('services.verifyTls')}}
          <helpIcon :text="$t('services.tlsTooltip')" />
        </div>
        <div class="edit-input">
          <v-checkbox
            :disabled="!isHttps"
            @change="setTouched()"
            v-model="service.ssl_auth"
            color="primary"
            class="table-checkbox"
          ></v-checkbox>
        </div>

        <v-checkbox
          @change="setTouched()"
          v-model="ssl_auth_all"
          color="primary"
          class="table-checkbox"
        ></v-checkbox>
      </div>

      <div class="button-wrap">
        <large-button :disabled="invalid || disableSave" @click="save()">{{$t('action.save')}}</large-button>
      </div>
    </ValidationObserver>

    <div class="group-members-row">
      <div class="row-title">{{$t('access.accessRights')}}</div>
      <div class="row-buttons">
        <large-button
          :disabled="!hasMembers"
          outlined
          @click="removeAllMembers()"
        >{{$t('action.removeAll')}}</large-button>
        <large-button
          outlined
          class="add-members-button"
          @click="showAddMembersDialog()"
        >{{$t('access.addSubjects')}}</large-button>
      </div>
    </div>

    <v-card flat>
      <table class="xrd-table group-members-table">
        <tr>
          <th>{{$t('localGroup.name')}}</th>
          <th>{{$t('localGroup.id')}}</th>
          <th></th>
        </tr>
        <template v-if="accessRights">
          <tr v-for="groupMember in accessRights" v-bind:key="groupMember.id">
            <td>{{groupMember.name}}</td>
            <td>{{groupMember.id}}</td>

            <td>
              <div class="button-wrap">
                <v-btn
                  small
                  outlined
                  rounded
                  color="primary"
                  class="xrd-small-button"
                  @click="removeMember(groupMember)"
                >{{$t('action.remove')}}</v-btn>
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="footer-buttons-wrap">
        <large-button @click="close()">{{$t('action.close')}}</large-button>
      </div>
    </v-card>

    <!-- Confirm dialog remove member -->
    <confirmDialog
      :dialog="confirmMember"
      title="localGroup.removeTitle"
      text="localGroup.removeText"
      @cancel="confirmMember = false"
      @accept="doRemoveMember()"
    />

    <!-- Confirm dialog remove all members -->
    <confirmDialog
      :dialog="confirmAllMembers"
      title="localGroup.removeAllTitle"
      text="localGroup.removeAllText"
      @cancel="confirmAllMembers = false"
      @accept="doRemoveAllMembers()"
    />

    <!-- Add new members dialog -->
    <addMembersDialog
      :dialog="addMembersDialogVisible"
      :filtered="[]"
      title="access.addSubjectsTitle"
      @cancel="closeMembersDialog"
      @membersAdded="doAddMembers"
    />
  </div>
</template>


<script lang="ts">
import Vue from 'vue';
import _ from 'lodash';
import axios from 'axios';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';
import AddMembersDialog from '@/components/AddMembersDialog.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import HelpIcon from '@/components/HelpIcon.vue';
import LargeButton from '@/components/LargeButton.vue';
import { Service, AccessRightSubject } from '@/types.ts';
import { isValidWsdlURL } from '@/util/helpers';
import {
  ValidationObserver,
  ValidationProvider,
  withValidation,
} from 'vee-validate';

type NullableSubject = undefined | AccessRightSubject;

export default Vue.extend({
  components: {
    SubViewTitle,
    AddMembersDialog,
    ConfirmDialog,
    HelpIcon,
    LargeButton,
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    serviceId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      touched: false,
      confirmGroup: false,
      confirmMember: false,
      confirmAllMembers: false,
      selectedMember: undefined as NullableSubject,
      description: undefined,
      url: '',
      addMembersDialogVisible: false,
      timeout: 23,
      accessRights: [
        {
          id: 'GLOBALGROUP:DEV:security-server-owners',
          name: 'Mock 1 security server owners',
        },
        {
          id: 'GLOBALGROUP:DEV:security-server-owneropos',
          name: 'Mock security server owners',
        },
      ],
      url_all: false,
      timeout_all: false,
      ssl_auth_all: false,
      service: {
        service_id: '',
        code: '',
        timeout: 0,
        ssl_auth: true,
        url: '',
      } as Service,
    };
  },
  computed: {
    isHttps(): boolean {
      if (this.service.url.startsWith('https')) {
        return true;
      }
      return false;
    },
    hasMembers(): boolean {
      if (this.accessRights && this.accessRights.length > 0) {
        return true;
      }
      return false;
    },

    disableSave(): boolean {
      // service is undefined --> can't save
      if (!this.service) {
        return true;
      }

      // inputs are not touched
      if (!this.touched) {
        return true;
      }

      return false;
    },
  },

  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      axios
        .patch(`/services/${this.serviceId}`, {
          service: this.service,
          timeout_all: this.timeout_all,
          url_all: this.url_all,
          ssl_auth_all: this.ssl_auth_all,
        })
        .then((res) => {
          this.service = res.data;
          this.$bus.$emit('show-success', 'Service saved');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    setTouched(): void {
      this.touched = true;
    },

    fetchData(serviceId: string): void {
      axios
        .get(`/services/${serviceId}`)
        .then((res) => {
          this.service = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });

      axios
        .get(`/services/${serviceId}/access-rights`)
        .then((res) => {
          // this.service = res.data;
          console.log(res.data);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    showAddMembersDialog(): void {
      this.addMembersDialogVisible = true;
    },

    doAddMembers(selectedIds: string[]): void {
      this.addMembersDialogVisible = false;
      // this.fetchData(this.clientId);

      axios
        .post(`/services/${this.serviceId}/access-rights`, {
          items: selectedIds,
        })
        .then((res) => {
          // this.service = res.data;
          console.log(res.data);
          this.fetchData(this.serviceId);
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    closeMembersDialog(): void {
      this.addMembersDialogVisible = false;
    },

    removeAllMembers(): void {
      this.confirmAllMembers = true;
    },

    doRemoveAllMembers(): void {
      const ids: any = [];
      this.accessRights.forEach((member: any) => {
        ids.push(member.id);
      });

      this.removeArrayOfMembers(ids);
      this.confirmAllMembers = false;
    },

    removeMember(member: any): void {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveMember() {
      const member: AccessRightSubject = this
        .selectedMember as AccessRightSubject;

      if (member && member.id) {
        this.removeArrayOfMembers([member.id]);
      }

      this.confirmMember = false;
      this.selectedMember = undefined;
    },

    removeArrayOfMembers(members: any) {
      axios
        .post(`/services/${this.serviceId}/delete`, {
          items: members,
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        })
        .finally(() => {
          this.fetchData(this.serviceId);
        });
    },
  },
  created() {
    this.fetchData(this.serviceId);
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';
@import '../assets/tables';

.apply-to-all {
  display: flex;
  justify-content: flex-end;

  .apply-to-all-text {
    width: 100px;
  }
}

.edit-row {
  display: flex;
  align-items: baseline;
  //  border: 1px solid gray;

  .description-input {
    width: 100%;
    max-width: 450px;
  }

  .edit-title {
    display: flex;
    align-content: center;
    min-width: 200px;
    //    border: 1px solid green;
    margin-right: 20px;
  }

  .edit-input {
    display: flex;
    align-content: center;
    //    border: 1px solid blue;
    width: 100%;
  }
}

.edit-row > *:last-child {
  margin-left: 20px;
  width: 100px;
  max-width: 100px;
  min-width: 100px;
  //border: 1px solid red;
  margin-left: auto;
  margin-right: 0;
}

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}
.row-title {
  width: 100%;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
}
.row-buttons {
  display: flex;
}

.add-members-button {
  margin-left: 20px;
}

.cert-hash {
  margin-top: 50px;
  display: flex;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.group-members-table {
  margin-top: 10px;
  width: 100%;
  th {
    text-align: left;
  }
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.footer-buttons-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}
</style>

