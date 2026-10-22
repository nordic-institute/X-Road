#!/usr/bin/env bash
# =============================================================================
# xroad-api-test.sh — Teste rápido de chamadas X-Road REST via curl
# =============================================================================
# Uso:
#   ./xroad-api-test.sh [opções]
#   ./xroad-api-test.sh --env .env.local
#
# Configuração: edite as variáveis abaixo ou crie um arquivo .env e passe com --env
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Cores para output
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ---------------------------------------------------------------------------
# Defaults — sobrescreva via .env ou flags
# ---------------------------------------------------------------------------
SS_HOST="localhost"
SS_PORT="8080"
PROTOCOL="http"          # http ou https
SKIP_TLS_VERIFY="true"   # true = ignora cert inválido (dev), false = valida

# Identidade do consumer (quem está chamando)
CLIENT_INSTANCE="DEV"
CLIENT_CLASS="COM"
CLIENT_MEMBER="4321"
CLIENT_SUBSYSTEM="TestClient"

# Identidade do provider (quem está sendo chamado)
PROVIDER_INSTANCE="DEV"
PROVIDER_CLASS="COM"
PROVIDER_MEMBER="1234"
PROVIDER_SUBSYSTEM="TestService"
SERVICE_CODE="mock1"

# Request
HTTP_METHOD="GET"
SERVICE_PATH=""          # ex: /v1/pets/123
QUERY_PARAMS=""          # ex: "status=available&limit=10"
BODY=""                  # JSON body para POST/PUT
CONTENT_TYPE="application/json"

# Headers opcionais
XROAD_USER_ID=""         # ex: EE12345678901
XROAD_ISSUE=""           # ex: DOC-42
XROAD_ID=""              # UUID — gerado automaticamente se vazio

# Modo
VERBOSE="false"
META_CMD=""              # listMethods | allowedMethods | getOpenAPI | listClients
ENV_FILE=""

# ---------------------------------------------------------------------------
# Funções utilitárias
# ---------------------------------------------------------------------------
log()      { echo -e "${BLUE}[INFO]${RESET}  $*"; }
success()  { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()     { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()    { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
header()   { echo -e "\n${BOLD}${CYAN}=== $* ===${RESET}\n"; }
die()      { error "$*"; exit 1; }

usage() {
  cat <<EOF
${BOLD}xroad-api-test.sh${RESET} — Tester rápido de APIs X-Road REST

${BOLD}USO${RESET}
  $0 [opções]

${BOLD}CONFIGURAÇÃO${RESET}
  --env FILE            Carrega variáveis de um arquivo .env
  --host HOST           Security Server host (default: $SS_HOST)
  --port PORT           Security Server porta (default: $SS_PORT)
  --https               Usa HTTPS (default: HTTP)
  --verify-tls          Valida certificado TLS (default: ignora em dev)

${BOLD}IDENTIDADE${RESET}
  --client  I/C/M/S     X-Road-Client completo (ex: DEV/COM/4321/TestClient)
  --service I/C/M/S/SC  ServiceId completo     (ex: DEV/COM/1234/TestService/mock1)

${BOLD}REQUEST${RESET}
  -X, --method METHOD   Método HTTP: GET POST PUT DELETE PATCH (default: GET)
  -p, --path PATH       Caminho após o serviceId (ex: /v1/pets/123)
  -q, --query PARAMS    Query string (ex: "status=ok&page=1")
  -d, --data JSON       Body JSON para POST/PUT
  --content-type TYPE   Content-Type (default: application/json)

${BOLD}HEADERS OPCIONAIS${RESET}
  --user-id ID          X-Road-UserId (ex: EE12345678901)
  --issue ID            X-Road-Issue
  --id UUID             X-Road-Id (gerado automaticamente se omitido)

${BOLD}METASERVIÇOS${RESET}
  --list-methods        Chama listMethods no provider
  --allowed-methods     Chama allowedMethods no provider
  --get-openapi CODE    Chama getOpenAPI?serviceCode=CODE
  --list-clients        Chama listClients (discovery de providers)

${BOLD}OUTROS${RESET}
  -v, --verbose         Mostra headers e detalhes completos
  -h, --help            Mostra esta ajuda

${BOLD}EXEMPLOS${RESET}
  # GET simples
  $0 --host 192.168.1.100 -X GET -p /v2/pets/1

  # POST com body
  $0 -X POST -d '{"name":"Rex","status":"available"}' -p /v2/pets

  # Descobrir serviços disponíveis
  $0 --list-methods

  # Usando arquivo de configuração
  $0 --env .env.staging -X GET -p /status

  # Verbose com HTTPS
  $0 --https --verify-tls -v -X GET -p /v1/health
EOF
}

# ---------------------------------------------------------------------------
# Parser de argumentos
# ---------------------------------------------------------------------------
parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env)          ENV_FILE="$2";        shift 2 ;;
      --host)         SS_HOST="$2";         shift 2 ;;
      --port)         SS_PORT="$2";         shift 2 ;;
      --https)        PROTOCOL="https";     shift   ;;
      --verify-tls)   SKIP_TLS_VERIFY="false"; shift ;;
      --client)
        IFS='/' read -r CLIENT_INSTANCE CLIENT_CLASS CLIENT_MEMBER CLIENT_SUBSYSTEM <<< "$2"
        shift 2 ;;
      --service)
        IFS='/' read -r PROVIDER_INSTANCE PROVIDER_CLASS PROVIDER_MEMBER PROVIDER_SUBSYSTEM SERVICE_CODE <<< "$2"
        shift 2 ;;
      -X|--method)    HTTP_METHOD="${2^^}";  shift 2 ;;
      -p|--path)      SERVICE_PATH="$2";    shift 2 ;;
      -q|--query)     QUERY_PARAMS="$2";    shift 2 ;;
      -d|--data)      BODY="$2";            shift 2 ;;
      --content-type) CONTENT_TYPE="$2";    shift 2 ;;
      --user-id)      XROAD_USER_ID="$2";   shift 2 ;;
      --issue)        XROAD_ISSUE="$2";     shift 2 ;;
      --id)           XROAD_ID="$2";        shift 2 ;;
      --list-methods)    META_CMD="listMethods";   shift ;;
      --allowed-methods) META_CMD="allowedMethods"; shift ;;
      --get-openapi)     META_CMD="getOpenAPI"; QUERY_PARAMS="serviceCode=$2"; shift 2 ;;
      --list-clients)    META_CMD="listClients";   shift ;;
      -v|--verbose)   VERBOSE="true";       shift   ;;
      -h|--help)      usage; exit 0 ;;
      *) die "Argumento desconhecido: $1. Use --help para ajuda." ;;
    esac
  done
}

# ---------------------------------------------------------------------------
# Carrega .env se fornecido
# ---------------------------------------------------------------------------
load_env() {
  [[ -z "$ENV_FILE" ]] && return
  [[ ! -f "$ENV_FILE" ]] && die "Arquivo .env não encontrado: $ENV_FILE"
  log "Carregando configuração de: $ENV_FILE"
  # shellcheck disable=SC1090
  set -a; source "$ENV_FILE"; set +a
}

# ---------------------------------------------------------------------------
# Gera UUID v4 simples (sem dependência de uuidgen)
# ---------------------------------------------------------------------------
gen_uuid() {
  if command -v uuidgen &>/dev/null; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    python3 -c "import uuid; print(uuid.uuid4())" 2>/dev/null \
      || cat /proc/sys/kernel/random/uuid 2>/dev/null \
      || echo "$(date +%s)-$$-$(shuf -i 1000-9999 -n 1)"
  fi
}

# ---------------------------------------------------------------------------
# Monta e executa a chamada
# ---------------------------------------------------------------------------
run_request() {
  local base_url="${PROTOCOL}://${SS_HOST}:${SS_PORT}"
  local client_id="${CLIENT_INSTANCE}/${CLIENT_CLASS}/${CLIENT_MEMBER}/${CLIENT_SUBSYSTEM}"
  local service_id="${PROVIDER_INSTANCE}/${PROVIDER_CLASS}/${PROVIDER_MEMBER}/${PROVIDER_SUBSYSTEM}/${SERVICE_CODE}"
  local msg_id="${XROAD_ID:-$(gen_uuid)}"

  # Monta URL
  local url
  if [[ "$META_CMD" == "listClients" ]]; then
    # listClients não segue o padrão /r1/{serviceId}
    url="${base_url}/r1/${PROVIDER_INSTANCE}/${PROVIDER_CLASS}/${PROVIDER_MEMBER}/${PROVIDER_SUBSYSTEM}/listClients"
  elif [[ -n "$META_CMD" ]]; then
    url="${base_url}/r1/${service_id%/*}/${META_CMD}"
    service_id="${service_id%/*}/${META_CMD}"
  else
    url="${base_url}/r1/${service_id}${SERVICE_PATH}"
  fi

  [[ -n "$QUERY_PARAMS" ]] && url="${url}?${QUERY_PARAMS}"

  # Monta headers curl
  local curl_args=()
  curl_args+=(-s -S)                            # silencioso mas mostra erros
  curl_args+=(-w "\n%{http_code}")              # adiciona status code na última linha
  curl_args+=(-X "$HTTP_METHOD")
  curl_args+=(-H "X-Road-Client: ${client_id}")
  curl_args+=(-H "X-Road-Id: ${msg_id}")
  curl_args+=(-H "Accept: application/json")

  [[ -n "$XROAD_USER_ID" ]] && curl_args+=(-H "X-Road-UserId: ${XROAD_USER_ID}")
  [[ -n "$XROAD_ISSUE"   ]] && curl_args+=(-H "X-Road-Issue: ${XROAD_ISSUE}")

  if [[ -n "$BODY" ]]; then
    curl_args+=(-H "Content-Type: ${CONTENT_TYPE}")
    curl_args+=(-d "$BODY")
  fi

  [[ "$SKIP_TLS_VERIFY" == "true" ]] && curl_args+=(-k)
  [[ "$VERBOSE"         == "true" ]] && curl_args+=(-v) || curl_args+=(-D /tmp/xroad_headers_$$.txt)

  # ---------------------------------------------------------------------------
  # Exibe resumo da chamada
  # ---------------------------------------------------------------------------
  header "X-Road REST Request"
  echo -e "  ${BOLD}URL:${RESET}            ${url}"
  echo -e "  ${BOLD}Método:${RESET}         ${HTTP_METHOD}"
  echo -e "  ${BOLD}Client:${RESET}         ${client_id}"
  echo -e "  ${BOLD}Service:${RESET}        ${service_id}"
  echo -e "  ${BOLD}X-Road-Id:${RESET}      ${msg_id}"
  [[ -n "$XROAD_USER_ID" ]] && echo -e "  ${BOLD}UserId:${RESET}         ${XROAD_USER_ID}"
  [[ -n "$XROAD_ISSUE"   ]] && echo -e "  ${BOLD}Issue:${RESET}          ${XROAD_ISSUE}"
  [[ -n "$BODY"          ]] && echo -e "  ${BOLD}Body:${RESET}           ${BODY}"
  echo ""

  # Mostra o curl equivalente para copiar/colar
  echo -e "${YELLOW}── curl equivalente ──────────────────────────────────────${RESET}"
  local curl_display="curl -X ${HTTP_METHOD} \"${url}\" \\\n"
  curl_display+="  -H \"X-Road-Client: ${client_id}\" \\\n"
  curl_display+="  -H \"X-Road-Id: ${msg_id}\" \\\n"
  curl_display+="  -H \"Accept: application/json\""
  [[ -n "$XROAD_USER_ID" ]] && curl_display+=" \\\n  -H \"X-Road-UserId: ${XROAD_USER_ID}\""
  [[ -n "$BODY"          ]] && curl_display+=" \\\n  -H \"Content-Type: ${CONTENT_TYPE}\" \\\n  -d '${BODY}'"
  [[ "$SKIP_TLS_VERIFY"  == "true" ]] && curl_display+=" \\\n  -k"
  echo -e "$curl_display"
  echo -e "${YELLOW}──────────────────────────────────────────────────────────${RESET}\n"

  # ---------------------------------------------------------------------------
  # Executa
  # ---------------------------------------------------------------------------
  header "Response"

  local raw_output http_status response_body

  # Captura output + status code (última linha)
  raw_output=$(curl "${curl_args[@]}" "$url" 2>/dev/null || true)
  http_status=$(echo "$raw_output" | tail -n1)
  response_body=$(echo "$raw_output" | head -n -1)

  # Status code colorido
  local status_color="$GREEN"
  [[ "$http_status" -ge 400 ]] && status_color="$RED"
  [[ "$http_status" -ge 300 && "$http_status" -lt 400 ]] && status_color="$YELLOW"

  echo -e "  ${BOLD}Status:${RESET} ${status_color}${http_status}${RESET}"
  echo ""

  # Mostra headers de resposta X-Road (se não verbose)
  if [[ "$VERBOSE" == "false" && -f /tmp/xroad_headers_$$.txt ]]; then
    local xroad_headers
    xroad_headers=$(grep -i "x-road-" /tmp/xroad_headers_$$.txt 2>/dev/null || true)
    if [[ -n "$xroad_headers" ]]; then
      echo -e "${CYAN}── Headers X-Road na resposta ──${RESET}"
      echo "$xroad_headers" | while IFS= read -r line; do
        echo "  $line"
      done
      echo ""
    fi
    rm -f /tmp/xroad_headers_$$.txt
  fi

  # Body formatado
  echo -e "${CYAN}── Body ────────────────────────────────────────────────────${RESET}"
  if command -v python3 &>/dev/null && [[ -n "$response_body" ]]; then
    echo "$response_body" | python3 -m json.tool 2>/dev/null || echo "$response_body"
  else
    echo "$response_body"
  fi
  echo -e "${CYAN}────────────────────────────────────────────────────────────${RESET}"

  # Verifica header X-Road-Error
  if [[ -n "${response_body}" ]] && echo "$response_body" | grep -q '"type"'; then
    local error_type
    error_type=$(echo "$response_body" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('type',''))" 2>/dev/null || true)
    if [[ -n "$error_type" ]]; then
      echo ""
      if echo "$error_type" | grep -q "^Client\."; then
        warn "Erro no Consumer Security Server: ${error_type}"
      elif echo "$error_type" | grep -q "^Server\.ServerProxy"; then
        warn "Erro no Provider Security Server: ${error_type}"
      fi
    fi
  fi

  echo ""
  [[ "$http_status" -lt 400 ]] && success "Chamada concluída com status ${http_status}" \
                                || error   "Falha com status ${http_status}"

  return 0
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  parse_args "$@"
  load_env

  # Validações básicas
  [[ -z "$SS_HOST" ]]           && die "SS_HOST não definido. Use --host ou --env."
  [[ -z "$CLIENT_MEMBER" ]]     && die "CLIENT não definido. Use --client I/C/M/S ou --env."
  [[ -z "$PROVIDER_MEMBER" ]]   && die "PROVIDER não definido. Use --service I/C/M/S/SC ou --env."
  [[ -z "$SERVICE_CODE" && -z "$META_CMD" ]] && die "SERVICE_CODE não definido. Use --service ou um metaserviço (--list-methods etc)."

  run_request
}

main "$@"
