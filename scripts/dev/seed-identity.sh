#!/usr/bin/env bash
# Cria usuários de desenvolvimento numa organização (tenant) do realm "davinci" do Keycloak local.
# Idempotente: pode rodar várias vezes. Somente para o ambiente local (compose.yaml).
#
# Uso: scripts/dev/seed-identity.sh <alias-da-organização>
#   ex.: scripts/dev/seed-identity.sh acme
#
# A organização é criada pelo serviço Identity quando o tenant é registrado no Tenant Registry.
# Usuários criados (senha: $DEV_PASSWORD, padrão Davinci@dev2026):
#   admin@<alias>.dev    papel tenant-admin  (MFA obrigatório: configura TOTP no primeiro login)
#   usuario@<alias>.dev  papel tenant-user
set -euo pipefail

ALIAS="${1:?informe o alias da organização (slug do tenant)}"
KC="${KEYCLOAK_URL:-http://localhost:5607}"
REALM="${KEYCLOAK_REALM:-davinci}"
PASSWORD="${DEV_PASSWORD:-Davinci@dev2026}"

token=$(curl -sf -d grant_type=password -d client_id=admin-cli \
  -d username="${KEYCLOAK_ADMIN:-admin}" -d password="${KEYCLOAK_ADMIN_PASSWORD:-davinci_dev}" \
  "$KC/realms/master/protocol/openid-connect/token" | jq -r .access_token)
api() { curl -sf -H "Authorization: Bearer $token" -H 'Content-Type: application/json' "$@"; }
base="$KC/admin/realms/$REALM"

org=$(api "$base/organizations?search=$ALIAS&exact=true" | jq -r --arg a "$ALIAS" '.[] | select(.alias == $a) | .id')
if [[ -z "$org" ]]; then
  echo "Organização '$ALIAS' não existe. Registre e ative o tenant no Tenant Registry (o Identity cria a organização)." >&2
  exit 1
fi

seed_user() {
  local email="$1" first="$2" last="$3" role="$4"
  local id
  id=$(api "$base/users?email=$email&exact=true" | jq -r '.[0].id // empty')
  if [[ -z "$id" ]]; then
    api -X POST "$base/users" -d "$(jq -n --arg e "$email" --arg f "$first" --arg l "$last" --arg p "$PASSWORD" \
      '{username: $e, email: $e, firstName: $f, lastName: $l, enabled: true, emailVerified: true,
        credentials: [{type: "password", value: $p, temporary: false}]}')" >/dev/null
    id=$(api "$base/users?email=$email&exact=true" | jq -r '.[0].id')
  fi
  api -X POST "$base/users/$id/role-mappings/realm" -d "[$(api "$base/roles/$role")]" >/dev/null
  # 409 = já é membro
  curl -s -o /dev/null -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -X POST "$base/organizations/$org/members" -d "\"$id\"" || true
  echo "  ✓ $email ($role)"
}

echo "Usuários de desenvolvimento em '$ALIAS' (senha: $PASSWORD):"
seed_user "admin@$ALIAS.dev" "Ana" "Administradora" "tenant-admin"
seed_user "usuario@$ALIAS.dev" "João" "Usuário" "tenant-user"
