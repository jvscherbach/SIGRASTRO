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
- `compose.verificacao.yaml`: **só para verificar o código sem tocar em sistemas externos.** Esvazia, no serviço `api`, `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD`, `LICENCIAMENTO_API_URL`, `LICENCIAMENTO_API_TOKEN`, `LICENCIAMENTO_API_AUTH_MODE`, `LICENCIAMENTO_API_COOKIE_NAME`, `SCORP_API_URL` e `VISTAS_DB_*`. A chave `environment` tem precedência sobre o `env_file`. Versionado, sem segredo (D34).
- `compose.local.yaml`: portas locais, volume temporário de dossiês e, no definitivo, o serviço `oracle` (imagem Oracle Database 23 Free com **tag fixa**, nunca `latest`), healthcheck, volume de dados e dependência da `api`.
- Fase inicial: a aplicação roda inteira em Docker (`api` e `front`); o Oracle local só entra quando surgir a primeira tabela do schema próprio.
- Overrides de homologação/produção só serão criados quando a infraestrutura for definida; não conterão banco nem volume de banco.

## Variáveis de ambiente

`.env.local` (e futuros `.env.homolog`/`.env.prod`) **não são versionados**. Templates `.example` só com placeholders neutros.

| Variável | Uso |
|---|---|
| `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD` | Leitura do banco do SIGA |
| `SIGA_DB_POOL_MAX` | Tamanho máximo do pool de leitura do SIGA (padrão 2 na fase inicial) |
| `SIGA_DB_CONN_TIMEOUT_MS`, `SIGA_DB_READ_TIMEOUT_MS`, `SIGA_DB_MAX_LIFETIME_MS` | Timeout de conexão (5000), de leitura do driver (30000) e vida máxima da conexão (1500000), em ms |
| `VISTAS_DEMO_SENHA` | Senha única dos usuários de demonstração; o padrão (`vistas-demo`) só existe no perfil `local` |
| `VISTAS_DOSSIE_DIRETORIO` | Diretório temporário dos dossiês (padrão `/var/tmp/vistas`) |
| `VISTAS_DB_POOL_MAX` | Tamanho máximo do pool do schema próprio (padrão 3 na fase inicial) |
| `AGENDAMENTO_ENABLED` | Liga as tarefas agendadas (padrão `true`) |
| `VISTAS_DB_URL`, `VISTAS_DB_USER`, `VISTAS_DB_PASSWORD` | Schema próprio (definitivo) |
| `FLYWAY_ENABLED` | Flyway do schema próprio (definitivo) |
| `LICENCIAMENTO_API_URL` | Base da API de arquivos do Licenciamento. **Ausente ou vazia = "arquivos não configurados"**: a `api` sobe, a lista funciona e a geração falha com o motivo "API de arquivos não configurada" |
| `LICENCIAMENTO_API_TOKEN` | Token de acesso (ver [05 — Integrações](05-integracoes.md)); só no `.env.local`, nunca em log, URL ou resposta |
| `LICENCIAMENTO_API_AUTH_MODE` | `bearer` (padrão; vazio também vale) ou `cookie` |
| `LICENCIAMENTO_API_COOKIE_NAME` | Nome do cookie, no modo `cookie` |
| `DOSSIE_SUMARIO` / `DOSSIE_MARCADORES` / `DOSSIE_NUMERACAO` | Sumário, marcadores e numeração de páginas (padrão ligados) |
| `vistas.documentos.origens.<origem>` | `true`/`false` por origem de documento (as seis origens da Vista ligadas por padrão, S2.1) |
| `SCORP_API_URL` | API do SCORP (documentos pessoais) |
| `DOSSIE_TTL_MINUTOS`, `DOSSIE_THREADS`, `DOWNLOAD_TIMEOUT_SEGUNDOS`, `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS`, `DOSSIE_ESPACO_MINIMO_MB`, `CONVERSAO_TIMEOUT_SEGUNDOS` | Valores iniciais; na versão definitiva podem ser sobrescritos pela tela de configurações |
| `OPENAPI_ENABLED` | OpenAPI fora do ambiente local |

## Sem o SIGA configurado

`SIGA_DB_URL` ausente ou vazia **não impede a `api` de subir**: `/actuator/health` fica `UP`, o login e o front funcionam e qualquer consulta ao SIGA responde `503 SIGA_INDISPONIVEL`. O mesmo vale se o SIGA estiver inacessível. O `.env.local` é opcional (`env_file` com `required: false`), o que exige **Docker Compose 2.24 ou mais novo**.

## Verificação sem sistemas externos (regra, D34)

O `.env.local` do responsável pode ter acesso ao SIGA, à API de arquivos e ao SCORP reais. Por isso:

1. **Nunca abrir, imprimir ou copiar o conteúdo do `.env.local`** (nem por `cat`, `Get-Content`, `type` ou ferramenta de leitura). Testes de "está preenchido?" só sem mostrar o valor e **tolerando espaço no início da linha**.
2. Todo `docker compose up` de verificação usa **os três arquivos**: `-f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml`.
3. Antes de qualquer chamada à API, confirmar **dentro do container**, sem imprimir valores, que cada variável externa está vazia: `docker compose ... exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA'`. Se alguma vier `PREENCHIDA`: parar, derrubar os containers (**sem `-v`**), não fazer chamada alguma e relatar.
4. Se uma resposta trouxer dado real (nome, CPF/CNPJ, número de processo que não seja de teste): parar, não copiar o dado para arquivo, mensagem ou log e relatar.
5. Os testes usam portas falsas, Oracle 23 Free por Testcontainers e um servidor HTTP local; token real nunca entra em teste, exemplo, log ou documentação.

## Cuidados

- **Linhas do `.env.local` não podem começar com espaço** (nem ter espaço em volta do `=`).

- O protótipo usa token de sessão pessoal: uso local apenas.
- Nunca usar `docker compose down -v` quando houver banco próprio local (apaga os dados).
- Não definir registry, secret manager, hostnames ou credenciais institucionais: ainda não decididos.
