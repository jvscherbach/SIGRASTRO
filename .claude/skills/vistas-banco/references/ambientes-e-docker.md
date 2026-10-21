# Ambientes e Docker Compose

## Ambientes

| Ambiente | Situação | Serviços no Compose | Banco do SIGA | Schema próprio |
|---|---|---|---|---|
| local (protótipo) | definido | `api` e `front` | externo, somente leitura | não existe (memória) |
| local (definitivo) | previsto | `api`, `front` e `oracle` | externo, somente leitura | container Oracle Database 23 Free (`FREEPDB1`), compatível com 19c |
| homologação | **não definido** | — | externo | externo |
| produção | **não definido** (alvo provável: Kubernetes do SIGA) | — | externo | externo |

O banco do SIGA **nunca** entra no Compose: é sempre externo, acessado por rede.

## Arquivos Compose

- `compose.yaml`: configurações comuns (`api` com JDK 21 + LibreOffice; `front` com nginx servindo o build do Angular e encaminhando `/api`).
- `compose.local.yaml`: portas locais, volume temporário de dossiês e, no definitivo, o serviço `oracle` (imagem Oracle Database 23 Free com **tag fixa**, nunca `latest`), healthcheck, volume de dados e dependência da `api`.
- Fase inicial: a aplicação roda inteira em Docker (`api` e `front`); o Oracle local só entra quando surgir a primeira tabela do schema próprio.
- Overrides de homologação/produção só serão criados quando a infraestrutura for definida; não conterão banco nem volume de banco.

## Variáveis de ambiente

`.env.local` (e futuros `.env.homolog`/`.env.prod`) **não são versionados**. Templates `.example` só com placeholders neutros.

| Variável | Uso |
|---|---|
| `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD` | Leitura do banco do SIGA |
| `SIGA_DB_POOL_MAX` | Tamanho máximo do pool de leitura do SIGA (padrão 2 na fase inicial) |
| `VISTAS_DB_POOL_MAX` | Tamanho máximo do pool do schema próprio (padrão 3 na fase inicial) |
| `AGENDAMENTO_ENABLED` | Liga as tarefas agendadas (padrão `true`) |
| `VISTAS_DB_URL`, `VISTAS_DB_USER`, `VISTAS_DB_PASSWORD` | Schema próprio (definitivo) |
| `FLYWAY_ENABLED` | Flyway do schema próprio (definitivo) |
| `LICENCIAMENTO_API_URL`, `LICENCIAMENTO_API_TOKEN` | API de arquivos |
| `SCORP_API_URL` | API do SCORP (documentos pessoais) |
| `DOSSIE_TTL_MINUTOS`, `DOSSIE_THREADS`, `DOWNLOAD_TIMEOUT_SEGUNDOS`, `CONVERSAO_TIMEOUT_SEGUNDOS` | Valores iniciais; na versão definitiva podem ser sobrescritos pela tela de configurações |
| `OPENAPI_ENABLED` | OpenAPI fora do ambiente local |

## Cuidados

- O protótipo usa token de sessão pessoal: uso local apenas.
- Nunca usar `docker compose down -v` quando houver banco próprio local (apaga os dados).
- Não definir registry, secret manager, hostnames ou credenciais institucionais: ainda não decididos.
