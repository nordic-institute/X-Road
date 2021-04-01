<template>
  <div>
    <div class="header-row">
      <div class="xrd-title-search">
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
                <v-icon class="xrd-clickable">icon-Folder-outline</v-icon>
              </td>
              <td data-test="member-name" class="xrd-clickable">
                Nordic Institue for Interoperability Solutions
              </td>
              <td data-test="member-class">ORG</td>
              <td data-test="member-code">555</td>
            </tr>
            <tr data-test="netum">
              <td data-test="member-icon">
                <v-icon class="xrd-clickable">icon-Folder-outline</v-icon>
              </td>
              <td class="xrd-clickable" @click="toDetails('netum')">Netum Oy</td>
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

.icon-column {
  width: 40px;
}

</style>
