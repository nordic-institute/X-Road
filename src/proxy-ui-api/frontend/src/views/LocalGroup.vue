<template>
  <div class="xr-tab-max-width">
    <div>
      <subViewTitle :title="group.code" @close="close"/>
      <template>
        <div class="cert-hash">
          Local Group
          <v-btn
            outline
            round
            color="primary"
            class="xr-big-button"
            type="file"
            @click="deleteGroup()"
          >Delete</v-btn>
        </div>
      </template>
    </div>

    <div class="edit-row">
      <div>Edit description</div>
      <v-text-field
        v-model="description"
        @change="saveDescription"
        single-line
        hide-details
        class="description-input"
      ></v-text-field>
    </div>

    <div class="group-members-row">
      <div class="row-title">Group Members</div>
      <div class="row-buttons">
        <v-btn
          outline
          color="primary"
          class="xr-big-button"
          type="file"
          @click="removeAllMembers()"
        >Remove All</v-btn>

        <v-btn
          outline
          color="primary"
          class="xr-big-button"
          type="file"
          @click="addMembers()"
        >Add Members</v-btn>
      </div>
    </div>

    <v-card flat>
      <table class="xrd-table group-members-table">
        <tr>
          <th>Member Name/Group Description</th>
          <th>Id</th>
          <th>Access Rights Given</th>
          <th></th>
        </tr>
        <template v-if="members && members.length > 0">
          <tr v-for="groupMember in members" v-bind:key="groupMember.id">
            <td>{{groupMember.name}}</td>
            <td>{{groupMember.id}}</td>
            <td>{{groupMember.created_at}}</td>

            <td>
              <div class="button-wrap">
                <v-btn
                  small
                  outline
                  round
                  color="primary"
                  class="xr-small-button"
                  @click="removeMember(groupMember)"
                >Remove</v-btn>
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="close-button-wrap">
        <v-btn
          round
          color="primary"
          class="xr-big-button elevation-0"
          type="file"
          @click="close()"
        >Close</v-btn>
      </div>
    </v-card>

    <!-- Confirm dialog delete group -->
    <v-dialog v-model="confirmGroup" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">Delete group?</v-card-title>
        <v-card-text>Are you sure that you want to delete this group?</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirmGroup = false">Cancel</v-btn>
          <v-btn color="darken-1" flat @click="doDeleteGroup()">Yes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Confirm dialog remove member-->
    <v-dialog v-model="confirmMember" persistent max-width="290">
      <v-card>
        <v-card-title class="headline">Remove member?</v-card-title>
        <v-card-text>Are you sure that you want to remove this member?</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirmMember = false">Cancel</v-btn>
          <v-btn color="darken-1" flat @click="doRemoveMember()">Yes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import _ from 'lodash';
import { mapGetters } from 'vuex';
import { Permissions } from '@/global';
import SubViewTitle from '@/components/SubViewTitle.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    code: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmGroup: false,
      confirmMember: false,
      selectedMember: undefined,
      description: 'tetet',
      group: {
        id: 'group123',
        code: 'groupcode',
        description: 'description',
        member_count: 10,
        updated_at: '2018-12-15T00:00:00.001Z',
      },
      members: [],
    };
  },
  computed: {
    ...mapGetters(['tlsCertificates']),
  },
  methods: {
    close() {
      this.$router.go(-1);
    },

    saveDescription: _.debounce(() => {
      console.log('I only get fired once every two seconds, max!');
    }, 500),

    fetchData(clientId: string, hash: string) {
      /*
      this.$store.dispatch('fetchTlsCertificates', clientId).then(
        (response) => {
          this.certificate = this.$store.getters.tlsCertificates.find(
            (cert: any) => cert.hash === hash,
          );
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      ); */

      this.sleep().then(() => {
        this.description = 'jau';

        this.members = [
          {
            id: 'FI:GOV:123:SS1',
            name: 'member name A',
            created_at: '2018-12-15T00:00:00.001Z',
          },
          {
            id: 'FI:GOV:123:SS18',
            name: 'member name B',
            created_at: '2018-12-15T00:00:00.001Z',
          },
        ];
      });
    },

    addMembers() {
      // TODO placeholder. will be done in future task
    },

    removeAllMembers() {
      // TODO placeholder. will be done in future task
    },

    removeMember(member: any) {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveMember() {
      this.confirmMember = false;
      console.log(this.selectedMember);
      this.selectedMember = undefined;

      /*
      this.$store
        .dispatch('deleteTlsCertificate', {
          clientId: this.id,
          hash: this.hash,
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
        */
    },

    deleteGroup() {
      this.confirmGroup = true;
    },
    doDeleteGroup() {
      this.confirmGroup = false;

      /*
      this.$store
        .dispatch('deleteTlsCertificate', {
          clientId: this.id,
          hash: this.hash,
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
        */
    },
    sleep() {
      return new Promise((resolve) => setTimeout(resolve, 3000));
    },
  },
  created() {
    this.fetchData(this.id, this.hash);
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

  .description-input {
    margin-left: 60px;
  }
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

.wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  padding-top: 60px;
  height: 100%;
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

