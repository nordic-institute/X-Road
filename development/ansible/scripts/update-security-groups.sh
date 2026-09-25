#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# X-VIA: Atualizar Security Groups para nova instância/stack
# ═══════════════════════════════════════════════════════════════════════════════
#
# Este script configura as regras de Security Group necessárias para uma nova
# stack X-Road (Central Server, Security Server, CA, Management SS).
#
# Ele garante que:
#   - VPN (10.8.0.0/24 e 10.1.0.0/16 masquerade) acessa portas de admin
#   - Comunicação interna entre componentes via SG references
#   - Portas de serviço (OCSP, TSA, global config) abertas corretamente
#
# USO:
#   ./update-security-groups.sh --region sa-east-1 --vpc-id vpc-XXXXX
#   ./update-security-groups.sh --region sa-east-1 --stack-name xvia-maringa-prod
#   ./update-security-groups.sh --region sa-east-1 --sg-cs sg-XXX --sg-ss sg-XXX --sg-ca sg-XXX --sg-mss sg-XXX
#
# ═══════════════════════════════════════════════════════════════════════════════

set -euo pipefail

# ─── Cores ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ─── Defaults ─────────────────────────────────────────────────────────────────
REGION="sa-east-1"
VPC_ID=""
STACK_NAME=""
SG_CS=""    # Central Server
SG_SS=""    # Security Server
SG_CA=""    # CA Server
SG_MSS=""   # Management Security Server
SG_DB=""    # Database (RDS)
VPN_CIDR="10.8.0.0/24"
VPN_VPC_CIDR="10.1.0.0/16"  # VPC onde está o servidor VPN (masquerade)
DRY_RUN=false
ADMIN_IP=""  # IP específico para admin UI (opcional)

# ─── Funções ──────────────────────────────────────────────────────────────────

usage() {
    cat << EOF
Uso: $(basename "$0") [opções]

Opções:
  --region REGION           Região AWS (default: sa-east-1)
  --vpc-id VPC_ID           VPC ID para descobrir SGs automaticamente
  --stack-name STACK        Nome da stack CloudFormation para descobrir SGs
  --sg-cs SG_ID             Security Group do Central Server
  --sg-ss SG_ID             Security Group do Security Server
  --sg-ca SG_ID             Security Group do CA Server
  --sg-mss SG_ID            Security Group do Management Security Server
  --sg-db SG_ID             Security Group do Database (RDS)
  --vpn-cidr CIDR           CIDR da VPN (default: 10.8.0.0/24)
  --vpn-vpc-cidr CIDR       CIDR da VPC da VPN/masquerade (default: 10.1.0.0/16)
  --admin-ip IP             IP público para Admin UI (ex: 177.193.214.120/32)
  --dry-run                 Apenas mostrar o que seria feito
  --help                    Mostrar esta ajuda

Exemplos:
  # Descobrir SGs pela stack CloudFormation:
  $(basename "$0") --stack-name xvia-maringa-prod

  # Especificar SGs manualmente:
  $(basename "$0") --sg-cs sg-0e92... --sg-ss sg-0064... --sg-ca sg-0be0... --sg-mss sg-09c0...

  # Com IP público para admin:
  $(basename "$0") --stack-name xvia-maringa-prod --admin-ip 200.100.50.25/32
EOF
    exit 0
}

log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_dry()   { echo -e "${YELLOW}[DRY-RUN]${NC} $*"; }

# Adiciona regra de ingress (idempotente - ignora se já existe)
add_ingress_cidr() {
    local sg_id="$1"
    local protocol="$2"
    local from_port="$3"
    local to_port="$4"
    local cidr="$5"
    local description="$6"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_dry "authorize-security-group-ingress $sg_id: ${protocol}/${from_port}-${to_port} from ${cidr} (${description})"
        return 0
    fi

    aws ec2 authorize-security-group-ingress \
        --region "$REGION" \
        --group-id "$sg_id" \
        --ip-permissions "[{
            \"IpProtocol\": \"$protocol\",
            \"FromPort\": $from_port,
            \"ToPort\": $to_port,
            \"IpRanges\": [{\"CidrIp\": \"$cidr\", \"Description\": \"$description\"}]
        }]" 2>/dev/null && log_ok "  $sg_id: ${protocol}/${from_port}-${to_port} ← ${cidr} ($description)" \
        || log_warn "  $sg_id: ${protocol}/${from_port}-${to_port} ← ${cidr} (já existe ou erro)"
}

# Adiciona regra de ingress referenciando outro SG (idempotente)
add_ingress_sg() {
    local sg_id="$1"
    local protocol="$2"
    local from_port="$3"
    local to_port="$4"
    local source_sg="$5"
    local description="$6"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_dry "authorize-security-group-ingress $sg_id: ${protocol}/${from_port}-${to_port} from SG:${source_sg} (${description})"
        return 0
    fi

    aws ec2 authorize-security-group-ingress \
        --region "$REGION" \
        --group-id "$sg_id" \
        --ip-permissions "[{
            \"IpProtocol\": \"$protocol\",
            \"FromPort\": $from_port,
            \"ToPort\": $to_port,
            \"UserIdGroupPairs\": [{\"GroupId\": \"$source_sg\", \"Description\": \"$description\"}]
        }]" 2>/dev/null && log_ok "  $sg_id: ${protocol}/${from_port}-${to_port} ← SG:${source_sg} ($description)" \
        || log_warn "  $sg_id: ${protocol}/${from_port}-${to_port} ← SG:${source_sg} (já existe ou erro)"
}

# Adiciona regra "all traffic" de um CIDR (protocolo -1)
add_ingress_all_cidr() {
    local sg_id="$1"
    local cidr="$2"
    local description="$3"

    if [[ "$DRY_RUN" == "true" ]]; then
        log_dry "authorize-security-group-ingress $sg_id: ALL from ${cidr} (${description})"
        return 0
    fi

    aws ec2 authorize-security-group-ingress \
        --region "$REGION" \
        --group-id "$sg_id" \
        --ip-permissions "[{
            \"IpProtocol\": \"-1\",
            \"IpRanges\": [{\"CidrIp\": \"$cidr\", \"Description\": \"$description\"}]
        }]" 2>/dev/null && log_ok "  $sg_id: ALL ← ${cidr} ($description)" \
        || log_warn "  $sg_id: ALL ← ${cidr} (já existe ou erro)"
}

# Descobrir SGs via CloudFormation stack
discover_sgs_from_stack() {
    local stack="$1"
    log_info "Descobrindo Security Groups da stack: $stack"

    local resources
    resources=$(aws cloudformation describe-stack-resources \
        --region "$REGION" \
        --stack-name "$stack" \
        --query "StackResources[?ResourceType=='AWS::EC2::SecurityGroup'].{LogicalId:LogicalResourceId,PhysicalId:PhysicalResourceId}" \
        --output json 2>/dev/null)

    if [[ -z "$resources" ]]; then
        log_error "Não foi possível encontrar a stack: $stack"
        exit 1
    fi

    SG_CS=$(echo "$resources" | jq -r '.[] | select(.LogicalId=="SGCentralServer") | .PhysicalId' 2>/dev/null || true)
    SG_SS=$(echo "$resources" | jq -r '.[] | select(.LogicalId=="SGSecurityServer") | .PhysicalId' 2>/dev/null || true)
    SG_CA=$(echo "$resources" | jq -r '.[] | select(.LogicalId=="SGCA") | .PhysicalId' 2>/dev/null || true)
    SG_MSS=$(echo "$resources" | jq -r '.[] | select(.LogicalId=="SGManagementSecurityServer") | .PhysicalId' 2>/dev/null || true)

    [[ -n "$SG_CS" ]]  && log_ok "  Central Server:     $SG_CS"  || log_warn "  Central Server: não encontrado"
    [[ -n "$SG_SS" ]]  && log_ok "  Security Server:    $SG_SS"  || log_warn "  Security Server: não encontrado"
    [[ -n "$SG_CA" ]]  && log_ok "  CA Server:          $SG_CA"  || log_warn "  CA Server: não encontrado"
    [[ -n "$SG_MSS" ]] && log_ok "  Management SS:      $SG_MSS" || log_warn "  Management SS: não encontrado"
}

# ─── Parse args ───────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
    case "$1" in
        --region)       REGION="$2"; shift 2 ;;
        --vpc-id)       VPC_ID="$2"; shift 2 ;;
        --stack-name)   STACK_NAME="$2"; shift 2 ;;
        --sg-cs)        SG_CS="$2"; shift 2 ;;
        --sg-ss)        SG_SS="$2"; shift 2 ;;
        --sg-ca)        SG_CA="$2"; shift 2 ;;
        --sg-mss)       SG_MSS="$2"; shift 2 ;;
        --sg-db)        SG_DB="$2"; shift 2 ;;
        --vpn-cidr)     VPN_CIDR="$2"; shift 2 ;;
        --vpn-vpc-cidr) VPN_VPC_CIDR="$2"; shift 2 ;;
        --admin-ip)     ADMIN_IP="$2"; shift 2 ;;
        --dry-run)      DRY_RUN=true; shift ;;
        --help|-h)      usage ;;
        *) log_error "Opção desconhecida: $1"; usage ;;
    esac
done

# ─── Descobrir SGs ────────────────────────────────────────────────────────────

if [[ -n "$STACK_NAME" ]]; then
    discover_sgs_from_stack "$STACK_NAME"
fi

# Validar que temos pelo menos o CS
if [[ -z "$SG_CS" ]]; then
    log_error "Security Group do Central Server não definido."
    log_error "Use --stack-name ou --sg-cs para especificar."
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo " X-VIA: Configurando Security Groups"
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo " Região:          $REGION"
echo " VPN CIDR:        $VPN_CIDR"
echo " VPN VPC CIDR:    $VPN_VPC_CIDR"
echo " Central Server:  ${SG_CS:-N/A}"
echo " Security Server: ${SG_SS:-N/A}"
echo " CA Server:       ${SG_CA:-N/A}"
echo " Management SS:   ${SG_MSS:-N/A}"
echo " Database:        ${SG_DB:-N/A}"
echo " Admin IP:        ${ADMIN_IP:-não definido}"
echo " Dry Run:         $DRY_RUN"
echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""

# ─── 1. Central Server (CS) ──────────────────────────────────────────────────

if [[ -n "$SG_CS" ]]; then
    log_info "═══ Central Server ($SG_CS) ═══"

    # VPN access (all traffic)
    add_ingress_all_cidr "$SG_CS" "$VPN_CIDR" "VPN users via VPC Peering"

    # VPN masquerade (porta 4000 para admin via VPN)
    add_ingress_cidr "$SG_CS" "tcp" 4000 4000 "$VPN_VPC_CIDR" "Admin UI - VPN server VPC (peering masquerade)"

    # Admin IP público (se fornecido)
    if [[ -n "$ADMIN_IP" ]]; then
        add_ingress_cidr "$SG_CS" "tcp" 4000 4000 "$ADMIN_IP" "Admin UI - IP admin"
    fi

    # Global config download (HTTP/HTTPS) - de SS e Management SS
    if [[ -n "$SG_SS" ]]; then
        add_ingress_sg "$SG_CS" "tcp" 80  80  "$SG_SS"  "Global config download - Security Server"
        add_ingress_sg "$SG_CS" "tcp" 443 443 "$SG_SS"  "Global config download HTTPS - Security Server"
        add_ingress_sg "$SG_CS" "tcp" 4001 4001 "$SG_SS" "Auth cert registration - Security Server"
    fi
    if [[ -n "$SG_MSS" ]]; then
        add_ingress_sg "$SG_CS" "tcp" 80  80  "$SG_MSS" "Global config download - Management SS"
        add_ingress_sg "$SG_CS" "tcp" 443 443 "$SG_MSS" "Global config download HTTPS - Management SS"
        add_ingress_sg "$SG_CS" "tcp" 4001 4001 "$SG_MSS" "Auth cert registration - Management SS"
        add_ingress_sg "$SG_CS" "tcp" 4002 4002 "$SG_MSS" "Management services - Management SS"
    fi

    # Postgres (se tiver SG de DB)
    if [[ -n "$SG_DB" ]]; then
        add_ingress_sg "$SG_CS" "tcp" 5432 5432 "$SG_DB" "PostgreSQL - Database"
    fi

    echo ""
fi

# ─── 2. Security Server (SS) ─────────────────────────────────────────────────

if [[ -n "$SG_SS" ]]; then
    log_info "═══ Security Server ($SG_SS) ═══"

    # VPN access (all traffic)
    add_ingress_all_cidr "$SG_SS" "$VPN_CIDR" "VPN users via VPC Peering"

    # VPN masquerade (portas admin)
    add_ingress_cidr "$SG_SS" "tcp" 4000 4000 "$VPN_VPC_CIDR" "Admin UI - VPN server VPC (peering masquerade)"
    add_ingress_cidr "$SG_SS" "tcp" 8080 8080 "$VPN_VPC_CIDR" "Info system HTTP - VPN server VPC (peering masquerade)"
    add_ingress_cidr "$SG_SS" "tcp" 8443 8443 "$VPN_VPC_CIDR" "Info system HTTPS - VPN server VPC (peering masquerade)"

    # Admin IP público (se fornecido)
    if [[ -n "$ADMIN_IP" ]]; then
        add_ingress_cidr "$SG_SS" "tcp" 4000 4000 "$ADMIN_IP" "Admin UI - IP admin"
        add_ingress_cidr "$SG_SS" "tcp" 8080 8080 "$ADMIN_IP" "Information system access - IP admin"
        add_ingress_cidr "$SG_SS" "tcp" 8443 8443 "$ADMIN_IP" "Information system access HTTPS - IP admin"
    fi

    # Message exchange e OCSP - de Management SS
    if [[ -n "$SG_MSS" ]]; then
        add_ingress_sg "$SG_SS" "tcp" 5500 5500 "$SG_MSS" "Message exchange - Management SS (monitoring)"
        add_ingress_sg "$SG_SS" "tcp" 5577 5577 "$SG_MSS" "OCSP query - Management SS (monitoring)"
    fi

    echo ""
fi

# ─── 3. CA Server ────────────────────────────────────────────────────────────

if [[ -n "$SG_CA" ]]; then
    log_info "═══ CA Server ($SG_CA) ═══"

    # VPN access (all traffic)
    add_ingress_all_cidr "$SG_CA" "$VPN_CIDR" "VPN users via VPC Peering"
    add_ingress_all_cidr "$SG_CA" "$VPN_VPC_CIDR" "VPC DEV (VPN server MASQUERADE)"

    # OCSP Responder (8888) - de SS e Management SS
    if [[ -n "$SG_SS" ]]; then
        add_ingress_sg "$SG_CA" "tcp" 8888 8888 "$SG_SS"  "OCSP Responder - Security Server"
        add_ingress_sg "$SG_CA" "tcp" 8899 8899 "$SG_SS"  "TSA - Security Server"
        add_ingress_sg "$SG_CA" "tcp" 8887 8887 "$SG_SS"  "Security Server ACME"
        add_ingress_sg "$SG_CA" "tcp" 443  443  "$SG_SS"  "SS-ACME/OCSP/TSA via HTTPS"
    fi
    if [[ -n "$SG_MSS" ]]; then
        add_ingress_sg "$SG_CA" "tcp" 8888 8888 "$SG_MSS" "OCSP Responder - Management SS"
        add_ingress_sg "$SG_CA" "tcp" 8899 8899 "$SG_MSS" "TSA - Management SS"
        add_ingress_sg "$SG_CA" "tcp" 443  443  "$SG_MSS" "GER-ACME/OCSP/TSA via HTTPS"
    fi
    if [[ -n "$SG_CS" ]]; then
        add_ingress_sg "$SG_CA" "tcp" 443 443 "$SG_CS" "SC-ACME/OCSP/TSA via HTTPS"
    fi

    echo ""
fi

# ─── 4. Management Security Server (MSS) ─────────────────────────────────────

if [[ -n "$SG_MSS" ]]; then
    log_info "═══ Management Security Server ($SG_MSS) ═══"

    # VPN access (all traffic)
    add_ingress_all_cidr "$SG_MSS" "$VPN_CIDR" "VPN users via VPC Peering"

    # VPN masquerade (portas admin)
    add_ingress_cidr "$SG_MSS" "tcp" 4000 4000 "$VPN_VPC_CIDR" "Admin UI - VPN server VPC (peering masquerade)"
    add_ingress_cidr "$SG_MSS" "tcp" 8080 8080 "$VPN_VPC_CIDR" "Info system HTTP - VPN server VPC (peering masquerade)"
    add_ingress_cidr "$SG_MSS" "tcp" 8443 8443 "$VPN_VPC_CIDR" "Info system HTTPS - VPN server VPC (peering masquerade)"

    # Admin IP público (se fornecido)
    if [[ -n "$ADMIN_IP" ]]; then
        add_ingress_cidr "$SG_MSS" "tcp" 4000 4000 "$ADMIN_IP" "Admin UI - IP admin"
        add_ingress_cidr "$SG_MSS" "tcp" 8080 8080 "$ADMIN_IP" "Information system access - IP admin"
        add_ingress_cidr "$SG_MSS" "tcp" 8443 8443 "$ADMIN_IP" "Information system access HTTPS - IP admin"
    fi

    # Message exchange e OCSP - de Security Server
    if [[ -n "$SG_SS" ]]; then
        add_ingress_sg "$SG_MSS" "tcp" 5500 5500 "$SG_SS" "Message exchange - Security Server"
        add_ingress_sg "$SG_MSS" "tcp" 5577 5577 "$SG_SS" "OCSP query - Security Server"
    fi

    echo ""
fi

# ─── Resumo ──────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════════"
if [[ "$DRY_RUN" == "true" ]]; then
    echo -e " ${YELLOW}DRY RUN concluído. Nenhuma alteração foi feita.${NC}"
    echo " Execute novamente sem --dry-run para aplicar."
else
    echo -e " ${GREEN}✅ Security Groups configurados com sucesso!${NC}"
fi
echo "═══════════════════════════════════════════════════════════════════════════════"
echo ""
echo " Portas abertas por componente:"
echo "   Central Server:  4000(admin), 80/443(global conf), 4001(auth reg), 4002(mgmt)"
echo "   Security Server: 4000(admin), 5500(msg), 5577(OCSP), 8080/8443(info sys)"
echo "   CA Server:       8888(OCSP), 8899(TSA), 8887(ACME), 443(HTTPS)"
echo "   Management SS:   4000(admin), 5500(msg), 5577(OCSP), 8080/8443(info sys)"
echo ""
echo " Acesso VPN: ${VPN_CIDR} (direto) + ${VPN_VPC_CIDR} (masquerade)"
echo ""
