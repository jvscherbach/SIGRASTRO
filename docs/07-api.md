# 07 — API do protótipo

Prefixo: `/api`. JSON, exceto onde indicado. Todas as rotas, menos o login, exigem o token simulado no header `Authorization: Bearer <token>`.

## Acesso

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/login` | Login simulado. Corpo `{ "usuario": "...", "senha": "..." }`. Retorna token (opaco, válido por 8 h), `tipo` `Bearer`, `expiraEm` e o usuário (perfil `OPERADOR`, `OPERADOR_SIGILO` ou `ADMIN`). 401 `NAO_AUTENTICADO` se usuário ou senha estiverem errados; 400 se faltar campo. Não exige token. |
| `GET` | `/auth/me` | Usuário e perfil atuais, com as permissões do perfil. |
| `POST` | `/auth/logout` | Encerra a sessão atual (204). |

Usuários de demonstração (senha única `vistas-demo` no perfil `local`; configurável por `VISTAS_DEMO_SENHA`): `operador.demo` (`OPERADOR`, CNPJ `12.345.678/0001-90`), `gestor.demo` (`OPERADOR_SIGILO`) e `admin.demo` (`ADMIN`).

Resposta de `GET /auth/me` (e o campo `usuario` do login):

```json
{
  "login": "operador.demo",
  "nome": "Operador Demo",
  "perfil": "OPERADOR",
  "perfilDescricao": "Operador",
  "cpfCnpj": "12.345.678/0001-90",
  "permissoes": {
    "verSigilosos": false,
    "verAuditoriaAcessos": false,
    "acessarConfiguracoes": false,
    "restritoAoInteressado": true,
    "verResponsavelTarefa": false
  }
}
```

## Consulta

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/busca?tipo=&numero=&cpfCnpj=&nome=&pagina=&tamanho=` | Busca. **Sempre devolve `PageResponse<ResultadoBusca>`.** Só por número de **requerimento** (`tipo=REQUERIMENTO` + `numero`, formato `N/AAAA`): correspondência exata, página com 1 item. Só por número de **processo** (`tipo=PROCESSO`): página com **todos** os requerimentos daquele número (1:N, D31), ordenados por abertura decrescente e id. 404 `NAO_ENCONTRADO` se o número não existir. Com `cpfCnpj` (com ou sem máscara; comparado só com dígitos) e/ou `nome` (mínimo 3 caracteres; busca parcial sem diferenciar maiúsculas): lista paginada, ordenada por abertura decrescente (`pagina` a partir de 0, `tamanho` padrão 20, máximo 100). `numero` exige `tipo` e vice-versa. |
| `GET` | `/requerimentos/{id}` | Dados do requerimento (incluindo o número do processo, se protocolado), tipo do requerimento, prioridade, objeto e **tarefa atual**. |

**Vínculo com o interessado (RN07), aplicado no back-end desde a S1:** para o Operador, a busca por número de requerimento de outro interessado e o detalhe de outro interessado retornam 403 `NAO_INTERESSADO`; na busca por número de processo, se só alguns requerimentos são dele, a página traz só esses, e se o número existe mas nenhum é dele, 403 `NAO_INTERESSADO`; as listas só trazem os vinculados (o filtro é feito na consulta, então total e paginação já vêm corretos); Operador sem CPF/CNPJ cadastrado não enxerga nada. Gestor e Administrador acessam tudo.

Item de `ResultadoBusca` (a identificação segue a RN04):

```json
{
  "id": 101,
  "identificacao": { "rotulo": "Processo", "numero": "2825/2025", "protocolado": true },
  "numeroRequerimento": "3331/2025",
  "numeroProcesso": "2825/2025",
  "interessado": { "nome": "Associação Residencial Vale Verde", "cpfCnpj": "12345678000190" },
  "tipoProcesso": "Uso de Recursos Hídricos",
  "situacao": { "codigo": "EM_ANALISE", "descricao": "Em análise" },
  "abertura": "2025-03-31T10:12:00"
}
```

Resposta de `GET /requerimentos/{id}` para **Gestor e Administrador**:

```json
{
  "id": 101,
  "identificacao": { "rotulo": "Processo", "numero": "2825/2025", "protocolado": true },
  "numeroRequerimento": "3331/2025",
  "numeroProcesso": "2825/2025",
  "tipoRequerimento": "Requerimento de Outorga",
  "prioridade": {
    "codigo": "PRIORITARIO",
    "descricao": "Atendimento prioritário",
    "criterios": [
      { "codigo": 2, "descricao": "Pessoa com idade igual ou superior a 80 anos" },
      { "codigo": 5, "descricao": "Pessoa com doença grave" }
    ]
  },
  "situacao": { "codigo": "EM_ANALISE", "descricao": "Em análise" },
  "abertura": "2025-03-31T10:12:00",
  "atualizacao": "2025-04-29T09:14:00",
  "interessado": { "nome": "Associação Residencial Vale Verde", "cpfCnpj": "12345678000190" },
  "tipoProcesso": "Uso de Recursos Hídricos",
  "objeto": {
    "objetivos": ["Consumo humano", "Irrigação"],
    "finalidadesAtividades": [
      { "finalidade": "Abastecimento público", "atividade": "Captação para abastecimento" },
      { "finalidade": "Agricultura", "atividade": null }
    ],
    "tiposObjetivo": [
      { "tipo": "SUBTERRANEA", "tipoDescricao": "Subterrânea", "objetivo": "Consumo humano" },
      { "tipo": "SUPERFICIAL", "tipoDescricao": "Superficial", "objetivo": "Irrigação" }
    ],
    "atividadeLicenciamento": null
  },
  "tarefaAtual": {
    "estado": "ATIVA",
    "nome": "Realizar Análise do Processo",
    "desde": "2026-10-20T12:14:00",
    "responsavel": { "situacao": "ATRIBUIDO", "nome": "Maria Nova de Souza" },
    "fluxo": "Outorga de Recursos Hídricos"
  }
}
```

Para o **Operador**, a mesma resposta vem **sem** `tarefaAtual.responsavel` (o campo é omitido do JSON, não vem como texto genérico):

```json
"tarefaAtual": {
  "estado": "ATIVA",
  "nome": "Realizar Análise do Processo",
  "desde": "2026-10-20T12:14:00",
  "fluxo": "Outorga de Recursos Hídricos"
}
```

- Datas e horas sem fuso, no horário de Cuiabá.
- `tipoRequerimento`: descrição de `TIPO_REQUERIMENTO`; `null` só se o SIGA não informar.
- `prioridade.codigo`: `NORMAL` (nenhum critério) ou `PRIORITARIO` (pelo menos um). Código de critério desconhecido vem com `descricao` "Critério N" (D30).
- `objeto`: listas independentes (P01 em [04](04-modelo-de-dados.md)). `finalidadesAtividades[].atividade` é `null` quando a atividade não existe na Arrecadação. `tiposObjetivo` só tem itens com objetivos de outorga; `tipoDescricao` traz "Subterrânea", "Superficial" ou "Barramento", ou o texto cru para valor desconhecido. `atividadeLicenciamento` é `{ grupo, atividade }` ou `null`. Sem dados, listas vazias; a tela mostra "—".
- Falha ao ler o objeto ou a prioridade derruba o detalhe (503 `SIGA_INDISPONIVEL` ou 500), sem estado parcial. A tarefa atual continua isolada (`INDISPONIVEL`).
- `situacao.codigo` é o valor do SIGA; valor fora do Apêndice A volta com o texto cru em `descricao`.
- `tarefaAtual.estado`: `ATIVA` (com nome e desde), `SEM_TAREFA` (instância encerrada ou sem tarefa) ou `INDISPONIVEL` (a leitura da tarefa falhou; o restante do detalhe é entregue normalmente).
- `tarefaAtual.responsavel` (RN12, D32): `{ situacao, nome }`, só para Gestor e Administrador (`permissoes.verResponsavelTarefa` em `/auth/me`). `situacao`: `ATRIBUIDO` (com `nome`), `NAO_ATRIBUIDA` ou `NAO_IDENTIFICADO` (CPF sem nome nas tabelas do SIGA, ou valor em outro formato). O CPF do servidor nunca sai na API.
- A chave do arquivo e qualquer nome interno do SIGA nunca saem na API (RNF09).

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

`405` (método não permitido) e `415` (tipo de conteúdo não suportado) usam o `errorCode` `PARAMETRO_INVALIDO` com o status correspondente; um código próprio pode ser criado depois. Erros de validação trazem `campos` (`campo` e `mensagem`). Sem `SIGA_DB_URL`, ou com o SIGA fora do ar, toda consulta responde `503 SIGA_INDISPONIVEL`. Coluna inexistente ou falta de permissão no SIGA aparecem como `500 ERRO_INTERNO`, sem detalhe técnico na resposta.

Listas paginadas (busca por interessado, auditoria) usam `PageResponse<T>`.
