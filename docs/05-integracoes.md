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
| `LICENCIAMENTO_API_TOKEN_FILE` | Opcional (S2.2): caminho de um arquivo com o token, relido a cada pedido; tem precedência sobre `LICENCIAMENTO_API_TOKEN` (ver "Token por arquivo") |
| `LICENCIAMENTO_API_AUTH_MODE` | `bearer` (padrão; vazio também vale como bearer) ou `cookie` |
| `LICENCIAMENTO_API_COOKIE_NAME` | Nome do cookie, no modo `cookie` |
| `DOWNLOAD_TIMEOUT_SEGUNDOS` | Tempo máximo de **inatividade** da leitura (sem receber bytes), inclusive no meio do corpo (padrão 120; até a S2.1 era 60 e valia também para a conexão) |
| `DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS` | Teto **total** para obter um documento, somando as novas tentativas (padrão 900) |
| `DOSSIE_ESPACO_MINIMO_MB` | Espaço livre mínimo no volume do diretório do pedido, conferido antes de cada download (padrão 2048; `0` desliga) |

A conexão tem tempo limite fixo de 10 s.

Comportamento (S2; tempos, novas tentativas e disco na S2.2):

- chave `"0"` (ou nula/vazia) **nunca é requisitada**: o documento é indisponível;
- **401 e 403** interrompem toda a geração, **sem nova tentativa**: o pedido fica `PENDENTE_DECISAO` (S2.2; antes `FALHA`) com `erro.codigo = CREDENCIAL_ARQUIVOS_RECUSADA` e a mensagem "Token da API de arquivos expirado ou recusado: capture um novo token." (nunca o valor do token); `CREDENCIAL_ARQUIVOS_RECUSADA` (502) é o código HTTP equivalente;
- **novas tentativas automáticas** (até 2 extras, com espera de 2 s e de 5 s) para **tempo limite de inatividade**, **503** e **queda ou recusa de conexão**. **404**, **401** e **403** nunca são repetidos; outros 5xx e demais status também não. Uma nova tentativa que não caiba no teto total não é feita. O número de pedidos feitos fica no item do pedido (`tentativas` no andamento, docs/07), sem chave nem URL; cada nova tentativa gera um log `INFO` só com o motivo;
- **teto total**: conferido a cada bloco gravado; estourado, o documento falha ("tempo total máximo excedido ao obter o arquivo") sem nova tentativa. Um corpo parado é cortado antes pela inatividade, então o tempo real fica no máximo em teto + inatividade;
- **disco**: antes de cada download o gerador compara o espaço livre do diretório do pedido com `DOSSIE_ESPACO_MINIMO_MB`; abaixo dele o documento falha com "espaço em disco insuficiente", sem chamar a API;
- **404**, **5xx**, tempo limite, conexão recusada e disco insuficiente falham **só aquele documento** ("arquivo não encontrado no repositório", "repositório de arquivos indisponível", "tempo limite excedido ao obter o arquivo", "tempo total máximo excedido ao obter o arquivo", "espaço em disco insuficiente"); o pedido termina a obtenção dos demais e fica em `PENDENTE_DECISAO` (RN09, S2.2);
- o binário é gravado direto em arquivo temporário (streaming em blocos de 64 KB, RNF03), nunca em memória, e o temporário é apagado em qualquer falha, inclusive entre uma tentativa e a seguinte;
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

#### Token por arquivo (S2.2)

`LICENCIAMENTO_API_TOKEN_FILE` (opcional) é o caminho, **dentro do container**, de um arquivo só com o token:

- o arquivo é relido a cada pedido e a cada nova tentativa, com cache pela data de modificação e pelo tamanho. Trocar o conteúdo vale já na próxima chamada, **sem recriar o container**;
- quando definida, tem **precedência** sobre `LICENCIAMENTO_API_TOKEN`;
- espaços e a quebra de linha final são aparados;
- arquivo ausente, ilegível ou vazio: o documento falha com "API de arquivos não configurada", sem chamar a API (o token da variável **não** é usado como reserva);
- o valor e o caminho do arquivo nunca vão para log, erro ou resposta (testado percorrendo as saídas de todos os cenários do adapter).

Montagem local: a pasta `./.secrets/` (no `.gitignore`) é montada somente leitura em `/run/secrets-vistas` por uma linha comentada do `compose.local.yaml`, e o `.env.local` aponta `LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos` (passo a passo no `README.md`). O `compose.verificacao.yaml` esvazia também essa variável.

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
