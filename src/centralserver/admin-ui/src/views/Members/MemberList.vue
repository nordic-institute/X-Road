<template>
  <div>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">{{ $t('members.header') }}</div>
        <xrd-search />
      </div>
      <xrd-button @click="() => {}" data-test="add-member-button"
        ><v-icon class="xrd-large-button-icon">icon-Add</v-icon>
        {{ $t('members.addMember') }}</xrd-button
      >
    </div>

    <v-card>
      <v-card-text>
        <xrd-table>
          <thead>
            <tr>
              <th class="icon-column"></th>
              <th>{{ $t('global.memberName') }} (2)</th>
              <th>
                {{ $t('global.memberClass') }}
              </th>
              <th>
                {{ $t('global.memberCode') }}
                <v-icon>mdi-arrow-up</v-icon>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr data-test="niis">
              <!-- Set member identifier etc. as data-text attribute for real data so that individual member row can be found in e2e-test -->
              <td data-test="member-icon">
                <v-icon class="link">icon-Folder-outline</v-icon>
              </td>
              <td data-test="member-name" class="link">
                Nordic Institue for Interoperability Solutions
              </td>
              <td data-test="member-class">ORG</td>
              <td data-test="member-code">555</td>
            </tr>
            <tr data-test="netum">
              <td data-test="member-icon">
                <v-icon class="link">icon-Folder-outline</v-icon>
              </td>
              <td class="link" @click="toDetails('netum')">Netum Oy</td>
              <td>COM</td>
              <td>IMAMEMBERCODE</td>
            </tr>
          </tbody>
        </xrd-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'MemberList',
  methods: {
    toDetails(): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: { memberid: 'netum' },
      });
    },
  },
});
</script>

<style scoped lang="scss">
@import '../../assets/colors';

.header-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-bottom: 24px;
}

.title-search {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  align-items: flex-end;

  .xrd-view-title {
    margin-right: 20px;
  }
}

.icon-column {
  width: 40px;
}

.link {
  color: $XRoad-Link;
  cursor: pointer;
}
</style>
