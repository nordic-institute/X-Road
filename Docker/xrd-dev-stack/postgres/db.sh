#################################################################################
#  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
#
#  See the NOTICE file(s) distributed with this work for additional
#  information regarding copyright ownership.
#
#  This program and the accompanying materials are made available under the
#  terms of the Apache License, Version 2.0 which is available at
#  https://www.apache.org/licenses/LICENSE-2.0.
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#  License for the specific language governing permissions and limitations
#  under the License.
#
#  SPDX-License-Identifier: Apache-2.0
#################################################################################

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE $POSTGRES_DB_NAME_MIW;
    CREATE USER $POSTGRES_USERNAME_MIW WITH ENCRYPTED PASSWORD '$POSTGRES_PASSWORD_MIW';
    GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_DB_NAME_MIW TO $POSTGRES_USERNAME_MIW;
    \c $POSTGRES_DB_NAME_MIW
    GRANT ALL ON SCHEMA public TO $POSTGRES_USERNAME_MIW;
EOSQL
