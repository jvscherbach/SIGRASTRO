# 07 — API do protótipo

Prefixo: `/api`. JSON, exceto onde indicado. Todas as rotas, menos o login, exigem o token simulado no header `Authorization: Bearer <token>`.

## Acesso

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/login` | Login simulado. Corpo `{ "usuario": "...", "senha": "..." }`. Retorna token e perfil (`OPERADOR` ou `OPERADOR_SIGILO`). |
| `GET` | `/auth/me` | Usuário e perfil atuais. |

## Consulta

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/busca?tipo=REQUERIMENTO\|PROCESSO&numero=N/AAAA` | Busca por número. Por processo, retorna o requerimento correspondente (relação 1:1). |
| `GET` | `/busca?cpfCnpj=...&nome=...` | Busca por interessado. Retorna lista paginada. |
| `GET` | `/requerimentos/{id}` | Dados do requerimento (incluindo o número do processo, se protocolado) e tarefa atual no Camunda. |

## Documentos

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/requerimentos/{id}/documentos` | Documentos do requerimento (e do processo, quando protocolado). |
| `GET` | `/documentos/{origem}/{id}/conteudo?formato=original\|pdf` | Conteúdo do documento para o modal. Retorna binário. Com `formato=pdf`, converte quando necessário. Bloqueado para sigilosos sem perfil. |

Item da lista de documentos:

```json
{
  "origem": "DOCUMENTO_PROCESSO",
  "id": 804328,
  "idRequerimento": 12345,
  "titulo": "RG e CPF",
  "nomeArquivo": "RG e CPF.pdf",
  "origemDescricao": "Representante legal",
  "data": "2025-03-31T10:12:00",
  "situacao": "VIGENTE",
  "sigiloso": false,
  "acessivel": true,
  "extensao": "pdf"
}
```

- `situacao`: `VIGENTE`, `SUBSTITUIDO` ou `INDISPONIVEL`. Usada pelo front-end para a pré-seleção e o bloqueio de indisponíveis; não é exibida como coluna.
- `acessivel`: `false` quando o perfil não permite acessar documentos sigilosos.
- A chave do arquivo nunca é retornada.

## Dossiê

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/dossies` | Cria um pedido de geração. Retorna `202` com `{ "id": "..." }`. |
| `GET` | `/dossies/{id}` | Andamento do pedido. |
| `GET` | `/dossies/{id}/arquivo?disposicao=inline\|attachment` | PDF final. `inline` para pré-visualização, `attachment` para download. |
| `DELETE` | `/dossies/{id}` | Cancela o pedido ou descarta o arquivo. |

Corpo do `POST /dossies`:

```json
{
  "idRequerimento": 12345,
  "documentos": [
    { "origem": "DOCUMENTO_PROCESSO", "id": 804328 },
    { "origem": "SCORP", "id": "..." }
  ]
}
```

Resposta do `GET /dossies/{id}`:

```json
{
  "id": "...",
  "status": "PROCESSANDO",
  "total": 55,
  "processados": 21,
  "erro": null,
  "paginas": null,
  "expiraEm": "2026-10-19T18:30:00"
}
```

`status`: `PENDENTE`, `PROCESSANDO`, `CONCLUIDO`, `FALHA`, `CANCELADO` ou `EXPIRADO`.

Em `FALHA`, `erro` traz o documento e o motivo, e `GET /dossies/{id}/arquivo` retorna 409 (não há arquivo):

```json
"erro": { "origem": "JUNTADA", "id": 900118, "titulo": "Ofício juntado ao processo", "motivo": "Tempo limite excedido ao obter o arquivo" }
```

Para tentar de novo, o cliente envia um novo `POST /dossies` (com ou sem o documento que falhou).

## Auditoria

`{tipo}` é `documentos` (perfil `OPERADOR_SIGILO`) ou `acessos` (perfil `ADMIN`). Pedido de um tipo por perfil não autorizado retorna 403.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/auditoria/{tipo}?de=&ate=&evento=&usuario=&q=&sigiloso=&pagina=` | Lista paginada, do mais recente para o mais antigo. `sigiloso` só em `documentos`. |
| `GET` | `/auditoria/{tipo}/resumo?de=&ate=` | Indicadores do período. |
| `GET` | `/auditoria/{tipo}/{id}` | Detalhe do evento (documentos ou alterações antes/depois). |
| `GET` | `/auditoria/{tipo}/exportacao?…` | CSV dos eventos filtrados. |

O registro dos eventos é feito pelo próprio back-end nas rotas de consulta, conteúdo de documento, geração e download do dossiê. Não há rota de escrita exposta.

## Configurações

Restrito ao perfil `ADMIN`. Toda escrita gera evento na auditoria de acessos.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/configuracoes` | Parâmetros vigentes. |
| `PUT` | `/configuracoes` | Grava os parâmetros alterados (um evento `CONFIG` com a lista de mudanças). |
| `GET` | `/usuarios` | Usuários com perfil, situação e último acesso. |
| `POST` | `/usuarios` | Inclui usuário. |
| `PATCH` | `/usuarios/{id}` | Altera perfil ou situação. |
| `GET` | `/conexoes` | Estado das conexões. |
| `POST` | `/conexoes/{nome}/teste` | Testa uma conexão e devolve o tempo de resposta. |

## Erros

Todo erro segue `ProblemDetail` com `errorCode` estável e `correlationId` (header `X-Correlation-Id`). Detalhes em [`padroes/http-erros-openapi.md`](padroes/http-erros-openapi.md).

```json
{
  "type": "about:blank",
  "title": "Acesso negado",
  "status": 403,
  "detail": "Você não é o interessado deste processo ou requerimento.",
  "errorCode": "NAO_INTERESSADO",
  "timestamp": "2026-10-20T13:10:00-04:00",
  "path": "/api/requerimentos/20871",
  "correlationId": "3f6b2c1e-..."
}
```

| HTTP | `errorCode` | Situação |
|---|---|---|
| 400 | `PARAMETRO_INVALIDO` | Parâmetros inválidos |
| 401 | `NAO_AUTENTICADO` | Login ausente ou inválido |
| 403 | `PERFIL_SEM_PERMISSAO` | Função restrita ao perfil |
| 403 | `DOCUMENTO_SIGILOSO` | Documento sigiloso para perfil não autorizado |
| 403 | `NAO_INTERESSADO` | Operador sem vínculo com o processo/requerimento |
| 404 | `NAO_ENCONTRADO` | Requerimento, processo, documento, dossiê ou evento inexistente |
| 409 | `DOSSIE_SEM_ARQUIVO` | Arquivo inexistente porque a geração falhou |
| 410 | `DOSSIE_EXPIRADO` | Dossiê expirado |
| 502 | `CREDENCIAL_ARQUIVOS_RECUSADA` | API de arquivos recusou a credencial |
| 503 | `SIGA_INDISPONIVEL` | Banco do SIGA indisponível |
| 500 | `ERRO_INTERNO` | Demais erros |

Listas paginadas (busca por interessado, auditoria) usam `PageResponse<T>`.
