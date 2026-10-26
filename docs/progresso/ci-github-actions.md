# Progresso: GitHub Actions (CI e publicação de imagens)

Escopo: a pipeline só valida e publica imagens; não há deploy.

| Bloco | Título | Modelo | Estado |
|---|---|---|---|
| 1 | CI base: jobs `api`, `front` e `e2e` | SONNET | [x] concluído |
| 2 | Imagens: `.dockerignore` e jobs `docker` e `compose` | SONNET | [ ] pendente |
| 3 | Publicação de imagens (`release.yml`, GHCR) | OPUS | [ ] pendente |
| 4 | Dependabot e CodeQL | SONNET | [ ] pendente |
| 5 | Auditoria de segurança dos workflows | OPUS | [ ] pendente |
| 6 | Documentação e tutorial do GitHub | SONNET | [ ] pendente |

## Notas

- Bloco 1: `npm audit` está dividido em dois passos. O bloqueante usa `--omit=dev` (produção, limpo hoje). O completo é informativo (`continue-on-error`), porque hoje acusa 2 achados altos só em ferramentas de desenvolvimento (`body-parser`, `undici` via `@angular/build`), cuja correção exige atualização do Angular CLI. Registrar em `docs/10` no bloco 6.
- O Playwright usa o reporter HTML quando `CI` está definido (para o artefato de falha).
