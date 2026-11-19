# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Segurança da verificação (leia primeiro)

Na S1.1 a `api` foi subida com as variáveis `SIGA_DB_*` do `.env.local` preenchidas e leu dados reais (nomes e CPFs): a checagem não viu linhas com espaço no início. **Isso não pode se repetir** (D34).

1. **Nunca abra, imprima ou copie o conteúdo do `.env.local`** (nada de `cat`, `Get-Content`, `type`, leitura por ferramenta). Para saber se uma variável está preenchida, use só testes que não mostram o valor e que tolerem espaço no início da linha.
2. **Todo `docker compose up` usa os três arquivos:** `-f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml`. O terceiro esvazia, no serviço `api`, as variáveis que levam a sistemas externos (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_API_URL`, `VISTAS_DB_*`); `environment` tem precedência sobre `env_file`. Nunca suba sem o terceiro.
3. **Antes de qualquer chamada à API**, confirme **dentro do container**, sem imprimir valores, que cada uma está vazia (ex.: `docker compose ... exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA'`). Se alguma vier `PREENCHIDA`: pare, derrube os containers (sem `-v`), não faça chamada alguma e relate.
4. Se alguma resposta trouxer dado real (nome, CPF/CNPJ, número de processo que não seja de teste): pare, não copie o dado para arquivo, mensagem ou log e relate.
5. Você não se conecta ao SIGA, à API de arquivos nem ao SCORP reais. Testes usam portas falsas, Oracle 23 Free por Testcontainers e um servidor HTTP local de teste. Token real nunca entra em teste, exemplo, log ou documentação.

## Dados pessoais

**Nenhum CPF, CNPJ ou nome de pessoa real** em documentação, testes, prompts, exemplos ou logs. Use dados fictícios evidentes (`12.345.678/0001-90`, "Associação Residencial Vale Verde") e, nos roteiros, marcadores como `<CPF_DE_TESTE>` e `<CNPJ_DE_TESTE>`. Nos roteiros, totais fixos de uma base real são trocados por "compare com o `SELECT COUNT(*)` na mesma base".

## Estado do repositório

**Rastro** (antes "SIGA Vistas"; D73) é um complemento de consulta ao SIGA Licenciamento. Seu primeiro módulo, o **Vistas**, gera o **dossiê em PDF** de um requerimento/processo, com os documentos selecionados em ordem cronológica. Na Fase 2 da R1 o pacote Java passou a `br.gov.mt.sema.rastro` e as propriedades a `rastro.*` (o prefixo `vistas.*` vale como alias, D74); variáveis de ambiente `VISTAS_*`, a skill `vistas-banco` e alguns nomes de infraestrutura continuam com `vistas`. Está na fase de **protótipo de validação**: o objetivo é provar que os problemas da "Vista do Processo" atual têm solução (processos que não geram vista e planilhas fora do PDF).

O repositório tem a documentação (`docs/`), a skill `vistas-banco` (`.claude/skills/`) e o código: `api/` (Spring Boot, Java 21) e `front/` (Angular 21), que sobem no Docker Compose. Estão concluídas a **S0 (fundação)**, a **S1 (consulta)**, a **S1.1 (objeto, prioridade e responsável pelo mapeamento P01; número de processo 1:N)** — S1 e S1.1 **homologadas com dados reais** pelo responsável, em base de desenvolvimento — e a **S2 (documentos e dossiê em PDF, com as correções da S1.1)**, corrigida pela **S2.1 (documentos reproduzem a Vista do SIGA)**: lista de documentos do requerimento (6 origens da Vista do SIGA, uma consulta SQL por origem), RN07 e sigilo já aplicados, geração assíncrona do dossiê só com PDFs (PDFBox 3 em disco), gaveta de geração, pré-visualização e download por Blob. A **S2.2** (blocos 1 a 10, `docs/14`) trouxe a seleção total, a ordem cronológica garantida pelo servidor, o download robusto e o token por arquivo, as falhas coletadas com continuação (`PENDENTE_DECISAO`, RN09 reescrita), a seção "Documentos não incluídos" e o download de dossiê grande por ticket. A **S3** (blocos 1 a 10, `docs/progresso/s3.md`) converte planilhas, textos e imagens para PDF dentro do dossiê (LibreOffice isolado em `integration.libreoffice`, imagens por PDFBox em `integration.imagem`, chave `CONVERSAO_HABILITADA`), identifica o convertido no sumário, nos marcadores e na capa e traz o modal **Visualizar** (`GET /documentos/{origem}/{id}/conteudo`); a homologação da S3 está em `docs/homologacao/S3-roteiro-homologacao.md`. A **S4** (blocos 1 a 10, `docs/progresso/s4.md`) trouxe a política de acesso única no back-end (`acesso.application.PoliticaDeAcesso`, D60) provada pelo `MatrizDeAcessoTest`, o menu e as rotas pelas `permissoes` (RN08), as geometrias em lista própria com ZIP tudo ou nada, os documentos pessoais do SCORP (origem "Pessoal", falha tolerante, `SCORP_SIGILO_PADRAO`) e a tela "Dossiês gerados"; a homologação da S4 está em `docs/homologacao/S4-roteiro-homologacao.md`. A **S5** (blocos 1 a 12, `docs/progresso/s5.md`) trouxe a geração em segundo plano (`AcompanhamentoDeDossieService`: a gaveta só minimiza, indicador na barra do topo, D71), o módulo `auditoria` em memória e somente inclusão (`RegistrarEvento` que nunca lança, retrato limpo, consulta por perfil com sigiloso mascarado, CSV com proteção de fórmula, `LOGIN`/`PERFIL` por evento de aplicação) e o módulo `configuracao` (usuários e perfis com proteções, parâmetros em tempo de execução com classe de aplicação e evento `CONFIG` único, conexões com teste); tudo **em memória** (some ao reiniciar, R50, R57) e a homologação da S5 está em `docs/homologacao/S5-roteiro-homologacao.md`. A S2 foi testada **sem nenhum sistema externo real**; a **homologação com arquivos reais é do responsável** (`docs/homologacao/S2-roteiro-homologacao.md`, `docs/casos-de-teste.md`). O plano e o estado de cada sprint ficam em `docs/14-plano-de-sprints.md`; cada sprint é implementada a partir de um prompt `.md` (`docs/prompts/`). **Atualize `docs/` durante a sprint, não só no fim.**

**Carregue a skill `vistas-banco` antes de qualquer trabalho de código ou banco.** Ela contém os padrões detalhados.

## Comandos

```bash
./mvnw -Dtest=ArchitectureTest test            # ArchUnit; rodar antes de terminar qualquer tarefa (não precisa de Docker)
./mvnw -Dtest=NomeDaClasseTest test            # teste único
./mvnw clean verify
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
npm --prefix front run e2e                     # Playwright com API simulada (só desenvolvimento); 1ª vez: npx playwright install chromium
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml config --services
git diff --check
node --test .claude/hooks/guarda-bash.test.mjs .claude/hooks/guarda-arquivos.test.mjs .claude/hooks/varrer-dados.test.mjs .claude/hooks/vazamento.test.mjs
```

- Nunca rode `docker compose down -v`.
- Só `git diff` entre os comandos git (nada de `commit`, `push`, `branch`, `stash`).
- Nunca se conecte ao banco real do SIGA: a conexão e a leitura reais são homologadas pelo responsável (D27). Os testes de integração usam Oracle 23 Free via Testcontainers (pulados sem Docker).
- Não faça commit nem push. A revisão é feita por `git diff`, e o commit fica com o responsável pelo projeto.

## Arquitetura

- **Back-end:** Java 21, Spring Boot 3, Maven Wrapper. É um monólito modular por domínio sob `br.gov.mt.sema.rastro` (antes `br.gov.mt.sema.vistas`), fiscalizado por ArchUnit.
- **Front-end:** Angular servido por nginx, que encaminha `/api` para a `api`. A referência visual é `docs/prototipo/siga-vistas-prototipo.html`.
- **Execução:** Docker Compose com os serviços `api` (JDK 21 + LibreOffice) e `front`. O banco do SIGA **nunca** entra no Compose.
- **PDF e conversão:** Apache PDFBox 3; LibreOffice headless via JODConverter; `RestClient` para HTTP de saída.

### Módulos e donos

`acesso`, `consulta`, `documento`, `dossie`, `auditoria` e `configuracao` são os módulos de negócio. `integration.{siga,licenciamento,scorp,arrecadacao}` são adapters externos. `config` e `shared` são transversais (`shared` não é depósito de código sem dono). O dono de uma funcionalidade é o módulo que define o significado de negócio dela; a tabela completa está em `docs/13-padroes-tecnicos.md`.

Camadas por módulo: `api` (controller + `api.dto`) → `application` (casos de uso + portas) → `domain` (sem Spring Web, JPA, JDBC ou tipos de `integration.*`); `infrastructure` implementa as portas. Crie só os pacotes que o fluxo usa, sem placeholders vazios. `consulta` não tem `infrastructure/` e `documento` só tem `infrastructure/agendamento/` (limpeza do cache do modal, S3, e das pastas do ZIP das geometrias, S4) e `infrastructure/disco/` (espaço livre, S4): as portas deles são implementadas por `integration.*`.

Entre módulos, a comunicação acontece só por caso de uso ou porta publicada (ex.: `auditoria` publica `RegistrarEvento`). Nunca use DTO HTTP, entidade, repository ou DTO de integração de outro módulo.

### Regras duras

- **O banco do SIGA (`SIGA2_LICENCIAMENTO`, `SIGA2_CAMUNDA`, `SIGA2_OUTORGA`, `SIGA2_ARRECADACAO`) é somente leitura.** Não grave nada, não crie objetos e não rode migrations nele. A leitura é feita por `NamedParameterJdbcTemplate` (sem JPA) com datasource `read-only`.
- **Só `integration.siga` escreve SQL contra o SIGA** (consultas em `resources/sql/siga/`). Os módulos de negócio declaram portas e recebem objetos internos.
- **Protótipo sem schema próprio:** usuários, auditoria, configurações e fila ficam em adapters `infrastructure/memoria/`. Não crie entidades, migrations ou tabelas "de preparação" antes da versão definitiva.
- **Versão definitiva:** schema próprio em Oracle (23 Free no desenvolvimento) com **compatibilidade obrigatória com 19c**. Usa JPA + Flyway com `ddl-auto=validate`. Nomes de objetos ficam em português e MAIÚSCULO; o arquivo de migration é `V<n>__<ingles_snake_case>.sql`. Não use recursos exclusivos do 23c, como `BOOLEAN` em tabela ou `IF NOT EXISTS`.
- **Erros HTTP:** `ProblemDetail` com `errorCode`, ID de correlação e `PageResponse<T>` (veja `docs/padroes/http-erros-openapi.md`).
- **Geração do dossiê:** assíncrona, com fila em memória, pool `DOSSIE_THREADS`, diretório temporário por pedido e limpeza por `@Scheduled`. Segue a regra **"nada incompleto sem decisão" (RN09, S2.2)**: as falhas são coletadas, o pedido fica em `PENDENTE_DECISAO` sem entregar nada e a continuação (tentar de novo ou remover) não baixa de novo o que já foi obtido. A conexão do SIGA é liberada antes do primeiro download. PDFBox lê os arquivos em disco (nunca tudo em memória) e **não força A4**.
- **Chave do arquivo, token e URL da API de arquivos** nunca saem de `documento.domain`, do `dossie` e dos adapters: nem DTO, nem log, nem resposta (RNF09; `ArchitectureTest` e testes de resposta fiscalizam). HTTP de saída só em `integration.*`.
- **Documentos:** `id` em texto; só PDF e vigente entra na pré-seleção; compactados (RN11) fora da lista e geometrias em lista própria, com ZIP tudo ou nada (S4); sigilo tolerante e, na dúvida, sigiloso (RN06).
- Não coloque credenciais, tokens ou URLs institucionais reais no repositório. `.env.local` não é versionado; o `.example` só tem os nomes das variáveis.
- Itens que dependem de verificação estão marcados como **NÃO CONFIRMADO** nos docs. Em "O que NÃO está decidido" (doc 13), sinalize esses pontos em vez de inventar.

### Regras de negócio mais citadas (`docs/02-requisitos.md`)

- **RN01, vigência:** vigente = `DATA_FIM IS NULL` e chave ≠ `"0"`; substituído = `DATA_FIM` preenchida; indisponível = chave `"0"` ou arquivo inexistente.
- **RN03, ordem cronológica:** data da própria origem (a assinatura deixou de ser usada na S2.1). Documentos sem data vão para o final.
- **RN05, duplicados:** chaves repetidas viram um único item.
- **RN06, sigilo:** considera todas as variações de coluna; na dúvida, o documento é sigiloso.
- **RN07, perfil Operador:** o Operador só acessa processos em que é o interessado. Gestor e Administrador acessam tudo.
- **RN02, RN10, RN11, exclusões:** doença grave nunca é listada; geometrias (SHP/KML/KMZ) ficam fora do dossiê; arquivos compactados são ignorados.

## Ferramentas do Claude Code

O repositório versiona guardas do próprio Claude Code (`docs/claude-code/`). **Hooks** em `.claude/settings.json` e `.claude/hooks/` (Node, sem dependências) bloqueiam automaticamente o que as regras acima proíbem: `git` além de `git diff` e qualquer `gh`; `docker compose down -v`, `docker volume rm/prune`, `docker system prune --volumes`; abrir, procurar ou citar `.env`, `.env.local`, `.env.*` (menos `*.example`) e `mapa-local.csv`; despejar variáveis de ambiente (`printenv`, `env`, `docker ... exec env`, `docker inspect`, `docker compose config` com valores). Depois de gravar um arquivo, avisam se ele tem CPF/CNPJ válido, token ou chave privada, sem repetir o valor. Postura "na dúvida, bloqueia" e **defesa em camadas** (hooks, `permissions.deny`, segredo fora da pasta do projeto; o isolamento do sistema só existe no WSL2): o que cada camada cobre, os limites e o passo a passo manual do segredo estão em `docs/claude-code/segredos.md`, e a prova é um corpus de 546 tentativas com isca (`vazamento.test.mjs`, no CI). Erro interno de uma guarda não trava a sessão. Recebeu um bloqueio: siga a orientação da mensagem, não tente contornar. Testes: `node --test .claude/hooks/guarda-bash.test.mjs .claude/hooks/guarda-arquivos.test.mjs .claude/hooks/varrer-dados.test.mjs .claude/hooks/vazamento.test.mjs`. `.claude/settings.local.json` é pessoal e fica fora do Git. **Servidor MCP `rastro`** (`.mcp.json`, `tools/mcp-rastro/`, `docs/claude-code/mcp.md`): consultas somente leitura (`buscar_requerimento`, `listar_documentos`, `estado_do_servico`) ao Rastro **local** e **só em modo de demonstração** (`GET /api/estado` com `modo=demonstracao`, que exige `RASTRO_MODO=demonstracao` e nenhum sistema externo), com a saída mascarada; credencial de demonstração por `RASTRO_MCP_USUARIO`/`RASTRO_MCP_SENHA` do ambiente do processo. Testes: `npm --prefix tools/mcp-rastro test`. **Subagentes e comando** (`.claude/agents/`, `.claude/commands/verificar-sprint.md`, `docs/claude-code/agentes.md`): `revisor-seguranca` (somente leitura), `executor-de-testes` e `atualizador-de-docs` (só edita `docs/`); `/verificar-sprint <N>` dispara os três em paralelo e grava `docs/progresso/verificacao-s<N>.md`, sem corrigir código nem commitar. Novos agentes e comandos só carregam em sessão nova.

## Documentação

- `docs/` está numerado de 01 a 14 (índice em `docs/README.md`).
- **`docs/padroes/` e `.claude/skills/vistas-banco/references/` têm o mesmo conteúdo.** Ao alterar um, altere o outro (o `README.md` só existe em `docs/padroes/`).

## Projeto sem orçamento

O Rastro é um projeto pessoal, em fase de protótipo, sem orçamento e sem vínculo institucional por enquanto. **Nada pago pode entrar no projeto**: nem recurso do GitHub que exija plano pago, nem serviço, nem licença, nem ferramenta com cota paga. O repositório pode ser privado no plano gratuito do GitHub: trabalhe com essa hipótese pior. Antes de propor ou usar qualquer recurso, confirme que ele é gratuito para repositório privado no plano gratuito e, se não tiver certeza, **não use** e registre a dúvida em `docs/10`.
