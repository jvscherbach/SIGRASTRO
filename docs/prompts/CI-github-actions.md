# GitHub Actions: CI e publicação de imagens (escopo atual, sem deploy)

> **Forma de trabalho:** a tarefa é dividida em **6 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**, e cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões.

## Contexto

SIGA Vistas: `api/` (Java 21, Spring Boot 3.5, Maven Wrapper) e `front/` (Angular 21, Node 22, Vitest e Playwright). Dockerfiles em `api/` e `front/`. O destino de produção ainda não existe: **nesta etapa a pipeline só valida e publica imagens**. Não há deploy; ele será acrescentado depois, em outro prompt, quando houver servidor da SEMA.

## Regras

- **Você não executa nada contra o SIGA real nem contra a API de arquivos real.** Nenhuma credencial entra na pipeline: **sem** `SIGA_DB_*`, `LICENCIAMENTO_API_*` nem `SCORP_API_URL`, nem como secret. Nenhum segredo entra na imagem nem no build.
- Nunca abra, imprima ou copie o `.env.local`.
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Sem URL institucional, nome de servidor ou dado real nos workflows, comentários ou docs.

## Protocolo das duas sessões

**1. Identifique a sessão.** A primeira mensagem do responsável traz uma linha `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Se ela não existir, pergunte qual é a sessão e espere. Não deduza pelo seu nome de modelo.

**2. Descubra onde parou.** Leia `docs/progresso/ci-github-actions.md`. Se o arquivo não existir, nenhum bloco foi feito e o primeiro pendente é o bloco 1. Se existir, o primeiro bloco **não marcado como concluído** é o bloco atual.

**3. Confira se o bloco é seu** pela tabela abaixo.
- **É seu:** execute só esse bloco (não adiante o seguinte).
- **Não é seu:** **não faça nada**, não crie nem altere arquivo. Responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <OPUS|SONNET>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <...>".` e pare.

**4. Ao concluir o bloco (ponto de commit):**
1. Deixe o repositório **íntegro**: os comandos do bloco rodam e os testes existentes continuam verdes. Um bloco não pode deixar o repositório quebrado.
2. Marque o bloco como concluído em `docs/progresso/ci-github-actions.md` (crie o arquivo no bloco 1, com a tabela dos 6 blocos, o modelo de cada um e o estado). A marcação entra no mesmo commit do bloco.
3. Rode `git diff --stat`.
4. Responda neste formato:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Validação: <o que rodou e o resultado>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>.
Depois do commit, <responda "continuar" nesta sessão | abra/volte à sessão X e envie o mesmo prompt com "SESSÃO: X">.
```

5. **Pare.** Se o próximo bloco for da mesma sessão, espere "continuar". Se for da outra, a sessão atual termina aí.

Tipos de commit permitidos: `ci`, `chore`, `docs`, `feat`, `fix`.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | CI base: jobs `api`, `front` e `e2e` | **SONNET** | Passos conhecidos, sem credencial |
| 2 | Imagens: `.dockerignore` e jobs `docker` e `compose` | **SONNET** | Mecânico, com checagem de arquivos |
| 3 | Publicação de imagens (`release.yml`, GHCR) | **OPUS** | Permissões de escrita, tags, cadeia de suprimentos |
| 4 | Dependabot e CodeQL | **SONNET** | Configuração padrão |
| 5 | Auditoria de segurança dos workflows | **OPUS** | Revisão de permissões, segredos e pinagem |
| 6 | Documentação e tutorial do GitHub | **SONNET** | Texto, a partir do que foi feito |

---

## Bloco 1 (SONNET): CI base

**Commit sugerido:** `ci: valida api, front e e2e em pull requests e na main`

Crie `.github/workflows/ci.yml` com:

- **Gatilhos:** `pull_request` e `push` na `main`. `concurrency` por ref, cancelando execuções antigas do mesmo PR.
- **Permissões:** `permissions: contents: read` no topo.
- **Job `api`** (ubuntu-latest): `actions/setup-java` Temurin 21 com cache `maven`; `./mvnw -B clean verify` no diretório `api`. Os testes de Testcontainers (Oracle 23 Free) usam o Docker do runner: não desligue nada. Os testes de fumaça contra o SIGA real continuam **pulados por padrão** (confirme que as variáveis que os ativam não estão definidas). Publique `target/surefire-reports` e `target/failsafe-reports` como artefato **só em caso de falha** (retenção 7 dias).
- **Job `front`:** `actions/setup-node` com Node 22 e cache `npm`; `npm ci`, `npm run build`, `npm test -- --watch=false`, `npm audit --audit-level=high` (falha em alta ou crítica; mostre o relatório). Se o `npm audit` for instável por causa da rede, deixe um passo separado e informativo, e diga isso na entrega.
- **Job `e2e`:** depende do `front`; `npx playwright install --with-deps chromium` (com cache) e `npm run e2e`. Publique `playwright-report` como artefato **só em caso de falha**. Os e2e não podem tocar em nenhum serviço real.
- Nomes estáveis de job (`api`, `front`, `e2e`), para virarem checks obrigatórios.
- **Fixe as actions por versão completa** (por exemplo `@v4.x.y`), nunca `@main` nem tag flutuante. Só actions oficiais (`actions/*`).

**Validação:** valide a sintaxe do YAML (`actionlint` se disponível por download oficial; senão parser YAML) e rode localmente `./mvnw -B clean verify`, `npm ci`, build, testes e `npm run e2e`, para provar que os comandos funcionam como escritos.

## Bloco 2 (SONNET): imagens e Compose

**Commit sugerido:** `ci: constrói as imagens e valida o compose sem publicar`

- Crie ou ajuste `.dockerignore` em `api/` e `front/`: excluir `.env*`, `node_modules`, `target`, `.git`, `dossies-tmp`, `.secrets`. Confirme que os Dockerfiles não copiam `.env*` nem arquivos de segredo.
- No `ci.yml`, acrescente:
  - **Job `docker`**, dependente de `api` e `front`: só **constrói** as duas imagens (`docker/build-push-action` com `push: false`, cache `type=gha`). Sem login em registry.
  - **Job `compose`:** `docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml config --services` (esperado: só `api` e `front`), sem subir nada e sem `--env-file`.
- Mesmas regras de pinagem do bloco 1 (`docker/*` oficiais, versão completa).

**Validação:** `docker build` das duas imagens, se o Docker local permitir, e o `compose config --services`. Valide o YAML.

## Bloco 3 (OPUS): publicação de imagens

**Commit sugerido:** `ci(release): publica imagens no GHCR em tags de versão`

Crie `.github/workflows/release.yml`:

- **Gatilho:** `push` de tag `v*.*.*` e `workflow_dispatch`. **Não** publica a cada merge.
- **Permissões por job**, no menor nível: `contents: read`, `packages: write` (e `id-token`/`attestations` só se usar atestado). Login no **GHCR** com `GITHUB_TOKEN` (`docker/login-action`). Nenhum PAT, nenhum outro secret.
- **Pré-requisito:** o CI precisa estar verde para o commit da tag. Reutilize o `ci.yml` por `workflow_call` (ajuste o `ci.yml` para aceitá-lo), mantendo **uma** definição dos passos, sem duplicar.
- **Imagens:** `ghcr.io/<owner>/<repo>/api` e `.../front` (minúsculas), com `docker/metadata-action`: tags `vX.Y.Z`, `X.Y` e `sha-<curto>`. **Não use `latest`.** Plataforma `linux/amd64`, cache `type=gha`, `provenance` e `sbom` ligados.
- **Sem segredos no build:** nenhum `build-arg` com credencial.
- Resumo do job (`$GITHUB_STEP_SUMMARY`) com as tags publicadas e o digest.
- Proteja contra injeção: nenhum `${{ github.* }}` de entrada controlável (nome de branch, título) dentro de `run:`; use variáveis de ambiente.

**Validação:** YAML válido; simule a lógica de tags do `metadata-action` em texto (sem publicar); confirme por busca que não há `latest`, `@main`, `secrets.` além de `GITHUB_TOKEN`.

## Bloco 4 (SONNET): Dependabot e CodeQL

**Commit sugerido:** `ci: configura Dependabot e análise CodeQL`

- `.github/dependabot.yml`: `maven` (`/api`), `npm` (`/front`), `github-actions` (`/`) e `docker` (`/api`, `/front`), semanal, agrupando patch e minor, limite de 5 PRs abertos por ecossistema.
- `.github/workflows/codeql.yml`: CodeQL para `java-kotlin` e `javascript-typescript`, em PR, em push na `main` e uma vez por semana. Para Java, build manual com `./mvnw -B -DskipTests package` no `api`. Permissões: `contents: read`, `security-events: write`. Actions oficiais, versão completa.

**Validação:** YAML válido.

## Bloco 5 (OPUS): auditoria de segurança dos workflows

**Commit sugerido:** `ci(seguranca): endurece permissões e pinagem dos workflows`

Revise **todos** os arquivos de `.github/` e os Dockerfiles com olhar de segurança e corrija o que achar:

- Permissões mínimas por workflow e por job; nenhum `write-all`.
- Pinagem: as actions de terceiros por **hash do commit** (com a versão em comentário) ou, no mínimo, por versão completa; nenhuma `@main`, `@master` ou tag flutuante. Só `actions/*`, `docker/*` e `github/*`.
- Nenhum `secrets.` além do `GITHUB_TOKEN`; nenhuma URL institucional, nome de servidor, `.env` ou dado real; nenhum `pull_request_target`; nenhum `run:` com entrada controlável por terceiros.
- Artefatos de falha sem dado sensível (relatórios de teste e do Playwright não podem conter token, chave ou dado real; confira o que os testes imprimem).
- Imagens sem segredos (inspecione o que os Dockerfiles copiam) e rodando sem root, se o Dockerfile permitir (relate se não permitir).
- Relate cada achado e o que corrigiu; o que não puder corrigir sem decisão do responsável, liste em `docs/10`.

**Validação:** busca por `secrets.`, `@main`, `@master`, `latest`, `pull_request_target`, `.env`, URLs; YAMLs válidos.

## Bloco 6 (SONNET): documentação e tutorial

**Commit sugerido:** `docs: documenta a pipeline e o passo a passo de configuração no GitHub`

- `docs/13-padroes-tecnicos.md` (ou o arquivo mais adequado): seção "Integração contínua", descrevendo cada workflow, os checks que devem ser obrigatórios na `main`, o que **não** está na pipeline (deploy, teste de fumaça, qualquer credencial) e como publicar uma versão (criar a tag `vX.Y.Z` pela interface do GitHub).
- `docs/09`: decisão "pipeline só valida e publica imagens; deploy depende do servidor da SEMA (pendente)". `docs/10`: pendência "Deploy automatizado: aguardando definição do ambiente de execução" e risco "repositório com histórico a conferir antes de tornar público".
- `README.md`: selo do CI (comentado se o repositório ainda não existir) e link para a seção.
- **Tutorial curto, para o responsável executar no GitHub:** (1) Settings → Branches → regra da `main` exigindo os checks `api`, `front`, `e2e`, `docker` e `compose` e PR com revisão; (2) Settings → Actions → Workflow permissions: somente leitura por padrão; (3) Settings → Code security: Dependabot alerts e security updates, e secret scanning se disponível; (4) visibilidade do pacote no GHCR; (5) como criar uma tag e conferir as imagens.
- Marque o bloco 6 no arquivo de progresso e escreva o **fechamento** da tarefa: lista dos 6 blocos, o que o responsável configura no GitHub e o que fica para o deploy.

## Critérios de aceite (ao final do bloco 6)

1. `ci.yml` com cinco jobs de nomes estáveis, sem nenhuma credencial do SIGA, da API de arquivos ou do SCORP.
2. `release.yml` publica no GHCR só em tag `v*.*.*` (ou execução manual), sem `latest`, com o CI verde como pré-requisito.
3. Actions fixadas; permissões mínimas por workflow e por job.
4. `.dockerignore` nos dois diretórios, sem `.env*` nas imagens.
5. Dependabot e CodeQL configurados.
6. Docs atualizados e tutorial entregue.
7. Cada bloco foi executado pela sessão atribuída, terminou íntegro e foi marcado em `docs/progresso/ci-github-actions.md`.
8. Nenhum comando git além de `git diff`; nenhum dado real no repositório.
