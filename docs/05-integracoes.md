# 05 — Integrações

## API do Licenciamento — arquivos

| Item | Valor |
|---|---|
| Endpoint | `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}` |
| Parâmetro opcional | `versao` (número da versão no repositório de arquivos) |
| Autenticação | Token de sessão de usuário, em um de dois modos: `bearer` (`Authorization: Bearer <token>`, padrão) ou `cookie` (`Cookie: <nome>=<token>`) |
| Resposta | Binário completo, com `Content-Type`, `Content-Length` e `Content-Disposition: inline; filename=...` |
| Erros | Falha no repositório de arquivos retorna HTTP 503; token ausente ou inválido, 401 |

Variáveis (todas no `.env.local`, nunca versionado; `.env.local.example` só traz os nomes, comentados):

| Variável | Uso |
|---|---|
| `LICENCIAMENTO_API_URL` | Base da API. **Ausente ou vazia = "arquivos não configurados"** (mesmo padrão do SIGA, D26): a `api` sobe, a lista funciona e a geração falha com o motivo "API de arquivos não configurada" |
| `LICENCIAMENTO_API_TOKEN` | Token capturado (sem o prefixo `Bearer`) |
| `LICENCIAMENTO_API_AUTH_MODE` | `bearer` (padrão; vazio também vale como bearer) ou `cookie` |
| `LICENCIAMENTO_API_COOKIE_NAME` | Nome do cookie, no modo `cookie` |
| `DOWNLOAD_TIMEOUT_SEGUNDOS` | Tempo limite de conexão e de leitura (sem receber bytes), por arquivo (padrão 60) |

Comportamento (S2):

- chave `"0"` (ou nula/vazia) **nunca é requisitada**: o documento é indisponível;
- **401 e 403** interrompem toda a geração: o pedido fica `FALHA` com `erro.codigo = CREDENCIAL_ARQUIVOS_RECUSADA` e a mensagem "Token da API de arquivos expirado ou recusado: capture um novo token." (nunca o valor do token); `CREDENCIAL_ARQUIVOS_RECUSADA` (502) é o código HTTP equivalente;
- **404**, **5xx**, tempo limite e conexão recusada falham **só aquele documento** ("arquivo não encontrado no repositório", "repositório de arquivos indisponível", "tempo limite excedido ao obter o arquivo"), o que interrompe o dossiê (RN09);
- o binário é gravado direto em arquivo temporário (streaming, RNF03), nunca em memória, e o temporário é apagado em qualquer falha;
- o tipo real é conferido pela assinatura `%PDF-` do arquivo (o `Content-Type` do repositório não basta): conteúdo que não é PDF falha o documento ("o arquivo não é um PDF válido");
- **nenhuma resposta ou log traz URL institucional, token ou chave** (testado).

### Token no protótipo

O protótipo usa o token da sessão de um usuário logado no SIGA, capturado no navegador:

1. Entrar no SIGA Licenciamento e abrir a Vista do Processo de um requerimento.
2. DevTools → aba **Network**, com **Preserve log** marcado.
3. Visualizar um documento, o que dispara `GET .../api/arquivo/<chave>`.
4. Em **Request Headers**, copiar o valor de `Authorization` (sem o prefixo `Bearer`) para `LICENCIAMENTO_API_TOKEN`. Se não houver esse header, copiar o cookie de autorização e configurar `LICENCIAMENTO_API_AUTH_MODE=cookie` e `LICENCIAMENTO_API_COOKIE_NAME`.
5. **Linhas do `.env.local` não podem começar com espaço.** Antes de subir, nunca imprima o arquivo; para verificar sem mostrar valores, teste dentro do container (ver [`padroes/ambientes-e-docker.md`](padroes/ambientes-e-docker.md)).

Restrições:

- o token identifica o usuário que o capturou, e todos os acessos ficam registrados em seu nome;
- uso exclusivamente local, sem versionamento;
- o token expira. Quando a API retornar 401, ele deve ser capturado novamente.

**Versão definitiva:** credencial de serviço (API key emitida pelo serviço de identidade e validada em `/api/auth/verify-api-key`) ou acesso direto ao Pandora. Ver [10 — Pendências e riscos](10-pendencias-e-riscos.md).

## SCORP — documentos pessoais (`integration.scorp`)

| Item | Valor |
|---|---|
| Endpoint usado pelo SIGA | `.../documentos/pessoa-licenciamento/{idCorporativo}` |
| Base | **NÃO CONFIRMADO** (`SCORP_API_URL`) |
| Autenticação | **NÃO CONFIRMADO**. Testar com o mesmo token da API do Licenciamento. |
| Retorno | Lista com `chave`, `nomeArquivo`, `descricao`, `dataInicio`, `dataFim` |
| Pessoas consultadas | Interessado, representantes legais e responsáveis técnicos do requerimento (`PESSOA.ID_CORPORATIVO`) |

Os arquivos retornados pelo SCORP são obtidos pelo mesmo endpoint `/api/arquivo/{chave}`. **NÃO CONFIRMADO**: verificar se a chave do SCORP é válida nesse endpoint.

Falha no SCORP não impede a listagem. Os documentos de origem `SCORP` aparecem como indisponíveis, com aviso.

## Arrecadação — DAR

**NÃO CONFIRMADO.** O SIGA gera um ZIP de DARs por requerimento a partir do sistema de Arrecadação. Antes de incluir no protótipo, é preciso identificar:

- o endpoint e a autenticação;
- se o DAR é arquivo armazenado (com chave) ou gerado sob demanda.

Até a confirmação, a origem `DAR` fica desativada por configuração.

## Camunda

Leitura direta das tabelas `SIGA2_CAMUNDA` (ver [04 — Modelo de dados](04-modelo-de-dados.md)). O BPMS do SIGA não é chamado.

Risco: se o SIGA migrar para Camunda 8, essas tabelas deixam de existir.
