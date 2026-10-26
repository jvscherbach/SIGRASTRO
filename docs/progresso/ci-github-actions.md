# Progresso: GitHub Actions (CI e publicação de imagens)

Escopo: a pipeline só valida e publica imagens; não há deploy.

| Bloco | Título | Modelo | Estado |
|---|---|---|---|
| 1 | CI base: jobs `api`, `front` e `e2e` | SONNET | [x] concluído |
| 2 | Imagens: `.dockerignore` e jobs `docker` e `compose` | SONNET | [x] concluído |
| 3 | Publicação de imagens (`release.yml`, GHCR) | OPUS | [x] concluído |
| 4 | Dependabot e CodeQL | SONNET | [x] concluído |
| 5 | Auditoria de segurança dos workflows | OPUS | [x] concluído |
| 6 | Documentação e tutorial do GitHub | SONNET | [x] concluído |

## Notas

- Bloco 1: `npm audit` está dividido em dois passos. O bloqueante usa `--omit=dev` (produção, limpo hoje). O completo é informativo (`continue-on-error`), porque hoje acusa 2 achados altos só em ferramentas de desenvolvimento (`body-parser`, `undici` via `@angular/build`), cuja correção exige atualização do Angular CLI. Registrar em `docs/10` no bloco 6.
- O Playwright usa o reporter HTML quando `CI` está definido (para o artefato de falha).
- Bloco 3: `release.yml` dispara em tag `v*.*.*` ou manualmente, mas o job `validar-ref` só deixa seguir se a ref for uma tag `vX.Y.Z` (pré-release opcional): execução manual numa branch falha sem publicar. O CI entra por `workflow_call` (`ci.yml` ganhou o gatilho; nenhum secret é repassado) e o `cancel-in-progress` passou a valer só para PR. Tags: `vX.Y.Z`, `X.Y` (omitida em pré-release) e `sha-<7>`; `latest=false` desliga o padrão do `metadata-action`. `provenance: mode=max` e `sbom: true`; sem atestado separado, então sem `id-token`/`attestations`. Para o bloco 5: pinar por hash e decidir se `ubuntu-latest` vira versão fixa. Para o bloco 6: o pacote nasce privado no GHCR na primeira publicação.
- Bloco 5 (auditoria): (1) actions de `ci.yml`, `release.yml` e `codeql.yml` pinadas pelo hash do commit da tag, com a versão em comentário (resolvidos por `git ls-remote`; a do CodeQL é tag anotada, pinada no commit); (2) `runs-on: ubuntu-24.04` no lugar de `ubuntu-latest`; (3) todo `actions/checkout` com `persist-credentials: false`; (4) `release.yml` publica com `no-cache: true` (sem cache `type=gha`, contra envenenamento de cache na imagem publicada); (5) CodeQL com `actions: read` (exigida em repositório privado); (6) diretiva `# syntax=docker/dockerfile:1` removida dos Dockerfiles (baixava um frontend por tag flutuante; o embutido do BuildKit basta); (7) front sem root na porta 8080 (D50; `nginx.conf`, Dockerfile, `compose.local.yaml`, README). Conferido sem achado: permissões (nenhum `write-all`; `packages: write` só no job de publicação), único secret `GITHUB_TOKEN`, sem `pull_request_target`, sem `${{ }}` em `run:`, sem URL institucional; testes de fumaça só com `SIGA_SMOKE`/`ARQUIVOS_SMOKE`, que a pipeline não define; artefatos com token fictício (`TOKEN-SECRETO-DE-TESTE`) e Playwright sem trace, vídeo ou screenshot; imagens sem `.env*`, sem segredo no histórico e `api` como `vistas`. Pendências que dependem do responsável: P22 a P24 em `docs/10`. Para o bloco 6: front na porta 8080 do container; tutorial deve incluir "Allow select actions".
- Bloco 6: seção "Integração contínua" e tutorial do GitHub em `docs/13`; D51 em `docs/09`; P25, P26 e R27 em `docs/10`; selo do CI (comentado) e link no `README.md`.

## Fechamento

**Os 6 blocos**

1. `ci.yml` com `api`, `front` e `e2e`.
2. `.dockerignore` e os jobs `docker` e `compose`.
3. `release.yml`: publicação no GHCR só em tag `vX.Y.Z`, sem `latest`, com o CI como pré-requisito.
4. Dependabot e CodeQL.
5. Auditoria: actions pinadas por hash, permissões mínimas, front sem root (D50).
6. Documentação e tutorial.

**O que o responsável configura no GitHub** (passo a passo em `docs/13`, "Configuração no GitHub"): actions permitidas (`actions/*`, `docker/*`, `github/*`), permissões do token somente leitura, regra da `main` com os checks `api`, `front`, `e2e`, `docker` e `compose` e revisão, Dependabot alerts e security updates, secret scanning, visibilidade do pacote no GHCR e a primeira tag `vX.Y.Z`. Antes de tornar o repositório ou os pacotes públicos, conferir o histórico (R27).

**O que fica para o deploy:** ambiente de execução e servidor da SEMA (P15, P26), gestão de segredos no destino, manifesto de implantação (front na porta 8080 do container, D50) e o deploy automatizado em si, em outro prompt. Também ficam pendentes P22 a P25 (digest das imagens base, `npm ci --ignore-scripts`, restrições só configuráveis no GitHub e o `npm audit` de desenvolvimento).
