
<template>
  <v-stepper :alt-labels="true" v-model="e1" class="stepper noshadow">
    <v-stepper-header class="noshadow">
      <v-stepper-step :complete="e1 > 1" step="1">Generate Certificate Signing Request</v-stepper-step>
      <v-divider></v-divider>
      <v-stepper-step :complete="e1 > 2" step="2">Subject Distinguished Name</v-stepper-step>
    </v-stepper-header>

    <v-stepper-items class="stepper-content">
      <v-stepper-content step="1">
        <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
          <div class="row-wrap">
            <div class="label">
              {{$t('csr.usage')}}
              <helpIcon text="item.help" />
            </div>

            <ValidationProvider name="crs.usage" rules="required" v-slot="{ errors }">
              <v-select
                :items="usageList"
                :disabled="key.usage != null"
                class="form-input"
                v-model="usage"
              ></v-select>
            </ValidationProvider>
          </div>

          <div class="row-wrap" v-if="usage === usageTypes.SIGNING">
            <div class="label">
              {{$t('csr.client')}}
              <helpIcon text="item.help" />
            </div>

            <ValidationProvider name="crs.client" rules="required" v-slot="{ errors }">
              <v-select
                :items="localMembersIds"
                item-text="id"
                item-value="id"
                class="form-input"
                v-model="client"
              ></v-select>
            </ValidationProvider>
          </div>

          <div class="row-wrap">
            <div class="label">
              {{$t('csr.certificationService')}}
              <helpIcon text="item.help" />
            </div>

            <ValidationProvider name="crs.certService" rules="required" v-slot="{ errors }">
              <v-select
                :items="authList()"
                item-text="name"
                item-value="name"
                class="form-input"
                v-model="certificationService"
              ></v-select>
            </ValidationProvider>
          </div>

          <div class="row-wrap">
            <div class="label">
              {{$t('csr.csrFormat')}}
              <helpIcon text="item.help" />
            </div>

            <ValidationProvider name="crs.crsFormat" rules="required" v-slot="{ errors }">
              <v-select :items="csrFormatList" class="form-input" v-model="csrFormat"></v-select>
            </ValidationProvider>
          </div>

          <div class="button-footer">
            <large-button outlined @click="cancel">{{$t('action.cancel')}}</large-button>
            <large-button :disabled="invalid" @click="save">{{$t('action.continue')}}</large-button>
          </div>
        </ValidationObserver>
      </v-stepper-content>
      <!-- Step 2 -->
      <v-stepper-content step="2">
        <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
          <div v-for="item in form" v-bind:key="item.id" class="row-wrap">
            <div class="label">
              {{item.label_key}}
              <!--   <helpIcon v-if="item.help" :text="item.help" /> -->
            </div>

            <div>
              <ValidationProvider
                :name="item.id"
                :rules="(item.required) && 'required' "
                v-slot="{ errors }"
              >
                <v-text-field
                  class="form-input"
                  :name="item.id"
                  type="text"
                  v-model="item.default_value"
                  :disabled="item.read_only"
                  :error-messages="errors"
                ></v-text-field>
              </ValidationProvider>
            </div>
          </div>

          <div class="button-footer">
            <div class="button-group">
              <large-button outlined @click="cancel">{{$t('action.cancel')}}</large-button>
              <large-button outlined @click="e1 = 1">{{$t('action.previous')}}</large-button>
            </div>
            <large-button @click="generateCsr" :disabled="invalid">{{$t('action.continue')}}</large-button>
          </div>
        </ValidationObserver>
      </v-stepper-content>
    </v-stepper-items>
  </v-stepper>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { mapGetters } from 'vuex';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { Key, Token } from '@/types';
import { RouteName } from '@/global';
import { saveAsFile } from '@/util/helpers';
import * as api from '@/util/api';

enum UsageTypes {
  SIGNING = 'SIGNING',
  AUTHENTICATION = 'AUTHENTICATION',
}

enum CsrFormatTypes {
  PEM = 'PEM',
  DER = 'DER',
}

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    keyId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      e1: 0,
      key: {} as Key,
      usageTypes: UsageTypes,
      usageList: Object.values(UsageTypes),
      certificationServiceList: [],
      csrFormatList: Object.values(CsrFormatTypes),
      usage: UsageTypes.SIGNING,
      client: '',
      certificationService: '',
      csrFormat: CsrFormatTypes.PEM,
      form: [],
    };
  },
  computed: {
    ...mapGetters(['memberClasses', 'localMembers', 'localMembersIds']),
  },
  watch: {
    certificationServiceList(val) {
      // Set first certification service selected as defaulg when the list is updated
      if (val && val.length === 1) {
        this.certificationService = val[0].name;
      }
    },
    localMembersIds(val) {
      console.log('-------------------');
      console.log(val);
      // Set first client selected as defaulg when the list is updated

      if (val && val.length === 1) {
        this.client = val[0].id;
      }
    },
  },
  methods: {
    getClientList(): any {
      // Create an array of local member id:s
      const memberIds: string[] = [];
      this.localMembers.forEach((client: any) => {
        memberIds.push(client.id + ':*');
      });

      return memberIds;
    },

    authList() {
      if (this.usage === UsageTypes.SIGNING) {
        const filtered = this.certificationServiceList.filter(
          (service: any) => {
            return !service.authentication_only;
          },
        );

        return filtered;
      }

      return this.certificationServiceList;
    },

    save(): void {
      let query = '';

      if (this.usage === UsageTypes.SIGNING) {
        query =
          `/certificate-authorities/${this.certificationService}/csr-subject-fields?key_usage_type=${this.usage}` +
          `&key_id=${this.keyId}&member_id=${this.client}`;
      } else {
        query =
          `/certificate-authorities/${this.certificationService}/csr-subject-fields?key_usage_type=${this.usage}` +
          `&key_id=${this.keyId}&member_id=${this.client}`;
      }

      /*
      if (this.usage === UsageTypes.SIGNING) {
        query =
          `/keys/${this.keyId}/csr-dn-fields?key_usage_type=${this.usage}` +
          `&ca_name=${this.certificationService}&member_id=${this.client}`;
      } else {
        query =
          `/keys/${this.keyId}/csr-dn-fields?key_usage_type=${this.usage}` +
          `&ca_name=${this.certificationService}`;
      }
      */
      ///certificate-authorities/{ca_name}/csr-subject-fields
      api
        .get(query)
        .then((res: any) => {
          this.form = res.data;
          this.e1 = 2;
        })
        .catch((error: any) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    cancel(): void {
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },

    fetchKeyData(id: string): void {
      api
        .get(`/keys/${id}`)
        .then((res: any) => {
          this.key = res.data;

          if (this.key.usage) {
            if (this.key.usage === UsageTypes.SIGNING) {
              this.usage = UsageTypes.SIGNING;
            } else if (this.key.usage === UsageTypes.AUTHENTICATION) {
              this.usage = UsageTypes.AUTHENTICATION;
            }
          }
        })
        .catch((error: any) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    fetchLocalMembers() {
      this.$store.dispatch('fetchLocalMembers').catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },

    fetchCertificatAuthorities() {
      api
        .get(`/certificate-authorities`)
        .then((res: any) => {
          // this.key = res.data;
          this.certificationServiceList = res.data;
        })
        .catch((error: any) => {
          this.$bus.$emit('show-error', error.message);
        });
    },

    generateCsr() {
      let subjectFieldValues = {};

      this.form.forEach((item) => {
        subjectFieldValues[item.id] = item.default_value;
        console.log(item.id);
        console.log(item.default_value);
      });

      console.log(subjectFieldValues);

      api
        .put(`/keys/${this.keyId}/generate-csr`, {
          key_usage_type: this.usage,
          ca_name: this.certificationService,
          csr_format: this.csrFormat,
          subject_field_values: subjectFieldValues,
          member_id: this.client,
        })
        .then((response) => {
          saveAsFile(response);
        })
        .catch((error: any) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
  created() {
    this.fetchKeyData(this.keyId);
    this.fetchLocalMembers();
    this.fetchCertificatAuthorities();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';

.stepper-content {
  width: 100%;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.label {
  width: 230px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.form-input {
  width: 300px;
}

.stepper {
  width: 100%;
}

.noshadow {
  -webkit-box-shadow: none;
  -moz-box-shadow: none;
  box-shadow: none;
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
</style>