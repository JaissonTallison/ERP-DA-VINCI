-- Bancos do ambiente local. Em produção cada serviço tem seu próprio banco/cluster (ADR-0010).
-- Executado só na criação do volume; para aplicar mudanças: docker compose down -v (apaga os dados locais).
CREATE DATABASE keycloak;

CREATE ROLE tenant_registry LOGIN PASSWORD 'tenant_registry_dev';
CREATE DATABASE tenant_registry OWNER tenant_registry;

CREATE ROLE identity LOGIN PASSWORD 'identity_dev';
CREATE DATABASE identity OWNER identity;

-- Organization: dois papéis para o Row-Level Security valer (a aplicação não é dona das tabelas).
CREATE ROLE organization_owner LOGIN PASSWORD 'organization_owner_dev';
CREATE ROLE organization_app LOGIN PASSWORD 'organization_app_dev';
CREATE DATABASE organization OWNER organization_owner;
