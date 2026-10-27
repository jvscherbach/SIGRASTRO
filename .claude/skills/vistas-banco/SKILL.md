---
name: vistas-banco
description: Contexto arquitetural e padrão de banco do SIGA Vistas (serviço de dossiê da Vista do Processo; Spring Boot 3/Java 21 em monólito modular, pacote raiz br.gov.mt.sema.vistas, front Angular, leitura somente do Oracle do SIGA e schema próprio Oracle via Flyway, desenvolvido em Oracle 23 Free compatível com 19c). Use sempre que o trabalho for no SIGA Vistas — criar módulo, endpoint, caso de uso, consulta ao banco do SIGA, adapter de integração, tarefa agendada, entidade, migration, DTO, contrato HTTP, teste ArchUnit, Compose, ou decidir em qual módulo (acesso, consulta, documento, dossie, auditoria, configuracao, integration.*) algo entra. Não substitui o padrão genérico de DBA (dba-oracle-padrao): este documento registra as decisões específicas do SIGA Vistas e prevalece sobre o genérico quando divergirem.
---

# Contexto arquitetural do SIGA Vistas

SIGA Vistas é um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os documentos e gera o dossiê em PDF em ordem cronológica. A documentação funcional completa está em `docs/` (requisitos, regras de negócio, telas, API e decisões). Este arquivo resume as **regras técnicas** que o código e o banco devem seguir.

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
| Agendamento | Spring `@Scheduled` em processo (ver `references/agendamento.md`) |
| PDF | Apache PDFBox 3 |
| Conversão | LibreOffice headless via JODConverter |
| HTTP de saída | `RestClient` |
| Front-end | Angular, servido por nginx |
| Execução | Docker Compose; pools de conexão pequenos na fase inicial; homologação/produção ainda não definidos (alvo provável: Kubernetes do SIGA) |

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

## Referências — carregue quando o pedido tocar no assunto

- `references/estrutura-de-pastas.md` — árvore do repositório, do back-end e do front-end na fase atual, e o que só nasce na versão definitiva.
- `references/modulos-e-dependencias.md` — regras de dependência entre camadas e módulos, contratos internos válidos.
- `references/integracao-externa.md` — fronteiras de `integration.siga`, `integration.licenciamento`, `integration.scorp`, `integration.arrecadacao`.
- `references/persistencia-e-migrations.md` — dois datasources, leitura do SIGA sem JPA, schema próprio com Flyway, adapters em memória no protótipo.
- `references/http-erros-openapi.md` — `ProblemDetail` com `errorCode`, correlação, `PageResponse<T>`, OpenAPI por ambiente.
- `references/ambientes-e-docker.md` — ambientes, Compose, variáveis, o que não está decidido.
- `references/agendamento.md` — tarefas agendadas: limpeza de dossiês expirados agora; expurgo e fila durável depois.
- `references/checklist-verificacao.md` — testes e comandos antes de considerar pronto.

### Padrão de banco (schema próprio e leitura do SIGA)

- `references/nomenclatura.md` — objetos em português e MAIÚSCULO; arquivo de migration em inglês.
- `references/tipos-dados.md` — tipos por natureza do dado, com exemplos do Vistas.
- `references/normalizacao.md` — 3FN e o caso do "retrato" gravado na auditoria.
- `references/indexacao.md` — índices de FK e dos filtros reais de auditoria e fila.
- `references/documentacao.md` — `COMMENT ON`, dicionário de dados em HTML e MER em `docs/database/` (obrigatórios a cada migration), colunas do SIGA consumidas.
- `references/mer.md` — MER abstrato e mapeado, referências lógicas ao SIGA.
- `references/seguranca-privilegios.md` — usuário de leitura do SIGA, owner e usuário de aplicação do schema próprio.
- `references/connection-pooling.md` — dois pools HikariCP e a relação com `DOSSIE_THREADS`.

## O que NÃO está decidido (sinalize, não invente)

- Instância Oracle do schema próprio em homologação/produção.
- Infraestrutura de homologação/produção (provável Kubernetes do SIGA), registry, secret manager, hostnames.
- Autenticação definitiva (Portal do SIGA/identidade institucional; gov.br se houver público externo).
- Credencial de serviço para arquivos (API key do serviço de identidade ou acesso direto ao Pandora).
- Versão real do Oracle do SIGA em homologação/produção (assumir compatibilidade com 19c até confirmar).
- Se o Flyway roda no startup da API ou numa etapa do pipeline.

## Regras que não mudam

- Nenhuma escrita no banco do SIGA.
- Nenhum valor real de credencial, URL institucional ou token no repositório.
- Prompts e automações não fazem commit nem push; só `git diff` para revisão.
- **Verificação sem sistemas externos (D34):** todo `docker compose up` usa `-f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml`; nunca abrir nem imprimir o `.env.local`; confirmar as variáveis externas vazias dentro do container, sem imprimir valores. Detalhes em `references/ambientes-e-docker.md`.
- Nenhum CPF, CNPJ ou nome de pessoa real em documentação, testes, prompts, exemplos ou logs (use dados fictícios e marcadores como `<CPF_DE_TESTE>`).
- A chave do arquivo, o token e a URL da API de arquivos nunca saem do domínio e dos adapters (nem DTO, nem log, nem resposta).
