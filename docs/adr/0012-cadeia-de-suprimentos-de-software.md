# ADR-0012: Segurança da cadeia de suprimentos de software

- Estado: Aceito
- Data: 2026-11-03

## Decisão

Ativo desde a Fase 1:

- GitHub Actions fixadas por SHA de commit, com comentário da versão; permissões mínimas (`contents: read`).
- Checkout sem persistir credenciais.
- Lockfile congelado no CI; wrapper do Gradle com checksum da distribuição e validação do `gradle-wrapper.jar`.
- Varredura de segredos (gitleaks) em todo push e PR.
- Dependabot para npm, Gradle, Actions e imagens do compose.
- Versões de imagem sempre explícitas (nunca `latest`).

A partir da Fase 2 (primeiro artefato implantável):

- SBOM CycloneDX por imagem/artefato, anexado ao release.
- Assinatura de imagens com cosign (Sigstore) e atestado de proveniência SLSA nível 3.
- Varredura de vulnerabilidades em imagens e dependências bloqueando PR com crítica corrigível.
- Admission control no Kubernetes aceitando apenas imagens assinadas.

## Consequências

- Atualizar uma Action exige trocar o SHA (Dependabot faz isso).
