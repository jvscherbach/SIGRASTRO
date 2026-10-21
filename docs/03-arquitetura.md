# 03 — Arquitetura

## Visão geral

```
┌─────────────┐      ┌──────────────────────────────┐      ┌──────────────────────────┐
│  Front-end  │ HTTP │          Back-end            │ JDBC │ Oracle (somente leitura) │
│  Angular    │─────▶│  Spring Boot                 │─────▶│ SIGA2_LICENCIAMENTO      │
│  (nginx)    │      │                              │      │ SIGA2_CAMUNDA            │
└─────────────┘      │  - consulta                  │      │ SIGA2_OUTORGA            │
                     │  - documentos                │      └──────────────────────────┘
                     │  - fila de geração (memória) │ HTTPS ┌─────────────────────────┐
                     │  - montagem do PDF (PDFBox)  │──────▶│ API do Licenciamento    │
                     │  - conversão (LibreOffice)   │       │ /api/arquivo/{chave}    │
                     │                              │──────▶│ SCORP (documentos       │
                     │  disco temporário            │       │   pessoais)             │
                     └──────────────────────────────┘       └─────────────────────────┘
```

O front-end conversa apenas com o back-end. O back-end concentra o acesso ao banco, às APIs externas e o token de acesso.

## Stack

| Camada | Tecnologia |
|---|---|
| Front-end | Angular 21 (standalone, sem zone.js; Node 22 LTS, mínimo 22.12 — a imagem usa 22.23.3), seguindo o protótipo navegável (sem starter) |
| Back-end | Java 21, Spring Boot 3 |
| Leitura do SIGA | `NamedParameterJdbcTemplate` (JDBC), driver Oracle `ojdbc11`, datasource somente leitura |
| Schema próprio (definitivo) | Oracle (23 Free no desenvolvimento, compatível com 19c, ambiente final); Spring Data JPA + Flyway, `ddl-auto=validate` |
| Agendamento | Spring `@Scheduled` em processo (limpeza de dossiês expirados) |
| Arquitetura de código | Monólito modular por domínio, pacote raiz `br.gov.mt.sema.vistas`, fiscalizado por ArchUnit |
| Montagem de PDF | Apache PDFBox 3 |
| Conversão de formatos | LibreOffice headless, via JODConverter |
| Cliente HTTP | `RestClient` (Spring) |
| Execução | Docker Compose |

## Módulos do back-end

Monólito modular por domínio. Regras completas em [13 — Padrões técnicos](13-padroes-tecnicos.md).

| Pacote | Responsabilidade |
|---|---|
| `acesso` | Usuários, perfis, vínculo do interessado, login (simulado no protótipo) |
| `consulta` | Busca de requerimento/processo, dados de exibição, situação e tarefa atual |
| `documento` | Agregação dos documentos, vigência, sigilo, deduplicação, geometrias, conteúdo para visualização |
| `dossie` | Fila de geração, conversão, montagem do PDF, capa, sumário, expiração, regra "completo ou nenhum" |
| `auditoria` | Registro e consulta de eventos |
| `configuracao` | Parâmetros administráveis |
| `integration.siga` | Única camada com SQL e conhecimento do modelo do banco do SIGA |
| `integration.licenciamento` | Cliente da API de arquivos |
| `integration.scorp` | Cliente do SCORP (documentos pessoais) |
| `integration.arrecadacao` | Taxas (desativado) |

`config` e `shared` são transversais (configuração técnica, erros, correlação, paginação).

A árvore de pastas do repositório, do back-end e do front-end está em [`padroes/estrutura-de-pastas.md`](padroes/estrutura-de-pastas.md).

## Geração assíncrona

- Fila em memória com um pool de threads de tamanho configurável.
- Cada pedido recebe um identificador. O front-end consulta o andamento periodicamente.
- Os arquivos de trabalho ficam em diretório temporário por pedido. Uma tarefa agendada (`@Scheduled`, a cada 5 min) apaga os expirados e os órfãos (ver [`padroes/agendamento.md`](padroes/agendamento.md)).
- Reiniciar o container descarta pedidos em andamento, o que é aceitável no protótipo.

## Execução

`compose.yaml` (comum) + `compose.local.yaml` (local), com dois serviços. Detalhes em [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md).

| Serviço | Imagem | Observação |
|---|---|---|
| `api` | JDK 21 + LibreOffice | Porta interna 8080. Volume temporário para os dossiês. |
| `front` | nginx com o build do Angular | Publica a interface e encaminha `/api` para a `api`. |

O Oracle e as APIs do SIGA são acessados a partir da máquina onde o Docker roda. É preciso que essa máquina tenha rede até eles.

Sem `SIGA_DB_URL` (ausente ou vazia) ou com o SIGA inacessível, a `api` sobe normalmente (`/actuator/health` = `UP`, sem o banco do SIGA no health) e as consultas respondem `503 SIGA_INDISPONIVEL`. Requer Docker Compose 2.24 ou mais novo (`env_file` opcional).

## Configuração (`.env`)

O arquivo `.env.local` não é versionado e é opcional na Sprint 0 e 1 (a `api` sobe sem ele). O repositório traz um `.env.local.example` só com os nomes e placeholders neutros.

| Variável | Descrição |
|---|---|
| `SIGA_DB_URL` | URL JDBC do Oracle do SIGA |
| `SIGA_DB_USER` / `SIGA_DB_PASSWORD` | Usuário somente leitura (`VISTAS_LEITURA_SIGA`) |
| `SIGA_DB_POOL_MAX` | Tamanho máximo do pool de leitura (padrão 2 na fase inicial) |
| `SIGA_DB_CONN_TIMEOUT_MS` / `SIGA_DB_READ_TIMEOUT_MS` / `SIGA_DB_MAX_LIFETIME_MS` | Timeout de conexão (5000), de leitura do driver (30000) e vida máxima da conexão (1500000), em ms |
| `VISTAS_DEMO_SENHA` | Senha única dos usuários de demonstração (padrão `vistas-demo` só no perfil `local`) |
| `VISTAS_DB_POOL_MAX` | Tamanho máximo do pool do schema próprio (padrão 3 na fase inicial) |
| `AGENDAMENTO_ENABLED` | Liga as tarefas agendadas (padrão `true`) |
| `VISTAS_DB_URL` / `VISTAS_DB_USER` / `VISTAS_DB_PASSWORD` | Schema próprio (somente na versão definitiva) |
| `FLYWAY_ENABLED` | Flyway do schema próprio (versão definitiva) |
| `OPENAPI_ENABLED` | OpenAPI fora do ambiente local |
| `LICENCIAMENTO_API_URL` | Base da API do Licenciamento (`https://licenciamento-api.sema.mt.gov.br`) |
| `LICENCIAMENTO_API_TOKEN` | Token de acesso (ver [05 — Integrações](05-integracoes.md)) |
| `SCORP_API_URL` | Base da API do SCORP (**NÃO CONFIRMADO**) |
| `DOSSIE_TTL_MINUTOS` | Expiração dos arquivos gerados (padrão 30) |
| `DOSSIE_THREADS` | Gerações simultâneas (padrão 2) |
| `DOWNLOAD_TIMEOUT_SEGUNDOS` | Timeout por arquivo (padrão 60) |
| `CONVERSAO_TIMEOUT_SEGUNDOS` | Timeout por conversão (padrão 120) |

## Usuários de banco

- `VISTAS_LEITURA_SIGA`: no banco do SIGA, apenas `CREATE SESSION` e `SELECT` nas tabelas listadas em [04 — Modelo de dados](04-modelo-de-dados.md) (na versão definitiva, preferencialmente em views `VW_`).
- `VISTAS` (owner) e `APP_VISTAS` (aplicação): schema próprio, só na versão definitiva.

Detalhes em [`padroes/seguranca-privilegios.md`](padroes/seguranca-privilegios.md).
