# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Estado do repositório

SIGA Vistas (nome provisório) é um serviço externo ao SIGA Licenciamento que gera o **dossiê em PDF** de um requerimento/processo, com os documentos selecionados em ordem cronológica. Está na fase de **protótipo de validação**: o objetivo é provar que os problemas da "Vista do Processo" atual têm solução (processos que não geram vista e planilhas fora do PDF).

O repositório tem a documentação (`docs/`), a skill `vistas-banco` (`.claude/skills/`) e o código: `api/` (Spring Boot, Java 21) e `front/` (Angular 21), que sobem no Docker Compose. A **S0 (fundação)**, a **S1 (consulta, com login simulado dos três perfis e leitura do SIGA)**, já homologada com dados reais, e a **S1.1 (objeto, prioridade e tipo do requerimento pelo mapeamento P01, nome do responsável só para Gestor e Administrador, busca direta por CPF/CNPJ e número de processo 1:N)** estão concluídas; a validação da S1.1 com dados reais está pendente de homologação manual (`docs/homologacao/S1.1-roteiro-homologacao.md`). O plano e o estado de cada sprint ficam em `docs/14-plano-de-sprints.md`; cada sprint é implementada a partir de um prompt `.md` (`docs/prompts/`). **Atualize `docs/` durante a sprint, não só no fim.**

**Carregue a skill `vistas-banco` antes de qualquer trabalho de código ou banco.** Ela contém os padrões detalhados.

## Comandos

```bash
./mvnw -Dtest=ArchitectureTest test            # ArchUnit; rodar antes de terminar qualquer tarefa (não precisa de Docker)
./mvnw -Dtest=NomeDaClasseTest test            # teste único
./mvnw clean verify
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml config
git diff --check
```

- Nunca rode `docker compose down -v`.
- Nunca se conecte ao banco real do SIGA: a conexão e a leitura reais são homologadas pelo responsável (D27). Os testes de integração usam Oracle 23 Free via Testcontainers (pulados sem Docker).
- Não faça commit nem push. A revisão é feita por `git diff`, e o commit fica com o responsável pelo projeto.

## Arquitetura

- **Back-end:** Java 21, Spring Boot 3, Maven Wrapper. É um monólito modular por domínio sob `br.gov.mt.sema.vistas`, fiscalizado por ArchUnit.
- **Front-end:** Angular servido por nginx, que encaminha `/api` para a `api`. A referência visual é `docs/prototipo/siga-vistas-prototipo.html`.
- **Execução:** Docker Compose com os serviços `api` (JDK 21 + LibreOffice) e `front`. O banco do SIGA **nunca** entra no Compose.
- **PDF e conversão:** Apache PDFBox 3; LibreOffice headless via JODConverter; `RestClient` para HTTP de saída.

### Módulos e donos

`acesso`, `consulta`, `documento`, `dossie`, `auditoria` e `configuracao` são os módulos de negócio. `integration.{siga,licenciamento,scorp,arrecadacao}` são adapters externos. `config` e `shared` são transversais (`shared` não é depósito de código sem dono). O dono de uma funcionalidade é o módulo que define o significado de negócio dela; a tabela completa está em `docs/13-padroes-tecnicos.md`.

Camadas por módulo: `api` (controller + `api.dto`) → `application` (casos de uso + portas) → `domain` (sem Spring Web, JPA, JDBC ou tipos de `integration.*`); `infrastructure` implementa as portas. Crie só os pacotes que o fluxo usa, sem placeholders vazios. `consulta` e `documento` não têm `infrastructure/`, porque as portas deles são implementadas por `integration.*`.

Entre módulos, a comunicação acontece só por caso de uso ou porta publicada (ex.: `auditoria` publica `RegistrarEvento`). Nunca use DTO HTTP, entidade, repository ou DTO de integração de outro módulo.

### Regras duras

- **O banco do SIGA (`SIGA2_LICENCIAMENTO`, `SIGA2_CAMUNDA`, `SIGA2_OUTORGA`, `SIGA2_ARRECADACAO`) é somente leitura.** Não grave nada, não crie objetos e não rode migrations nele. A leitura é feita por `NamedParameterJdbcTemplate` (sem JPA) com datasource `read-only`.
- **Só `integration.siga` escreve SQL contra o SIGA** (consultas em `resources/sql/siga/`). Os módulos de negócio declaram portas e recebem objetos internos.
- **Protótipo sem schema próprio:** usuários, auditoria, configurações e fila ficam em adapters `infrastructure/memoria/`. Não crie entidades, migrations ou tabelas "de preparação" antes da versão definitiva.
- **Versão definitiva:** schema próprio em Oracle (23 Free no desenvolvimento) com **compatibilidade obrigatória com 19c**. Usa JPA + Flyway com `ddl-auto=validate`. Nomes de objetos ficam em português e MAIÚSCULO; o arquivo de migration é `V<n>__<ingles_snake_case>.sql`. Não use recursos exclusivos do 23c, como `BOOLEAN` em tabela ou `IF NOT EXISTS`.
- **Erros HTTP:** `ProblemDetail` com `errorCode`, ID de correlação e `PageResponse<T>` (veja `docs/padroes/http-erros-openapi.md`).
- **Geração do dossiê:** assíncrona, com fila em memória, pool `DOSSIE_THREADS`, diretório temporário por pedido e limpeza por `@Scheduled`. Segue a regra **"completo ou nenhum" (RN09)**: se qualquer documento falhar, nenhum dossiê é entregue.
- Não coloque credenciais, tokens ou URLs institucionais reais no repositório. `.env.local` não é versionado; o `.example` só tem os nomes das variáveis.
- Itens que dependem de verificação estão marcados como **NÃO CONFIRMADO** nos docs. Em "O que NÃO está decidido" (doc 13), sinalize esses pontos em vez de inventar.

### Regras de negócio mais citadas (`docs/02-requisitos.md`)

- **RN01, vigência:** vigente = `DATA_FIM IS NULL` e chave ≠ `"0"`; substituído = `DATA_FIM` preenchida; indisponível = chave `"0"` ou arquivo inexistente.
- **RN03, ordem cronológica:** data de assinatura > data na tabela de origem > data de cadastro no repositório de arquivos. Documentos sem data vão para o final.
- **RN05, duplicados:** chaves repetidas viram um único item.
- **RN06, sigilo:** considera todas as variações de coluna; na dúvida, o documento é sigiloso.
- **RN07, perfil Operador:** o Operador só acessa processos em que é o interessado. Gestor e Administrador acessam tudo.
- **RN02, RN10, RN11, exclusões:** doença grave nunca é listada; geometrias (SHP/KML/KMZ) ficam fora do dossiê; arquivos compactados são ignorados.

## Documentação

- `docs/` está numerado de 01 a 14 (índice em `docs/README.md`).
- **`docs/padroes/` e `.claude/skills/vistas-banco/references/` têm o mesmo conteúdo.** Ao alterar um, altere o outro (o `README.md` só existe em `docs/padroes/`).
