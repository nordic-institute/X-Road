<template>
  <div class="wrapper">
    <div class="new-content">
      <subViewTitle title="Certificate" @close="close"/>
      <template v-if="certificate">
        <div class="cert-hash">
          {{certificate.hash}}
          <v-btn
            outline
            round
            color="primary"
            class="text-capitalize table-button rounded-button"
            type="file"
            @click="deleteCertificate()"
          >Delete</v-btn>
        </div>

        <div>{{certificate.details}}</div>
      </template>
    </div>
    <v-dialog v-model="confirm" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">Delete certificate?</v-card-title>
        <v-card-text>Are you sure that you want to delete this certificate?</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirm = false">Cancel</v-btn>
          <v-btn color="darken-1" flat @click="doDeleteCertificate()">Yes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
  },
  data() {
    return {
      confirm: false,
      certificate: null,
    };
  },
  computed: {
    ...mapGetters(['tlsCertificates']),
  },
  methods: {
    close() {
      this.$router.go(-1);
    },
    fetchData(clientId: string, hash: string) {
      this.$store.dispatch('fetchTlsCertificates', clientId).then(
        (response) => {
          this.certificate = this.$store.getters.tlsCertificates.find(
            (cert: any) => cert.hash === hash,
          );
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    deleteCertificate() {
      this.confirm = true;
    },
    doDeleteCertificate() {
      this.confirm = false;

      this.$store
        .dispatch('deleteTlsCertificate', {
          clientId: this.$route.query.id,
          hash: this.$route.query.hash,
        })
        .then(
          (response) => {
            this.$bus.$emit('show-success', 'Certificate deleted');
          },
          (error) => {
            this.$bus.$emit('show-error', error.message);
          },
        )
        .finally(() => {
          this.close();
        });
    },
  },
  created() {
    this.fetchData(
      this.$route.query.id as string,
      this.$route.query.hash as string,
    );
  },
});
</script>

<style lang="scss" scoped>
.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding-top: 60px;
  max-width: 850px;
  height: 100%;
  width: 100%;
}

.content {
  max-width: 850px;
  width: 400px;
  border: 1px black solid;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid #9b9b9b;
  color: #4a4a4a;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;
}

#close-x {
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: #979797;
}

#close-x:before {
  content: '\00d7';
}

.cert-hash {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.new-content {
  max-width: 850px;
  width: 100%;
}
</style>

