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

## Integração contínua

A pipeline roda no GitHub Actions (`.github/`). **Ela só valida e publica imagens: não há deploy** (D51). Nenhuma credencial do SIGA, da API de arquivos ou do SCORP entra nela, nem como secret; o único secret usado é o `GITHUB_TOKEN`, no login do GHCR. As variáveis que ativam os testes de fumaça (`SIGA_SMOKE`, `ARQUIVOS_SMOKE`) não são definidas, então esses testes ficam pulados.

| Arquivo | Quando roda | O que faz |
|---|---|---|
| `workflows/ci.yml` | Pull request, push na `main` e chamado pelo `release.yml` | `mudancas` decide se a mudança é só de documentação (`docs/`, `*.md`, `.claude/`); nesse caso `api`, `front`, `e2e` e `docker` ficam pulados (D53). Cinco jobs de verificação: `api` (`./mvnw -B clean verify`, com Testcontainers no Docker do runner), `front` (build, testes e `npm audit`), `e2e` (Playwright com API simulada, depois do `front`), `docker` (constrói as duas imagens, sem push e sem login) e `compose` (`config --services`, sem subir nada). Os relatórios de teste só são guardados em caso de falha, por 7 dias |
| `workflows/release.yml` | Tag `vX.Y.Z` ou execução manual sobre uma tag | Confere a ref, roda o `ci.yml` e publica `api` e `front` no GHCR (`ghcr.io/<dono>/<repositório>/api` e `/front`) com as tags `vX.Y.Z`, `X.Y` e `sha-<curto>`. **Nunca `latest`.** Build sem cache, com `provenance` e `sbom`. Não publica a cada merge |
| `dependabot.yml` | Mensal (D53) | Atualizações de `maven`, `npm`, `github-actions` e `docker`, agrupando minor e patch (máx. 5 PRs por ecossistema) |

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

**Consumo estimado** (estimativa, **não medida**; confira na primeira semana em Settings → Billing): `api` 10 a 15 min (2 vCPU, Oracle por Testcontainers), `front` 3 a 5, `e2e` 4 a 6, `docker` 5 a 10, `compose` e `mudancas` 1 cada (o GitHub arredonda cada job para cima, em minutos). Uma execução completa fica entre **25 e 38 min**; um PR com dois pushes e o push na `main` gasta perto de 100 min, e só documentação fica em ~2 min. A cota de 2.000 min cobre algo como 15 a 20 PRs de código por mês, contando os do Dependabot. Uma release roda o CI de novo e os dois `publicar` sem cache (mais 20 a 30 min).

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
5. **Pacotes no GHCR: acesso e retenção.** O pacote nasce **privado** na primeira publicação. Em Packages (perfil) abra `siga-vistas/api` e `siga-vistas/front` → Package settings: confira *Manage Actions access* (o repositório com permissão de escrita). Mantenha os pacotes privados (R27). **Retenção** (manual, sem custo): a cada release, apague as versões com tag que não serão mais usadas (por exemplo, guarde as três últimas) pelo menu da versão → *Delete version*; uma versão apagada pode ser restaurada em até 30 dias. **Não apague versões sem tag** soltas: cada imagem leva manifestos sem tag (plataforma, `provenance` e `sbom`) e apagar um que ainda faz parte de uma versão com tag quebra essa imagem (P29).
6. **Varredura de segredos local (substitui o secret scanning).** Antes de tornar o repositório ou um pacote público (R27), e de vez em quando, rode o [gitleaks](https://github.com/gitleaks/gitleaks) (código aberto) na sua máquina, sobre todo o histórico, com os valores mascarados. Com o binário da página de releases oficial: `gitleaks detect --source . --log-opts="--all" --redact`. Com Docker, fixe uma versão publicada da imagem oficial: `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:<versão> detect --source /repo --log-opts="--all" --redact`. Um achado é tratado fora do repositório (troca da credencial e limpeza do histórico); o relatório não é versionado. **Não entra na pipeline.**
7. **Criar uma versão e conferir as imagens.** Veja "Como publicar uma versão" acima. Depois: Actions → Release deve ficar verde (`validar-ref`, `ci`, `publicar (api)` e `publicar (front)`); o resumo de cada job lista as tags e o digest; em Packages devem aparecer `vX.Y.Z`, `X.Y` e `sha-<curto>`, e **nenhuma** `latest`.

**Alternativa registrada, sem recomendação:** tornar o repositório público torna gratuitos o code scanning, o secret scanning, a proteção de branch, os Environments e os minutos do runner padrão (4 vCPU). Depende antes da varredura do histórico (R27, passo 6) e de decisão do responsável.

## Verificação sem sistemas externos

Toda subida de verificação usa `compose.yaml` + `compose.local.yaml` + **`compose.verificacao.yaml`**, que esvazia as variáveis externas, e confirma as variáveis vazias dentro do container (D34; [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md)).

## Regras que não mudam

- Nenhuma escrita no banco do SIGA.
- Nenhum valor real de credencial, URL institucional ou token no repositório.
- Prompts e automações não fazem commit nem push; só `git diff` para revisão.
