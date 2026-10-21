# 05 — Integrações

## API do Licenciamento — arquivos

| Item | Valor |
|---|---|
| Endpoint | `GET {LICENCIAMENTO_API_URL}/api/arquivo/{chave}` |
| Parâmetro opcional | `versao` (número da versão no repositório de arquivos) |
| Autenticação | Token de sessão de usuário (`Authorization: Bearer ...`, ou cookie de autorização) |
| Resposta | Binário completo, com `Content-Type`, `Content-Length` e `Content-Disposition: inline; filename=...` |
| Erros | Falha no repositório de arquivos retorna HTTP 503; token ausente ou inválido, 401 |

Comportamento no protótipo:

- chave `"0"` não é requisitada: o documento é marcado como indisponível;
- 401 interrompe a geração com mensagem clara de token expirado;
- 404, 503 e timeout marcam apenas o documento como falho;
- o binário é gravado direto em arquivo temporário (streaming), sem passar por memória.

### Token no protótipo

O protótipo usa o token da sessão de um usuário logado no SIGA, capturado no navegador:

1. Entrar no SIGA Licenciamento e abrir a Vista do Processo de um requerimento.
2. DevTools → aba **Network**, com **Preserve log** marcado.
3. Visualizar um documento, o que dispara `GET .../api/arquivo/<chave>`.
4. Em **Request Headers**, copiar o valor de `Authorization` (sem o prefixo `Bearer`) para `LICENCIAMENTO_API_TOKEN`. Se não houver esse header, copiar o cookie de autorização e configurar `LICENCIAMENTO_API_AUTH_MODE=cookie` e `LICENCIAMENTO_API_COOKIE_NAME`.

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
