# Segurança e governança

> Estado: **diretrizes aprovadas**; implementação a partir da Fase 2 (Identity e Governance). Decisões em ADR-0009 e ADR-0012.

## Identidade e acesso

| Tema              | Diretriz                                                                                                                                                               |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Protocolos        | OAuth 2.0 + OpenID Connect para usuários e serviços; SAML 2.0 apenas para federação com IdPs corporativos que exigirem.                                                |
| IdP da plataforma | Keycloak por região, atuando como _broker_: federa Entra ID, Okta e outros IdPs dos clientes. Nenhum serviço de negócio fala com IdP externo diretamente.              |
| SSO               | Por tenant, configurável no realm/organização do cliente.                                                                                                              |
| MFA               | Obrigatório para administradores e acessos privilegiados; política configurável por tenant para demais usuários (TOTP, WebAuthn/passkeys).                             |
| Tokens            | Access token curto (≤ 5 min), assinado (ES256/RS256), com `tenant_id`, `cell_id`, `sub`, `acr`. Refresh token rotativo. Serviços validam localmente via JWKS em cache. |
| Serviço a serviço | mTLS na malha da célula + client credentials com escopo mínimo.                                                                                                        |

## Autorização: RBAC + ABAC

- **RBAC** define o que um papel pode fazer (`finance.payable.approve`).
- **ABAC** restringe onde: entidade legal, filial, centro de custo, limite de valor, horário.
- Decisão = papel concede a ação **e** atributos do usuário/recurso satisfazem a política.
- Políticas vivem no contexto **Governance**; cada serviço avalia localmente (biblioteca + cache de políticas), sem chamada síncrona por requisição.
- Motor de políticas (OPA/Rego, Cedar ou implementação própria): **decisão pendente** (ADR-0009, item em aberto).

## Segregação de funções (SoD)

- Matriz de conflitos declarada por domínio (ex.: quem cadastra fornecedor não aprova pagamento a ele).
- Verificação preventiva na concessão de papéis e detectiva em relatório periódico.
- Exceções exigem aprovação registrada com prazo de validade.

## Gestão de acessos privilegiados

- Nenhum acesso humano permanente a produção. Acesso _just-in-time_, com aprovação, prazo e gravação de sessão.
- Contas de "quebra-vidro" lacradas, com alarme ao uso.
- Suporte acessando dados de tenant precisa de consentimento registrado do cliente.

## Segredos e criptografia

| Item        | Diretriz                                                                                                                                                                                                  |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Segredos    | Cofre central (HashiCorp Vault/OpenBao ou gerenciador da nuvem) por região; injeção em runtime; rotação automática. **Proibido** segredo em código, imagem, variável de CI em texto ou `.env` versionado. |
| Em trânsito | TLS 1.3 externo; mTLS interno.                                                                                                                                                                            |
| Em repouso  | Criptografia de disco/banco com KMS regional. Tenants silo/bridge podem trazer chave própria (BYOK).                                                                                                      |
| Campo       | Dados sensíveis (documentos pessoais, dados bancários, salário) cifrados em nível de aplicação (envelope encryption).                                                                                     |

## Auditoria resistente a alterações

- Todo comando que muda estado gera registro de auditoria: quem, quando, tenant, entidade, antes/depois, origem, correlação.
- Gravação _append-only_; cada lote encadeado por hash (cadeia verificável) e copiado para object storage com **retenção imutável** (WORM/Object Lock).
- Auditoria fica na região do tenant; retenção conforme lei aplicável (ex.: 5+ anos para registros fiscais no Brasil).

## Privacidade (LGPD e internacional)

- Mapeamento de dados pessoais por serviço (inventário mantido junto ao código, revisado em PR).
- Bases legais e finalidade registradas; minimização por padrão.
- Direitos do titular (acesso, correção, portabilidade, eliminação/anonimização) via fluxo orquestrado que atravessa os serviços por eventos.
- Conflito entre eliminação e obrigação legal de guarda (fiscal, trabalhista) resolvido por bloqueio + anonimização após o prazo.
- Residência regional (ver arquitetura global) atende LGPD, GDPR e transferências internacionais; DPAs e cláusulas padrão por região.

## Segurança da cadeia de software

| Controle                                                   | Estado                                                        |
| ---------------------------------------------------------- | ------------------------------------------------------------- |
| Actions do GitHub fixadas por SHA                          | **Ativo** (Fase 1)                                            |
| `permissions: contents: read` por padrão nos workflows     | **Ativo**                                                     |
| Varredura de segredos (gitleaks) em toda PR                | **Ativo**                                                     |
| Dependabot (npm, Gradle, Actions, Compose)                 | **Ativo**                                                     |
| Checksum do Gradle wrapper e da distribuição               | **Ativo**                                                     |
| Lockfile congelado no CI (`--frozen-lockfile`)             | **Ativo**                                                     |
| SBOM CycloneDX por artefato implantável                    | **Ativo** (embutido no bootJar e exposto em `/actuator/sbom`) |
| Assinatura de imagem (cosign/Sigstore) e proveniência SLSA | Fase 2                                                        |
| SAST/DAST e varredura de imagens (ex.: Trivy)              | Fase 2                                                        |
| Branch protection + revisão obrigatória + CODEOWNERS       | Configurar no GitHub ao publicar o repositório                |

## Gestão de vulnerabilidades

- Prazos de correção a partir da divulgação: crítica 7 dias, alta 30, média 90, baixa no próximo ciclo.
- Exceções registradas com dono, justificativa e prazo.
- Testes de intrusão por release relevante, apenas contra ambientes próprios.
