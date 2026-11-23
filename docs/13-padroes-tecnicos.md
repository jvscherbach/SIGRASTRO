# 13 — Padrões técnicos

Rastro é um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os documentos e gera o dossiê em PDF em ordem cronológica. Este documento resume as **regras técnicas** que o código e o banco devem seguir. O detalhamento de cada tema está em [`padroes/`](padroes/). As mesmas regras estão empacotadas como skill do Claude Code (`vistas-banco`), para uso durante a implementação.

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

Pacote raiz `br.gov.mt.sema.rastro` (antes `br.gov.mt.sema.vistas`; renomeado na Fase 2 da R1, D74). Propriedades com prefixo `rastro.*`; o prefixo antigo `vistas.*` continua aceito como alias, a antiga vence e o log avisa a depreciação sem o valor (`config.AliasDasPropriedadesAntigas`).

| Pacote | Responsabilidade |
|---|---|
| `acesso` | usuários, perfis, vínculo do interessado (RN07), autenticação (simulada no protótipo) |
| `consulta` | busca de requerimento/processo, dados de exibição, situação, tarefa atual |
| `documento` | lista de documentos, vigência, sigilo, deduplicação, geometrias, conteúdo para visualização |
| `dossie` | pedidos de geração, fila, conversão, montagem do PDF, expiração, regra "nada incompleto sem decisão" (RN09) e continuação |
| `auditoria` | registro (somente inclusão) e consulta de eventos de documentos e de acessos |
| `configuracao` | parâmetros administráveis |
| `tramitacao` | posição do requerimento no fluxo do Camunda (etapa atual, desde quando, com quem) pelo catálogo de etapas em YAML (`resources/rastro/etapas/`); não depende de `documento` nem de `dossie` (S7, docs/15) |
| `financeiro` | histórico financeiro do requerimento (S9): lançamentos de taxa de `LANCAMENTO_TAXA` (lidos por `integration.siga.LancamentosDoSigaJdbc`, atrás da porta `LancamentosDoRequerimento`), regra de validade (`ClassificarValidade`, bloco 2) e, nos blocos seguintes, situação, vencimento por data e isenção; não depende de `documento` nem de `dossie`; da `tramitacao`, só por porta (blocos 4 e 5) |
| `integration.siga` | **único** lugar que conhece tabelas, colunas, SQL e enums do banco do SIGA |
| `integration.licenciamento` | cliente da API de arquivos do Licenciamento (`/api/arquivo/{chave}`) |
| `integration.scorp` | cliente do SCORP (documentos pessoais de interessado, representantes e responsáveis técnicos) |
| `integration.arrecadacao` | taxas/DAR (desativado até a forma de obtenção ser confirmada) |
| `integration.libreoffice` | conversor de documentos para PDF (LibreOffice headless via JODConverter); **único** lugar que usa JODConverter, UNO e jsoup |

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
- [`padroes/tipos-dados.md`](padroes/tipos-dados.md) — tipos por natureza do dado, com exemplos do Rastro.
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

- **S6, bloco 7, servidor MCP** (`tools/mcp-rastro/package.json`, versões fixas, `package-lock.json` versionado, fora das imagens; só para o Claude Code local): `@modelcontextprotocol/sdk` **1.32.2** (MIT, publicado em 23/10/2026) e `zod` **4.6.7** (MIT; a 3.25.x tinha alerta alto de consumo de memória, GHSA-mrhx-c7xc-m3w5, corrigido na 4.6.6). Transitivas: 85 MIT, 7 ISC, 2 BSD-3-Clause e 1 BSD-2-Clause; `npm audit --omit=dev` em 0. Testes (`npm --prefix tools/mcp-rastro test`) só locais, fora do CI, porque exigiriam `npm ci` da pasta a cada PR.

- **`@playwright/test`** (devDependency do `front`, versão fixa, D35): testes e2e com API simulada; fora das imagens de produção, `npm audit` em 0.
- **`org.apache.pdfbox:pdfbox`** (produção, `api`), prevista desde a S0 como stack do PDF.
- **S3, conversão** (todas gratuitas e de código aberto, sem copyleft forte; versões fixas no `pom.xml`):

| Dependência | Versão | Licença | Escopo | Uso |
|---|---|---|---|---|
| `org.jodconverter:jodconverter-local-lo` (traz `jodconverter-local` e `jodconverter-core`) | 4.4.9 | Apache 2.0 | produção | controle do LibreOffice headless. O `jodconverter-spring-boot-starter` 4.4 é de Spring Boot 2.7 (auto-configuração por `spring.factories`) e **não** é usado: o adapter monta o `LocalOfficeManager` |
| `org.libreoffice:libreoffice` (bibliotecas UNO, transitiva do `-lo`) | 24.8.4 | MPL 2.0 (copyleft fraco, por arquivo; uso sem modificação) | produção | ponte Java–LibreOffice |
| `com.google.code.gson:gson` (transitiva do `jodconverter-core`, versão do Spring Boot) | gerenciada | Apache 2.0 | produção | registro de formatos do JODConverter |
| `org.jsoup:jsoup` | 1.21.1 | MIT | produção | limpeza do HTML antes da conversão |
| `org.apache.poi:poi-ooxml` | 5.4.1 | Apache 2.0 | **teste** | documentos Office sintéticos (inclusive criptografados) |
| LibreOffice (`libreoffice-writer-nogui`, `libreoffice-calc-nogui`, Ubuntu 24.04) | do repositório da distribuição | MPL 2.0 | imagem da `api` | conversor |
| Fontes `fonts-liberation`, `fonts-dejavu-core`, `fonts-crosextra-carlito`, `fonts-crosextra-caladea` | do repositório da distribuição | SIL OFL 1.1 / licença livre do DejaVu | imagem da `api` | acentuação e métrica compatível com Arial, Times, Calibri e Cambria |

### Dependências do front (R2)

| Dependência | Versão | Licença | Escopo | Motivo |
|---|---|---|---|---|
| `@fontsource/bricolage-grotesque` | 5.3.0 (fixa) | SIL OFL 1.1 | produção | fonte dos títulos do SIGRASTRO (pesos 500 e 700, subconjunto latino), servida localmente |
| `@fontsource/instrument-sans` | 5.3.0 (fixa) | SIL OFL 1.1 | produção | fonte do corpo (pesos 400, 500, 600 e 700, subconjunto latino), servida localmente |

As fontes entram pelo npm e são empacotadas no build, com `font-display: swap`: o front não chama serviço de terceiros (Google Fonts) em tempo de execução. `npm audit --omit=dev` em 0.

## Integração contínua

A pipeline roda no GitHub Actions (`.github/`). **Ela só valida e publica imagens: não há deploy** (D51). Nenhuma credencial do SIGA, da API de arquivos ou do SCORP entra nela, nem como secret; o único secret usado é o `GITHUB_TOKEN`, no login do GHCR. As variáveis que ativam os testes de fumaça (`SIGA_SMOKE`, `ARQUIVOS_SMOKE`) não são definidas, então esses testes ficam pulados.

| Arquivo | Quando roda | O que faz |
|---|---|---|
| `workflows/ci.yml` | Pull request, push na `main`, execução manual (`workflow_dispatch`, com a opção `incluir_conversao`) e chamado pelo `release.yml` | `mudancas` decide se a mudança é só de documentação (`docs/`, `*.md`, `.claude/`); nesse caso `api`, `front`, `e2e` e `docker` ficam pulados (D53). **S6, bloco 6:** o próprio `mudancas` roda os testes das guardas do Claude Code (`node --test` dos três `.claude/hooks/*.test.mjs`, com o Node que já vem no runner, sem instalar nada, em segundos), porque é o único job que sempre executa e o filtro trata `.claude/` como documentação. **S8, bloco 6:** o mesmo job instala (`npm ci`), testa (`npm test`) e audita (`npm audit --omit=dev --audit-level=high`) o servidor MCP em `tools/mcp-rastro`, com o Node do runner (engines >=20), em segundos e sem recurso novo nem pago. Cinco jobs de verificação: `api` (roda `./mvnw -B clean verify`, com Testcontainers no Docker do runner; **S6, bloco 5:** a instalação do LibreOffice e os testes com a tag `conversao-libreoffice` só rodam em push na `main` e na execução manual com `incluir_conversao`; em PR comum e em PR do Dependabot o passo é pulado, o build passa `-Dtestes.excluidos=desempenho,conversao-libreoffice` e o resumo do job diz "Testes de conversão com LibreOffice pulados neste PR"; quando rodam, esses testes **falham** sem o `soffice` se a variável `CI` está definida, e só são pulados, com aviso, na máquina do desenvolvedor; S3, bloco 8), `front` (build, testes e `npm audit`), `e2e` (Playwright com API simulada, depois do `front`), `docker` (constrói as duas imagens, sem push e sem login) e `compose` (`config --services`, sem subir nada). Os relatórios de teste só são guardados em caso de falha, por 7 dias |
| `workflows/release.yml` | Tag `vX.Y.Z` ou execução manual sobre uma tag | Confere a ref, roda o `ci.yml` e publica `api` e `front` no GHCR (`ghcr.io/<dono>/<repositório>/api` e `/front`) com as tags `vX.Y.Z`, `X.Y` e `sha-<curto>`. **Nunca `latest`.** Build sem cache, com `provenance` e `sbom`. Não publica a cada merge |
| `dependabot.yml` | Mensal (D53) | Atualizações de `maven`, `npm` (`/front` e, desde a S8 bloco 6, `/tools/mcp-rastro`), `github-actions` e `docker`, agrupando minor e patch (máx. 5 PRs por ecossistema) |

**Checks da `main`:** `api`, `front`, `e2e`, `docker` e `compose` (nomes estáveis de propósito; `mudancas` é auxiliar). Em repositório **privado no plano gratuito não há proteção de branch** (tabela abaixo): os checks rodam e aparecem no PR, mas o GitHub não impede o merge. A disciplina é o fluxo (passo 3 do tutorial). Se um dia houver proteção, esses cinco viram obrigatórios; os jobs pulados por mudança só de documentação contam como sucesso, porque o workflow sempre dispara e o pulo é por `if` (não há `paths-ignore`, que deixaria o check pendente). O `npm audit` bloqueante cobre só as dependências de produção; a auditoria completa (com as de desenvolvimento) é informativa (P25).

**Segurança dos workflows:** permissões mínimas por workflow e por job (`packages: write` só no job de publicação); actions só de `actions/*` e `docker/*`, fixadas pelo hash do commit com a versão em comentário; `actions/checkout` sem credencial persistida; sem `pull_request_target`; nenhuma entrada controlável por terceiros dentro de `run:`. A `api` e o `front` rodam sem root nas imagens (D50).

**O que não está na pipeline:** deploy (aguarda o ambiente de execução, P15 e P26), testes de fumaça contra o SIGA ou a API de arquivos reais (homologação do responsável, D27), qualquer credencial e qualquer recurso pago (D52): code scanning, secret scanning, Environments com aprovação e atestados de artefato (`actions/attest`) ficam fora.

**Como publicar uma versão:** no GitHub, **Releases → Draft a new release → Choose a tag**, digite `vX.Y.Z` (por exemplo `v0.1.0`), escolha **Create new tag on publish**, com o alvo na `main`, e publique. A tag dispara o `release.yml`. Em **Actions → Release** acompanhe os jobs; em **Packages** (página do repositório) conferem-se as imagens e as tags. Pré-releases (`v0.1.0-rc.1`) são aceitas e não geram a tag `X.Y`.

### Custo: repositório privado no plano GitHub Free (D52, D53)

Conferido na documentação oficial do GitHub em 26/10/2026 (páginas de planos, cobrança do Actions e do Packages, runners, ambientes, recursos de segurança). "Não confirmado" quer dizer que a documentação lida não responde; nesses casos o recurso **não é usado** e a dúvida está em [10](10-pendencias-e-riscos.md).

| Recurso | Situação no Free privado | Decisão |
|---|---|---|
| Minutos do GitHub Actions (runner padrão Linux) | **Limitado:** 2.000 min/mês. Em repositório privado o runner `ubuntu-24.04` tem 2 vCPU e 8 GB (o público tem 4 vCPU). Sem forma de pagamento cadastrada, o uso é **bloqueado** ao fim da cota, sem cobrança | Usar, com economia: só documentação não roda os jobs pesados, PR antigo é cancelado, Dependabot mensal, `timeout-minutes` em todo job (R28) |
| Armazenamento de artefatos do Actions | **Limitado:** 500 MB, compartilhados com o GitHub Packages (fora o GHCR) | Usar: artefatos só em falha, 7 dias |
| Cache do Actions (`actions/cache`, `setup-*`, `type=gha`) | **Gratuito até 10 GB por repositório**; só é cobrado se o limite for configurado acima disso. Entradas sem acesso há 7 dias são removidas | Usar; **não aumentar** o limite do cache (R30) |
| GHCR (Container registry) | **Gratuito "por enquanto"**: armazenamento e tráfego do Container registry não são cobrados hoje; o GitHub promete aviso com um mês de antecedência. O Packages em geral tem cota de 500 MB e 1 GB de tráfego/mês no Free. Tráfego a partir do Actions com `GITHUB_TOKEN` é gratuito | Usar, com retenção manual (passo 5 do tutorial) (R29) |
| Proteção de branch (checks e revisão obrigatórios) | **Pago:** "Protected branches" e "Required pull request reviewers" em privado são do GitHub Pro/Team | **Não usar.** Fluxo disciplinado (passo 3) |
| Rulesets de repositório | **Não confirmado:** uma página do GitHub os lista entre os recursos "para todos os planos" e outra limita a Team/Enterprise | **Não usar** (P27) |
| Dependabot (alertas, atualizações de segurança e de versão) e dependency graph | **Gratuito** ("Available for all GitHub plans"). Os PRs do Dependabot rodam o CI e consomem minutos | Usar; versões mensais |
| Secret scanning e push protection | **Pago** em privado (GitHub Secret Protection); gratuito só em repositório público | **Não usar.** Varredura local com `gitleaks` (passo 6) |
| Code scanning (CodeQL) | **Pago** em privado (GitHub Code Security) | Removido (D52) |
| Environments e aprovação manual (*required reviewers*) | **Indisponível:** no Free, ambientes só em repositório público | Não usado (o `release.yml` não tem Environment) |
| Atestados de artefato (`actions/attest`) | **Indisponível** em privado no Free | Não usado; `provenance` e `sbom` do Buildx ficam na própria imagem no GHCR |
| Configurações do Actions (actions permitidas, token somente leitura) | **Gratuito**, sem restrição de plano na documentação | Usar (passo 1) |
| `actions/*`, `docker/*`, Maven Wrapper, `npm audit`, Playwright, Testcontainers, PDFBox, LibreOffice | Código aberto, sem cota paga | Usar |
| Docker Hub (imagens base e `gvenzl/oracle-free` do Testcontainers) | **Limitado:** pull anônimo tem limite de taxa (valores **não confirmados** nesta auditoria) | Usar sem login; falha por limite só atrasa o CI (R31) |

**Consumo estimado** (estimativa, **não medida**; confira na primeira semana em Settings → Billing): `api` 10 a 15 min (13 a 19 min com a conversão, S3: instalação do LibreOffice ~0,5 a 1 min e testes de conversão ~2,5 a 3 min, medidos localmente, R40) (2 vCPU, Oracle por Testcontainers), `front` 3 a 5, `e2e` 4 a 6, `docker` 5 a 10, `compose` e `mudancas` 1 cada (o GitHub arredonda cada job para cima, em minutos). Uma execução completa fica entre **25 e 38 min**; um PR com dois pushes e o push na `main` gasta perto de 100 min, e só documentação fica em ~2 min. A cota de 2.000 min cobre algo como 15 a 20 PRs de código por mês, contando os do Dependabot. Uma release roda o CI de novo e os dois `publicar` sem cache (mais 20 a 30 min).

### Configuração no GitHub (passo a passo, feita pelo responsável)

Só usa o que é gratuito em repositório privado no plano Free. Faça depois do primeiro push.

1. **Actions permitidas e permissões do token.** Settings → Actions → General.
   - Em *Actions permissions*, escolha a opção de permitir as actions do dono **e algumas de fora** (*Allow \<dono\>, and select non-\<dono\>, actions and reusable workflows*; o texto varia com a interface), marque *Allow actions created by GitHub* e acrescente `docker/*` na lista.
   - Em *Workflow permissions*, escolha **Read repository contents and packages permissions** (somente leitura, o padrão de repositório novo de conta pessoal). Cada job que precisa de mais declara no próprio arquivo.
2. **Cobrança sem surpresa.** Em Settings → Billing (da conta), **não cadastre forma de pagamento** para o projeto: sem ela, o Actions para ao fim da cota em vez de cobrar. Se a conta já tiver uma forma de pagamento por outro motivo, confira os orçamentos (*budgets*) antes de seguir (P28). Acompanhe ali o uso de minutos. **Não aumente** o limite do cache do Actions.
3. **Fluxo da `main` (sem proteção de branch).** No plano gratuito privado o GitHub não bloqueia push direto nem exige checks. A regra é de disciplina:
   - toda mudança vai por branch e pull request; **nunca** push direto na `main`;
   - só faça o merge com `api`, `front`, `e2e`, `docker` e `compose` verdes (ou pulados por ser só documentação) no PR;
   - nada de *force push* na `main`.
4. **Dependabot.** Settings → *Code security* (o nome da seção varia: *Security* ou *Advanced Security*): ligar **Dependency graph**, **Dependabot alerts** e **Dependabot security updates**. As atualizações de versão já vêm do `dependabot.yml`. Secret scanning e push protection **não** aparecem ou pedem plano pago em privado: não contrate.
5. **Pacotes no GHCR: acesso e retenção.** O pacote nasce **privado** na primeira publicação. Em Packages (perfil) abra `<repositório>/api` e `<repositório>/front` (`siga-vistas/...` até a renomeação do repositório; veja "Renomeação do Rastro") → Package settings: confira *Manage Actions access* (o repositório com permissão de escrita). Mantenha os pacotes privados (R27). **Retenção** (manual, sem custo): a cada release, apague as versões com tag que não serão mais usadas (por exemplo, guarde as três últimas) pelo menu da versão → *Delete version*; uma versão apagada pode ser restaurada em até 30 dias. **Não apague versões sem tag** soltas: cada imagem leva manifestos sem tag (plataforma, `provenance` e `sbom`) e apagar um que ainda faz parte de uma versão com tag quebra essa imagem (P29).
6. **Varredura de segredos local (substitui o secret scanning).** Antes de tornar o repositório ou um pacote público (R27), e de vez em quando, rode o [gitleaks](https://github.com/gitleaks/gitleaks) (código aberto) na sua máquina, sobre todo o histórico, com os valores mascarados. Com o binário da página de releases oficial: `gitleaks detect --source . --log-opts="--all" --redact`. Com Docker, fixe uma versão publicada da imagem oficial: `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:<versão> detect --source /repo --log-opts="--all" --redact`. Um achado é tratado fora do repositório (troca da credencial e limpeza do histórico); o relatório não é versionado. **Não entra na pipeline.**
7. **Criar uma versão e conferir as imagens.** Veja "Como publicar uma versão" acima. Depois: Actions → Release deve ficar verde (`validar-ref`, `ci`, `publicar (api)` e `publicar (front)`); o resumo de cada job lista as tags e o digest; em Packages devem aparecer `vX.Y.Z`, `X.Y` e `sha-<curto>`, e **nenhuma** `latest`.

**Alternativa registrada, sem recomendação:** tornar o repositório público torna gratuitos o code scanning, o secret scanning, a proteção de branch, os Environments e os minutos do runner padrão (4 vCPU). Depende antes da varredura do histórico (R27, passo 6) e de decisão do responsável.

## Renomeação do Rastro (R1, Fase 2)

O que a Fase 2 trocou e o que ficou de propósito está em D74 (back-end) e D75 (front e infraestrutura), em [09](09-decisoes.md).

### O que muda na máquina do responsável

- **Projeto Compose:** `name: rastro`. Containers passam a `rastro-api-1` e `rastro-front-1`, a rede a `rastro_interna` e as imagens construídas a `rastro-api` e `rastro-front`.
- **Volume:** **mantém o nome** `siga-vistas_dossies-tmp` (`name:` fixo em `compose.local.yaml`), então nada se perde. Ao subir, o Compose pode avisar que o volume "já existe e foi criado para o projeto siga-vistas": é esperado e inofensivo.
- **Primeira subida depois da troca:** se ainda houver containers do projeto antigo rodando, derrube-os antes, **sem `-v`**: `docker compose -p siga-vistas down`. Senão as portas 8080 e 8081 ficam ocupadas pelos antigos.
- **Imagens antigas** (`siga-vistas-api`, `siga-vistas-front`) ficam no cache do Docker sem uso; apague quando quiser com `docker image rm siga-vistas-api siga-vistas-front`.
- **Sessão e preferências do navegador:** `vistas.token` (sessionStorage) e `vistas.recentes.<login>` (localStorage) são lidas como reserva e migradas para `rastro.*` na primeira leitura; ninguém é deslogado nem perde os recentes. O fallback sai numa versão futura (risco em [10](10-pendencias-e-riscos.md)).

### Renomear o volume à mão (opcional)

O volume só guarda temporários do dossiê, do modal e das geometrias (todos expiram em minutos ou horas), então descartá-lo é seguro; mesmo assim a decisão é do responsável. Para manter os dados com o nome novo:

1. `docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml down` (sem `-v`).
2. `docker volume create rastro_dossies-tmp`
3. `docker run --rm -v siga-vistas_dossies-tmp:/de -v rastro_dossies-tmp:/para alpine sh -c "cp -a /de/. /para/"`
4. Em `compose.local.yaml`, troque `name: siga-vistas_dossies-tmp` por `name: rastro_dossies-tmp` (ou apague a linha `name:`).
5. Suba de novo e confira; só então, se quiser, `docker volume rm siga-vistas_dossies-tmp`.

### Lista manual do responsável (o Claude não executa nada disto)

1. Renomear o repositório no GitHub (Settings → General → *Repository name*) de `siga-vistas` para o nome novo. O GitHub redireciona o endereço antigo, mas atualize o remoto: `git remote set-url origin <novo endereço>`.
2. Renomear a pasta local `siga-vistas`. O Docker não depende do nome da pasta (o projeto Compose tem `name:` fixo).
3. README: o selo do CI e o `cd rastro` do tutorial já apontam para `jvscherbach/rastro` (R1, bloco 6). O selo só funciona depois do passo 1; até lá aparece quebrado. Se a pasta local ainda se chamar `siga-vistas` (passo 2), o `cd rastro` só vale depois de renomeá-la.
4. **CI:** os workflows não têm segredos nem variáveis próprios (só o `GITHUB_TOKEN` automático), então nada a renomear em Settings → Secrets and variables. Os nomes de workflow e de job (`CI`, `Release`, `api`, `front`, `e2e`, `docker`, `compose`) não tinham o nome antigo e não mudaram; só as tags locais das imagens do job `docker` passaram a `rastro-api:ci` e `rastro-front:ci`.
5. **GHCR:** o nome da imagem publicada vem do nome do repositório (`ghcr.io/<dono>/<repositório>/api`). Depois do passo 1, a próxima release cria pacotes novos; os antigos (`siga-vistas/api`, `siga-vistas/front`) continuam lá até você apagá-los (passo 5 da seção anterior).
6. `.env.local`: nenhuma variável mudou de nome; nada a fazer. Se ele tiver `LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/...`, continua certo (o caminho de montagem não mudou).
7. Renomear a skill `vistas-banco` é decisão sua (não foi renomeada aqui: o nome é citado no `CLAUDE.md` e nos prompts).

## Verificação sem sistemas externos

Toda subida de verificação usa `compose.yaml` + `compose.local.yaml` + **`compose.verificacao.yaml`**, que esvazia as variáveis externas, e confirma as variáveis vazias dentro do container (D34; [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md)).

### Dados fictícios de teste (recorte do SIGA e cenários do Camunda, S7 bloco 2)

O **único** mecanismo de dados do SIGA em teste é o recorte no Oracle 23 Free por Testcontainers: `SigaOracleTeste` sobe o container uma vez por execução do Maven e roda, como `system`, os scripts `api/src/test/resources/sql/siga/01-recorte.sql` a `08-financeiro-s9.sql` (DDL mínimo + dados fixos), e em seguida grava os cenários com o `CamundaFicticioBuilder` (`CenariosFicticiosDaS7`, posição atual; `CenariosFicticiosDaS8`, caminho percorrido, S8 bloco 1; `CenariosFicticiosDaS9`, lançamentos de taxa, S9 bloco 1). **Financeiro:** `processo.comLancamento(PAGA).comDar("FICT-0001").emitidoEm(..).vencendoEm(..).pagoEm(..)` grava `LANCAMENTO_TAXA` (só `FICT-` é aceito como DAR; `CHAVE_GED`, `MENSAGEM` e `ID_TITULACAO` ficam nulos) e os vencimentos são relativos ao `HOJE`/`RELOGIO` de `CenariosFicticiosDaS9`. O Compose de verificação **não** tem SIGA (R66, R69). Lista dos cenários: [docs/15](15-tramitacao.md#ambiente-de-verificação-camunda-e-licenciamento-fictícios-bloco-2).

**Como criar um cenário novo:**

1. Escolha um id livre (7000 a 7299 são da S7 e dos testes dela; **8000 a 8199 são dos cenários da S8**; testes da S8 que criem cenário próprio usam 8200 em diante; **9001 a 9199 são dos cenários financeiros da S9**; testes da S9 que criem cenário próprio usam 9200 em diante) e use número `<id>/2026`.
2. Se ele for de uso geral (outros testes vão consultá-lo), acrescente-o em `CenariosFicticiosDaS7.carregar` com uma constante e documente a linha na tabela de docs/15. Se for só de um teste, grave-o no próprio teste: `new CamundaFicticioBuilder(new NamedParameterJdbcTemplate(SigaOracleTeste.dataSourceAdministrador()), CatalogoDeEtapasDeTeste.padrao()).processo(Fluxo.HIDRICO, 7201)...gravar()` (feche o datasource).
3. Use só ids técnicos do catálogo (`rastro/etapas/*.yml`); o construtor recusa o resto. Coluna nova do SIGA: `ALTER TABLE` num script novo `0N-...sql` (nunca recriar tabela que outro script já criou) com o `GRANT SELECT` ao usuário de leitura, e acrescente o script na lista de `SigaOracleTeste`.
4. Dados pessoais: só CPFs `000000000NN` (inválidos de propósito) e nomes evidentes ("Servidor Exemplo NN", "Interessado Fictício S7"). `DadosFicticiosDaS7Test` varre as fixtures (sem Docker) e falha em CPF/CNPJ válido, sem imprimir o valor.
5. `CamundaFicticioBuilderTest` (S7) e `CamundaFicticioBuilderCaminhoTest` (S8) conferem o que foi gravado (tarefas, filhas, assinaturas, finais, sequência exata do caminho). Sem Docker, os testes de integração ficam pulados, mas `CamundaFicticioBuilderValidacaoTest` e `DadosFicticiosDaS7Test` (que também varre os arquivos da S8) rodam.

**Caminho percorrido (S8, bloco 1).** O mesmo `CamundaFicticioBuilder` grava o histórico: `passou(id, inicio, fim[, cpf])`, `passouPelaEspera`, `voltouPara` (recusa o que não é volta), `passouEmCiclo` / `voltouParaEmCiclo` (multi-instância, uma tarefa por CPF), `passouEmCicloEncerradoPor` (as demais tarefas ficam `deleted`), `passouPelaEmissao` / `passouPelaEmissaoComDevolucao` (callActivity e filhas encerradas, sobrepostas), `passouPeloSuporte`, `scriptRepetido`, `atividadeFicticia` (ruído sem id levantado), `expirou`, `variavel` (só a lista branca `VARIAVEIS_PERMITIDAS`; valor com 11 dígitos é recusado), `complementacaoConcluida`, e, para o plano B, `noHistoricoDoLicenciamento`, `comDatas` e `abertoEm`. É **alternativa** a `percorrendo(...)` (um cenário usa um ou outro); a etapa ativa ou o final vem depois, com `desde` = início da etapa ativa ou fim da instância. Tempos: `dias(n)` / `dias(n, horas)` a partir de `DESDE_PADRAO`. Os ids de atividade ganharam seis dígitos (`<instância>-a000123`), para o desempate de `START_TIME_` pelo id ser estável (bloco 2). Os valores das variáveis e a gravação da correção expirada são **hipóteses (P40)**.

## Regras que não mudam

- Nenhuma escrita no banco do SIGA.
- Nenhum valor real de credencial, URL institucional ou token no repositório.
- Prompts e automações não fazem commit nem push; só `git diff` para revisão.

### Testes de conversão com LibreOffice: onde rodam (S6, bloco 5)

- **Tag:** `conversao-libreoffice` (JUnit 5) nas cinco classes que exigem o `soffice`: `ArquivosHostisTest`, `ConversaoComSofficeTest`, `ConversaoDePlanilhasTest`, `NumeracaoComPlanilhaConvertidaTest` e `ProcessosDoConversorTest`. A propriedade `testes.excluidos` do `pom.xml` lista as tags excluídas (padrão `desempenho`, que já existia).
- **Localmente (tudo):** `./mvnw -B verify` roda os testes de conversão; sem o LibreOffice instalado, eles ficam pulados com aviso. Para um PR de código, o jeito de reproduzir o CI é `./mvnw -B verify -Dtestes.excluidos=desempenho,conversao-libreoffice`.
- **Só a conversão:** `./mvnw -B test -Dgroups=conversao-libreoffice` (com o LibreOffice instalado).
- **No Actions:** automático em push na `main`; manual em Actions → CI → *Run workflow* → marcar `incluir_conversao`. Um push em tag de release chama o CI com a ref da tag e **não** roda a conversão (a `main` já a rodou).
- **Risco:** uma regressão na conversão só aparece depois do merge, quando o push na `main` falha. **Mitigação:** quem mexe em `integration.libreoffice`, `documento.application` (conversão) ou na montagem do PDF roda `./mvnw -B verify` localmente, com o LibreOffice, **antes** do PR, ou dispara o CI manual na branch com `incluir_conversao`. Registrado em `docs/10` (R67).
