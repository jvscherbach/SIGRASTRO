# Progresso: GitHub Actions (CI e publicação de imagens)

Escopo: a pipeline só valida e publica imagens; não há deploy.

| Bloco | Título | Modelo | Estado |
|---|---|---|---|
| 1 | CI base: jobs `api`, `front` e `e2e` | SONNET | [x] concluído |
| 2 | Imagens: `.dockerignore` e jobs `docker` e `compose` | SONNET | [x] concluído |
| 3 | Publicação de imagens (`release.yml`, GHCR) | OPUS | [x] concluído |
| 4 | Dependabot e CodeQL | SONNET | [ ] pendente |
| 5 | Auditoria de segurança dos workflows | OPUS | [ ] pendente |
| 6 | Documentação e tutorial do GitHub | SONNET | [ ] pendente |

## Notas

- Bloco 1: `npm audit` está dividido em dois passos. O bloqueante usa `--omit=dev` (produção, limpo hoje). O completo é informativo (`continue-on-error`), porque hoje acusa 2 achados altos só em ferramentas de desenvolvimento (`body-parser`, `undici` via `@angular/build`), cuja correção exige atualização do Angular CLI. Registrar em `docs/10` no bloco 6.
- O Playwright usa o reporter HTML quando `CI` está definido (para o artefato de falha).
- Bloco 3: `release.yml` dispara em tag `v*.*.*` ou manualmente, mas o job `validar-ref` só deixa seguir se a ref for uma tag `vX.Y.Z` (pré-release opcional): execução manual numa branch falha sem publicar. O CI entra por `workflow_call` (`ci.yml` ganhou o gatilho; nenhum secret é repassado) e o `cancel-in-progress` passou a valer só para PR. Tags: `vX.Y.Z`, `X.Y` (omitida em pré-release) e `sha-<7>`; `latest=false` desliga o padrão do `metadata-action`. `provenance: mode=max` e `sbom: true`; sem atestado separado, então sem `id-token`/`attestations`. Para o bloco 5: pinar por hash e decidir se `ubuntu-latest` vira versão fixa. Para o bloco 6: o pacote nasce privado no GHCR na primeira publicação.
