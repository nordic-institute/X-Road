<template>
    <div class="xrd-tab-max-width xrd-view-common">

        <div class="wrap-right">
            <v-btn
                color="primary"
                @click="addEndpoint"
                outlined
                rounded
                class="rounded-button elevation-0 rest-button"
                data-test="endpoint-add"
            >{{$t('endpoints.addEndpoint')}}
            </v-btn>
        </div>

        <table class="xrd-table">
            <thead>
                <tr>
                    <th>{{$t('endpoints.path')}}</th>
                    <th>{{$t('endpoints.httpRequestMethod')}}</th>
                    <th></th>
                </tr>
            </thead>
            <tbody v-if="service.endpoints">
                <template v-for="endpoint in service.endpoints">
                    <template v-if="isBaseEndpoint(endpoint)">
                        <tr class="generated">
                            <td class="path-wrapper">{{service.service_code}}</td>
                            <td class="url-wrapper">{{service.url}}</td>
                            <td></td>
                        </tr>
                    </template>
                    <template v-else>
                        <tr v-bind:class="{generated: endpoint.generated}">
                            <td>{{endpoint.method}}</td>
                            <td>{{endpoint.path}}</td>
                            <td class="wrap-right">
                                <v-btn
                                    v-if="!endpoint.generated"
                                    small
                                    outlined
                                    rounded
                                    color="primary"
                                    class="xrd-small-button xrd-table-button"
                                    data-test="endpoint-delete"
                                    @click="deleteEndpoint(endpoint)">{{$t('action.remove')}}
                                </v-btn>
                                <v-btn
                                    v-if="!endpoint.generated"
                                    small
                                    outlined
                                    rounded
                                    color="primary"
                                    class="xrd-small-button xrd-table-button"
                                    data-test="endpoint-edit"
                                    @click="editEndpoint(endpoint)">{{$t('action.edit')}}
                                </v-btn>
                                <v-btn
                                    small
                                    outlined
                                    rounded
                                    color="primary"
                                    class="xrd-small-button xrd-table-button"
                                    data-test="endpoint-edit-accessrights"
                                    @click="editAccessRights(endpoint)">{{$t('access.accessRights')}}
                                </v-btn>
                            </td>
                        </tr>
                    </template>
                </template>

            </tbody>
        </table>

    </div>
</template>

<script lang="ts">
import Vue from 'vue';
import {mapGetters} from 'vuex';
import {Endpoint} from '@/types';

export default Vue.extend({
  computed: {
    ...mapGetters(['service']),
  },
  methods: {
    addEndpoint(): void {
        // NOOP
    },
    isBaseEndpoint(endpoint: Endpoint): boolean {
      return endpoint.method === '*' && endpoint.path === '**';
    },
    deleteEndpoint(endpoint: Endpoint): void {
      // NOOP
    },
    editEndpoint(endpoint: Endpoint): void {
      // NOOP
    },
    editAccessRights(endpoint: Endpoint): void {
      // NOOP
    },
  },

});
</script>

<style lang="scss" scoped>

    @import '../../../assets/colors';
    @import '../../../assets/tables';
    @import '../../../assets/global-style';

    .path-wrapper {
        white-space: nowrap;
        min-width: 100px;
    }

    .url-wrapper {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 400px;
    }

    .generated {
        color: $XRoad-Grey40;
    }


</style>
