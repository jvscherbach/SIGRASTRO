# 13 — Padrões técnicos

SIGA Vistas é um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os documentos e gera o dossiê em PDF em ordem cronológica. Este documento resume as **regras técnicas** que o código e o banco devem seguir. O detalhamento de cada tema está em [`padroes/`](padroes/). As mesmas regras estão empacotadas como skill do Claude Code (`vistas-banco`), para uso durante a implementação.

Pontos que moldam todas as regras abaixo:

- **O banco do SIGA não é nosso.** O serviço só **lê** o Oracle do SIGA (`SIGA2_LICENCIAMENTO`, `SIGA2_CAMUNDA`, `SIGA2_OUTORGA`, `SIGA2_ARRECADACAO`). Nunca grava, nunca cria objeto, nunca roda migration lá (D14).
- **Schema próprio só na versão definitiva.** No protótipo não há banco próprio: usuários, perfis, auditoria, parâmetros e fila ficam em memória (D09). Na versão definitiva, um schema próprio **Oracle** guarda esses dados, versionado por Flyway: desenvolvimento em **Oracle Database 23 Free** (container), com compatibilidade obrigatória com **Oracle 19c**, que é o ambiente final se o projeto for aprovado.
- **Público atual: interno.** O acesso de interessados externos (perfil Operador como interessado, RN07) é regra futura e muda autenticação e exposição.

## Stack

| Camada | Tecnologia |
|---|---|
| Back-end | Java 21, Spring Boot 3, Maven Wrapper (`./mvnw`) |
| Leitura do SIGA | `NamedParameterJdbcTemplate` + `ojdbc11`, datasource somente leitura (sem JPA) |
| Schema próprio (definitivo) | Oracle — 23 Free local, 19c no ambiente final; Spring Data JPA + Flyway, `ddl-auto=validate` |
| Agendamento | Spring `@Scheduled` em processo (ver [`padroes/agendamento.md`](padroes/agendamento.md)) |
| PDF | Apache PDFBox 3, com arquivos em disco (`Loader.loadPDF` sobre `File`, cache em arquivos temporários; nunca tudo em memória) |
| Conversão | LibreOffice headless via JODConverter |
| HTTP de saída | `RestClient` |
| Front-end | Angular, servido por nginx |
| Execução | Docker Compose 2.24 ou mais novo; pools de conexão pequenos na fase inicial; homologação/produção ainda não definidos (alvo provável: Kubernetes do SIGA) |

## Módulos e quem é dono de cada funcionalidade

Pacote raiz `br.gov.mt.sema.vistas` (nome do projeto provisório).

| Pacote | Responsabilidade |
|---|---|
| `acesso` | usuários, perfis, vínculo do interessado (RN07), autenticação (simulada no protótipo) |
| `consulta` | busca de requerimento/processo, dados de exibição, situação, tarefa atual |
| `documento` | lista de documentos, vigência, sigilo, deduplicação, geometrias, conteúdo para visualização |
| `dossie` | pedidos de geração, fila, conversão, montagem do PDF, expiração, regra "nada incompleto sem decisão" (RN09) e continuação |
| `auditoria` | registro (somente inclusão) e consulta de eventos de documentos e de acessos |
| `configuracao` | parâmetros administráveis |
| `integration.siga` | **único** lugar que conhece tabelas, colunas, SQL e enums do banco do SIGA |
| `integration.licenciamento` | cliente da API de arquivos do Licenciamento (`/api/arquivo/{chave}`) |
| `integration.scorp` | cliente do SCORP (documentos pessoais de interessado, representantes e responsáveis técnicos) |
| `integration.arrecadacao` | taxas/DAR (desativado até a forma de obtenção ser confirmada) |

`config` (configuração técnica do Spring) e `shared` (erro comum, correlação, paginação, IDs técnicos) são transversais, não módulos de negócio. `shared` não é depósito de código sem dono.

O dono é o módulo que define o **significado de negócio**. Ex.: "quais documentos entram no dossiê" é de `documento`; "montar o PDF" é de `dossie`; "registrar que alguém baixou" é de `auditoria`, chamado por `dossie` via contrato interno.

## Fluxo para adicionar uma funcionalidade

1. Escolha o módulo pelo conceito de negócio dono.
2. Crie só os pacotes que o fluxo usa — sem placeholders vazios.
3. Regra e vocabulário em `domain`, sem dependência de entrega, persistência ou do SIGA.
4. Caso de uso em `application`, com as portas necessárias.
5. Implementações em `infrastructure`; HTTP em `api`.
6. Dado do SIGA: declare uma porta no módulo dono e implemente o adapter em `integration.siga`. Nenhum outro pacote escreve SQL contra `SIGA2_*`.
7. Para falar com outro módulo, use caso de uso/porta publicada — nunca DTO HTTP, entidade JPA ou repository de outro módulo.
8. Rode `./mvnw -Dtest=ArchitectureTest test` antes de terminar.

## Referências

- [`padroes/estrutura-de-pastas.md`](padroes/estrutura-de-pastas.md) — árvore do repositório, do back-end e do front-end na fase atual, e o que só nasce na versão definitiva.
- [`padroes/modulos-e-dependencias.md`](padroes/modulos-e-dependencias.md) — regras de dependência entre camadas e módulos, contratos internos válidos.
- [`padroes/integracao-externa.md`](padroes/integracao-externa.md) — fronteiras de `integration.siga`, `integration.licenciamento`, `integration.scorp`, `integration.arrecadacao`.
- [`padroes/persistencia-e-migrations.md`](padroes/persistencia-e-migrations.md) — dois datasources, leitura do SIGA sem JPA, schema próprio com Flyway, adapters em memória no protótipo.
- [`padroes/http-erros-openapi.md`](padroes/http-erros-openapi.md) — `ProblemDetail` com `errorCode`, correlação, `PageResponse<T>`, OpenAPI por ambiente.
- [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md) — ambientes, Compose, variáveis, o que não está decidido.
- [`padroes/agendamento.md`](padroes/agendamento.md) — tarefas agendadas: limpeza de dossiês expirados agora; expurgo e fila durável depois.
- [`padroes/checklist-verificacao.md`](padroes/checklist-verificacao.md) — testes e comandos antes de considerar pronto.

### Padrão de banco (schema próprio e leitura do SIGA)

- [`padroes/nomenclatura.md`](padroes/nomenclatura.md) — objetos em português e MAIÚSCULO; arquivo de migration em inglês.
- [`padroes/tipos-dados.md`](padroes/tipos-dados.md) — tipos por natureza do dado, com exemplos do Vistas.
- [`padroes/normalizacao.md`](padroes/normalizacao.md) — 3FN e o caso do "retrato" gravado na auditoria.
- [`padroes/indexacao.md`](padroes/indexacao.md) — índices de FK e dos filtros reais de auditoria e fila.
- [`padroes/documentacao.md`](padroes/documentacao.md) — `COMMENT ON`, dicionário de dados em HTML e MER em `docs/database/` (obrigatórios a cada migration), colunas do SIGA consumidas.
- [`padroes/mer.md`](padroes/mer.md) — MER abstrato e mapeado, referências lógicas ao SIGA.
- [`padroes/seguranca-privilegios.md`](padroes/seguranca-privilegios.md) — usuário de leitura do SIGA, owner e usuário de aplicação do schema próprio.
- [`padroes/connection-pooling.md`](padroes/connection-pooling.md) — dois pools HikariCP e a relação com `DOSSIE_THREADS`.

## O que NÃO está decidido (sinalize, não invente)

- Instância Oracle do schema próprio em homologação/produção.
- Infraestrutura de homologação/produção (provável Kubernetes do SIGA), registry, secret manager, hostnames.
- Autenticação definitiva (Portal do SIGA/identidade institucional; gov.br se houver público externo).
- Credencial de serviço para arquivos (API key do serviço de identidade ou acesso direto ao Pandora).
- Versão real do Oracle do SIGA em homologação/produção (assumir compatibilidade com 19c até confirmar).
- Se o Flyway roda no startup da API ou numa etapa do pipeline.

## Exceções registradas a "sem dependências novas"

- **`@playwright/test`** (devDependency do `front`, versão fixa, D35): testes e2e com API simulada; fora das imagens de produção, `npm audit` em 0.
- **`org.apache.pdfbox:pdfbox`** (produção, `api`), prevista desde a S0 como stack do PDF.

## Integração contínua

A pipeline roda no GitHub Actions (`.github/`). **Ela só valida e publica imagens: não há deploy** (D51). Nenhuma credencial do SIGA, da API de arquivos ou do SCORP entra nela, nem como secret; o único secret usado é o `GITHUB_TOKEN`, no login do GHCR. As variáveis que ativam os testes de fumaça (`SIGA_SMOKE`, `ARQUIVOS_SMOKE`) não são definidas, então esses testes ficam pulados.

| Arquivo | Quando roda | O que faz |
|---|---|---|
| `workflows/ci.yml` | Pull request, push na `main` e chamado pelo `release.yml` | Cinco jobs: `api` (`./mvnw -B clean verify`, com Testcontainers no Docker do runner), `front` (build, testes e `npm audit`), `e2e` (Playwright com API simulada, depois do `front`), `docker` (constrói as duas imagens, sem push e sem login) e `compose` (`config --services`, sem subir nada). Os relatórios de teste só são guardados em caso de falha, por 7 dias |
| `workflows/release.yml` | Tag `vX.Y.Z` ou execução manual sobre uma tag | Confere a ref, roda o `ci.yml` e publica `api` e `front` no GHCR (`ghcr.io/<dono>/<repositório>/api` e `/front`) com as tags `vX.Y.Z`, `X.Y` e `sha-<curto>`. **Nunca `latest`.** Build sem cache, com `provenance` e `sbom`. Não publica a cada merge |
| `workflows/codeql.yml` | Pull request, push na `main` e toda segunda-feira | Análise estática (CodeQL) de `java-kotlin` e `javascript-typescript` |
| `dependabot.yml` | Semanal | Atualizações de `maven`, `npm`, `github-actions` e `docker`, agrupando minor e patch (máx. 5 PRs por ecossistema) |

**Checks obrigatórios na `main`:** `api`, `front`, `e2e`, `docker` e `compose`. Os nomes dos jobs são estáveis de propósito. O `npm audit` bloqueante cobre só as dependências de produção; a auditoria completa (com as de desenvolvimento) é informativa (P25).

**Segurança dos workflows:** permissões mínimas por workflow e por job (`packages: write` só no job de publicação); actions só de `actions/*`, `docker/*` e `github/*`, fixadas pelo hash do commit com a versão em comentário; `actions/checkout` sem credencial persistida; sem `pull_request_target`; nenhuma entrada controlável por terceiros dentro de `run:`. A `api` e o `front` rodam sem root nas imagens (D50).

**O que não está na pipeline:** deploy (aguarda o ambiente de execução, P15 e P26), testes de fumaça contra o SIGA ou a API de arquivos reais (homologação do responsável, D27) e qualquer credencial.

**Como publicar uma versão:** no GitHub, **Releases → Draft a new release → Choose a tag**, digite `vX.Y.Z` (por exemplo `v0.1.0`), escolha **Create new tag on publish**, com o alvo na `main`, e publique. A tag dispara o `release.yml`. Em **Actions → Release** acompanhe os jobs; em **Packages** (página do repositório) conferem-se as imagens e as tags. Pré-releases (`v0.1.0-rc.1`) são aceitas e não geram a tag `X.Y`.

### Configuração no GitHub (passo a passo, feita pelo responsável)

Faça depois do primeiro push, quando os workflows já tiverem rodado ao menos uma vez (os checks só aparecem na lista depois disso).

1. **Actions permitidas e permissões do token.** Settings → Actions → General.
   - Em *Actions permissions*, escolha **Allow select actions and reusable workflows**, marque *Allow actions created by GitHub* e acrescente `docker/*` na lista de permitidas.
   - Em *Workflow permissions*, escolha **Read repository contents and packages permissions** (somente leitura por padrão). Cada job que precisa de mais declara no próprio arquivo.
2. **Proteção da `main`.** Settings → Branches → Add branch ruleset (ou *Add rule*) para `main`:
   - exigir *pull request* antes de integrar, com ao menos 1 aprovação;
   - exigir *status checks* e marcar `api`, `front`, `e2e`, `docker` e `compose`;
   - exigir que a branch esteja atualizada antes de integrar e bloquear *force push*.
3. **Segurança do código.** Settings → Code security:
   - ligar **Dependabot alerts** e **Dependabot security updates**;
   - ligar **Secret scanning** e **Push protection**, se disponíveis no plano;
   - o **CodeQL** já roda pelo workflow; se o repositório for privado, a gravação dos resultados exige o GitHub Code Security.
4. **Visibilidade do pacote no GHCR.** O pacote nasce **privado** na primeira publicação. Em Packages (perfil ou organização) abra `siga-vistas/api` e `siga-vistas/front` → Package settings: confira *Manage Actions access* (o repositório com permissão de escrita) e só mude a visibilidade para pública se o responsável decidir (R27).
5. **Criar uma versão e conferir as imagens.** Veja "Como publicar uma versão" acima. Depois: Actions → Release deve ficar verde (`validar-ref`, `ci`, `publicar (api)` e `publicar (front)`); o resumo de cada job lista as tags e o digest; em Packages devem aparecer `vX.Y.Z`, `X.Y` e `sha-<curto>`, e **nenhuma** `latest`.

## Verificação sem sistemas externos

Toda subida de verificação usa `compose.yaml` + `compose.local.yaml` + **`compose.verificacao.yaml`**, que esvazia as variáveis externas, e confirma as variáveis vazias dentro do container (D34; [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md)).

## Regras que não mudam

- Nenhuma escrita no banco do SIGA.
- Nenhum valor real de credencial, URL institucional ou token no repositório.
- Prompts e automações não fazem commit nem push; só `git diff` para revisão.
