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
| `GET` | `/requerimentos/{id}/documentos` | Documentos **do requerimento** (a chave é o requerimento, D31), das seis origens da Vista (S2.1), deduplicados (RN05), sem compactados (RN11), em ordem cronológica (RN03); as geometrias vêm à parte, em `geometrias` (RN10, S4). Aplica a RN07 e o sigilo (D36): Operador de outro interessado, 403 `NAO_INTERESSADO`; requerimento inexistente, 404 `NAO_ENCONTRADO`; SIGA fora do ar, 503 `SIGA_INDISPONIVEL`. |
| `GET` | `/requerimentos/{id}/geometrias/arquivo` | **S4.** "Baixar geometrias": ZIP com todas as geometrias vigentes que o usuário acessa, tudo ou nada. Detalhes em "Geometrias", abaixo. |
| `GET` | `/documentos/{origem}/{id}/conteudo?requerimento={idRequerimento}&formato=pdf\|original` | **S3.** Conteúdo do documento para o modal (binário, em streaming). `formato=pdf` (padrão) converte quando necessário. Acesso conferido a cada requisição, mesmo com cache (D55). Detalhes abaixo. |

Resposta de `GET /requerimentos/{id}/documentos`:

```json
{
  "itens": [
    {
      "origem": "DOCUMENTO_PROCESSO",
      "id": "804328",
      "idRequerimento": 12345,
      "titulo": "RG e CPF",
      "nomeArquivo": "RG e CPF.pdf",
      "origemDescricao": "Documento do processo",
      "data": "2025-03-31T10:12:00",
      "situacao": "VIGENTE",
      "sigiloso": false,
      "acessivel": true,
      "extensao": "pdf",
      "tipoArquivo": "PDF",
      "selecionavelPorPadrao": true,
      "bloqueio": null
    }
  ],
  "totais": { "total": 55, "vigentes": 48, "semEfeito": 5, "naoPdf": 6, "tipoNaoInformado": 1, "indisponiveis": 2, "sigilosos": 3, "convertiveis": 5, "geometrias": 1, "geometriasOmitidas": 0 },
  "geometrias": [
    {
      "origem": "DOCUMENTO_PROCESSO",
      "id": "804400",
      "idRequerimento": 12345,
      "titulo": "Polígono da área",
      "nomeArquivo": "area.kml",
      "origemDescricao": "Documento do processo",
      "data": "2025-04-02T09:00:00",
      "situacao": "VIGENTE",
      "sigiloso": false,
      "extensao": "kml",
      "tipoArquivo": "GEOMETRIA",
      "tamanho": null
    }
  ]
}
```

- `id` é **texto** em todo o contrato (`"804328"`), para acomodar o SCORP. `origem` é o código (`DOCUMENTO_PROCESSO`, `DOCUMENTO_REQUERIMENTO`, `COMPLEMENTACAO`, `IMOVEL`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`) e `origemDescricao`, o texto da tela.
- `situacao`: `VIGENTE`, `SUBSTITUIDO` ou `INDISPONIVEL` (RN01). Não é exibida como coluna.
- `tipoArquivo`: `PDF`, `PLANILHA`, `TEXTO`, `IMAGEM`, `GEOMETRIA`, `COMPACTADO` ou `DESCONHECIDO` (pela extensão do nome do arquivo; sem extensão ou extensão não reconhecida é `DESCONHECIDO`).
- `bloqueio` (por que o documento não pode ser selecionado): `null`, `NAO_PDF` (extensão conhecida que não é PDF e não é convertível; com `CONVERSAO_HABILITADA=false` ou conversor indisponível, todo não PDF; `DESCONHECIDO` não recebe `NAO_PDF`; planilha, texto e imagem convertíveis só recebem `NAO_PDF` com a conversão desligada, S3), `INDISPONIVEL` ou `SIGILOSO` (sem perfil). Precedência: indisponível, sigiloso, não PDF.
- `selecionavelPorPadrao` (S2.2, RN13): significa "selecionável" e é a seleção inicial: `true` exatamente quando `bloqueio` é `null`. Entram vigentes e sem efeito (substituídos), de qualquer tipo, inclusive `DESCONHECIDO`.
- `acessivel`: `false` quando o documento é sigiloso e o perfil não o acessa (Operador). Nesse caso o documento vem **mascarado**: `titulo` = "Documento sigiloso" e `nomeArquivo`, `extensao` e `tipoArquivo` são `null` (RF20).
- `geometrias` (S4, RN10): as geometrias (SHP, KML, KMZ) da lista consolidada, em ordem cronológica, **fora** de `itens`, da seleção, dos totais acima e do dossiê. Mesmos campos de identificação e situação dos documentos, sem os de seleção (`acessivel`, `selecionavelPorPadrao`, `bloqueio`), mais `tamanho` (bytes; `null` enquanto desconhecido: o SIGA não o informa). Para o Operador, geometria sigilosa **não aparece**: nem nome, nem data, nem id; só entra em `totais.geometriasOmitidas`. Gestor e Administrador veem todas (`sigiloso` indica a sigilosa).
- `totais`: contagem de toda a lista, depois da consolidação (RN05, RN11 e geometrias fora). `totais.geometrias` é o tamanho de `geometrias`; `totais.geometriasOmitidas`, as sigilosas que o perfil não vê (sempre 0 para Gestor e Administrador).
  - Por situação, excludentes, somam o total: `total = vigentes + semEfeito + indisponiveis`. `vigentes` conta `VIGENTE` (sem `DATA_FIM`, chave válida); `semEfeito` conta `SUBSTITUIDO` (`DATA_FIM` preenchida, chave válida); `indisponiveis` conta `INDISPONIVEL` (chave nula, vazia ou `"0"`), qualquer que seja a `DATA_FIM`.
  - Recortes que cruzam as situações: `naoPdf` (extensão conhecida que não é PDF; não entra em `tipoNaoInformado`), `tipoNaoInformado` (tipo `DESCONHECIDO`), `sigilosos` e `convertiveis` (S3: planilhas, textos e imagens convertíveis para PDF que o perfil pode selecionar, isto é, sem bloqueio; sempre 0 com a conversão desligada).
  - `tipoArquivo` vem da extensão; o tipo real (conteúdo) só é conhecido ao obter o arquivo e é conferido ao gerar o dossiê.
  - Os selecionados não são um total da API: a tela conta os itens marcados; ao abrir, são os de `bloqueio` `null` (`total − indisponiveis − naoPdf − sigilosos bloqueados para o perfil`, sem contar duas vezes quem tem mais de um motivo; vale o `bloqueio` de cada item). Por isso "145 documentos · 71 vigentes" podia vir com "144 selecionados": os sem efeito também são selecionáveis, e "vigentes" nunca foi o total selecionável.
- **A chave do arquivo nunca é retornada** (RNF09); testado percorrendo as respostas.

### Geometrias (S4)

`GET /requerimentos/{id}/geometrias/arquivo` devolve **um ZIP** (`application/zip`, `attachment`, em streaming do disco) com todas as geometrias **vigentes** do requerimento que o usuário acessa. Substituídas e indisponíveis aparecem na lista, mas não vão para o ZIP.

- **Acesso** como na lista: requerimento inexistente, 404; Operador de outro interessado, 403 `NAO_INTERESSADO` (nada é lido); geometria sigilosa nunca vai para o Operador.
- **Sem geometria para baixar:** 404 `NAO_ENCONTRADO`, "Este requerimento não tem geometrias." Para o Operador, o texto é o mesmo quando só havia sigilosas (nada revela a diferença).
- **Obtenção:** mesmo cliente da API de arquivos do dossiê (tempos, novas tentativas, token por arquivo, docs/05), um arquivo por vez, para uma pasta temporária própria em `VISTAS_DOSSIE_DIRETORIO/geometrias`, com a checagem de `DOSSIE_ESPACO_MINIMO_MB` antes de cada um. As falhas são **coletadas** (todos os arquivos são tentados).
- **Tudo ou nada:** se algum arquivo falhar, nenhum ZIP é entregue: `GEOMETRIAS_INCOMPLETAS`, **502** (ou **504** quando todas as falhas são de tempo limite), com `arquivos: [{ "nome": "area-2.kml", "motivo": "arquivo não encontrado no repositório" }]` (nome que o arquivo teria no ZIP; motivo exibível; nunca a chave, a URL ou o token). Credencial recusada: 502 `CREDENCIAL_ARQUIVOS_RECUSADA`, sem ZIP.
- **Limites:** soma dos arquivos acima de `GEOMETRIAS_MAX_MB` (500) ou mais de `GEOMETRIAS_MAX_ARQUIVOS` (200) arquivos: 413 `GEOMETRIAS_GRANDES_DEMAIS` (o limite de arquivos é conferido antes de baixar; o de tamanho, a cada arquivo obtido).
- **Nomes dentro do ZIP:** o nome do arquivo de origem, quando possível; sem caminho (só o último segmento), sem caracteres de controle ou de formatação (inclusive a inversão de direção U+202E), sem `< > : " | ? *` (viram `_`), sem `..`, até 120 caracteres com a extensão preservada (em minúsculas); vazio vira `geometria.<ext>`. **Únicos** sem diferenciar maiúsculas: na colisão, `area-2.kml`, `area-3.kml`... A data de cada entrada é a data do documento.
- **Cabeçalhos:** `Content-Disposition: attachment` com `geometrias-processo-<numero>-<ano>.zip` ou `geometrias-requerimento-<numero>-<ano>.zip` (mesma regra do RF18), `Content-Length`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.
- **Temporários:** cada original sai do disco assim que entra no ZIP; a pasta do download é apagada quando a resposta termina, é interrompida ou falha. Uma limpeza agendada apaga, na subida, todas as pastas que sobraram e, a cada `limpeza-orfaos`, as com mais de 6 h.
- O dossiê continua **sem** geometrias: `POST /dossies` com o id de uma geometria responde 400 (o documento não pertence à lista do dossiê).

### Conteúdo para o modal (S3)

`GET /documentos/{origem}/{id}/conteudo?requerimento={idRequerimento}&formato=pdf|original`

- **Acesso antes de tudo, a cada requisição** (também quando o arquivo já está no cache): RN07 no requerimento (Operador de outro interessado, 403 `NAO_INTERESSADO`; requerimento inexistente, 404); o documento é **relido da lista do requerimento** no servidor e `origem` + `id` precisam estar nela (os ids se repetem entre origens; origem inválida, id inexistente ou de outro requerimento: 404 `NAO_ENCONTRADO`); sigiloso sem perfil, 403 `DOCUMENTO_SIGILOSO`; indisponível (chave `"0"`), 404. Nada é baixado antes dessas checagens.
- `requerimento` é o `idRequerimento` do item da lista (D55). Obrigatório.
- `formato=original`: o arquivo como veio. `Content-Type` pelo tipo real (conteúdo, não nome). PDF e imagem vêm `inline`; todo o resto (inclusive HTML, que o navegador executaria) vem como `attachment`.
- `formato=pdf` (padrão): o PDF, se já for PDF; senão convertido com o **mesmo conversor e os mesmos parâmetros do dossiê** (planilha, texto e HTML pelo LibreOffice; PNG e JPG pelo PDFBox), com o cabeçalho `X-Vistas-Convertido-De` (ex.: `XLSX`). Tipo sem conversão (compactado, não reconhecido) ou conversão desligada: 415 `FORMATO_NAO_SUPORTADO`.
- Cabeçalhos: `Content-Disposition` com nome seguro (só o nome, sem caminho, caractere de controle, aspas, `;` nem ponto inicial; até 150 caracteres; na versão em PDF, extensão trocada por `.pdf`), `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`.
- **Cache** em `VISTAS_DOSSIE_DIRETORIO/visualizacao`, uma pasta por `(origem, id)` com o original e o PDF: vale `VISUALIZACAO_TTL_MINUTOS` (10) desde a gravação; acima de `VISUALIZACAO_CACHE_MAX_MB` (500) saem os mais antigos. Limpeza no intervalo da expiração dos dossiês e esvaziamento na subida. Duas requisições do mesmo documento esperam uma pela outra (uma só obtenção e uma só conversão). Uma falha de conversão mantém o original no cache: a nova tentativa não baixa de novo.
- **Fila de conversões do modal**: até `VISUALIZACAO_CONVERSOES_SIMULTANEAS` (2) em paralelo e `VISUALIZACAO_FILA_CONVERSOES` (4) esperando; além disso, 429 `MUITAS_CONVERSOES`. PDF e arquivo em cache não entram na fila.
- Streaming do disco para o cliente; o arquivo nunca é carregado inteiro na memória.
- Falhas (`ProblemDetail`): arquivo não encontrado no repositório, 404 `NAO_ENCONTRADO`; credencial recusada, 502 `CREDENCIAL_ARQUIVOS_RECUSADA`; repositório fora do ar ou não configurado, 502 `REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL`; tempo limite da obtenção ou da conversão, 504 `TEMPO_LIMITE_EXCEDIDO`; senha, 422 `DOCUMENTO_PROTEGIDO_POR_SENHA`; corrompido, 422 `DOCUMENTO_CORROMPIDO`; limites (páginas, tamanho, megapixels, planilha vazia), 422 `CONVERSAO_RECUSADA`; conversor indisponível, 503 `CONVERSOR_INDISPONIVEL`. A mensagem (`detail`) é o motivo em português, sem chave, URL nem token.
- **A chave do arquivo e o token nunca aparecem** no corpo nem nos cabeçalhos; testado percorrendo as respostas (sucesso e falha).

## Dossiê

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/dossies` | Cria um pedido de geração. Retorna `202` com `{ "id": "..." }` (UUID aleatório). |
| `GET` | `/dossies/{id}` | Andamento do pedido. |
| `GET` | `/dossies/{id}/arquivo?disposicao=inline|attachment` | PDF final (capa com "N documentos incluídos de M listados" e, logo após o sumário, a seção "Documentos não incluídos" com o motivo de cada um; S2.2, docs/06).\|attachment` | PDF final. `inline` para pré-visualização (o arquivo continua), `attachment` para download (**o arquivo é apagado ao fim do download**, RNF05). `Content-Disposition` com o nome do RF18: `dossie-processo-<numero>-<ano>.pdf` (protocolado) ou `dossie-requerimento-<numero>-<ano>.pdf`. |
| `POST` | `/dossies/{id}/arquivo/ticket` | Dossiê grande (S2.2, D49): devolve `{ "ticket": "...", "expiraEm": "..." }`, com 256 bits aleatórios (base64url), de **uso único**, válido por **60 s** e preso ao pedido e ao usuário. Não é o token de sessão; o servidor guarda só o resumo SHA-256; nunca vai para log. Mesmas regras do arquivo: 404 sem acesso, 409 sem arquivo, 410 expirado. `Cache-Control: no-store`. |
| `GET` | `/dossies/{id}/arquivo?disposicao=attachment&ticket=...` | Download direto pelo navegador, em streaming e **sem cabeçalho de sessão** (o filtro de autenticação deixa passar só esta rota com `ticket`). Ticket inexistente, já usado, vencido, de outro pedido ou apresentado com a sessão de outro usuário: `403 TICKET_INVALIDO` (o ticket é consumido na primeira tentativa). `inline` com ticket: 400. Ao fim do download completo, o arquivo e o diretório do pedido são apagados (como no `attachment` comum). Resposta com `Referrer-Policy: no-referrer`; o nginx não registra essa rota no log de acesso e não a bufferiza. |
| `POST` | `/dossies/{id}/continuacao` | Decide um pedido em `PENDENTE_DECISAO` (S2.2): `{ "acao": "TENTAR_NOVAMENTE" }` refaz **só** os documentos com problema; `{ "acao": "REMOVER_E_CONTINUAR" }` os marca `REMOVIDO` e monta com os já obtidos. Nada já obtido é baixado de novo. `202`; `409 DOSSIE_SEM_DECISAO_PENDENTE` fora de `PENDENTE_DECISAO`; `400 PARAMETRO_INVALIDO` para ação desconhecida ou `REMOVER_E_CONTINUAR` sem nenhum documento obtido. Substitui os "novos `POST`" da S2. |
| `DELETE` | `/dossies/{id}` | Cancela o pedido em qualquer situação não terminal e apaga o diretório do pedido (documentos já obtidos e PDF final); idempotente, 204. |

Só quem criou o pedido (e Gestor e Administrador) acessa o `{id}`; para os demais o pedido **não existe** (404 `NAO_ENCONTRADO`).

Corpo do `POST /dossies`:

```json
{
  "idRequerimento": 12345,
  "documentos": [
    { "origem": "DOCUMENTO_PROCESSO", "id": "804328" },
    { "origem": "COMPLEMENTACAO", "id": "900118" }
  ]
}
```

Validações (nesta ordem; o cliente nunca é confiável, a lista é relida no servidor):

1. requerimento existe (404) e o usuário pode acessá-lo, RN07 (403 `NAO_INTERESSADO`);
2. lista não vazia (400 `PARAMETRO_INVALIDO`, com `campos`);
3. cada `{origem, id}` **pertence** ao requerimento (400, indicando qual);
4. documento sigiloso sem perfil: 403 `DOCUMENTO_SIGILOSO` (a mensagem não revela o título);
5. documento indisponível ou não PDF: 400 `PARAMETRO_INVALIDO`, indicando qual. Substituído em PDF é aceito.

O pedido resolve as chaves **uma vez**, guarda o instantâneo e libera a conexão do SIGA antes de baixar qualquer arquivo.

Resposta do `GET /dossies/{id}`:

```json
{
  "id": "6f0c3c0e-5f0e-4c7e-9a52-2b1f0f5d8a11",
  "status": "OBTENDO",
  "total": 55,
  "processados": 21,
  "erro": null,
  "falhas": [],
  "paginas": null,
  "expiraEm": "2026-10-19T18:30:00",
  "itens": [
    { "origem": "DOCUMENTO_PROCESSO", "id": "804328", "titulo": "RG e CPF", "situacao": "OK", "tentativas": 2 },
    { "origem": "COMPLEMENTACAO", "id": "900118", "titulo": "Resposta da complementação", "situacao": "OBTENDO", "tentativas": 0 }
  ]
}
```

- `status` (S2.2): `AGUARDANDO` (na fila), `OBTENDO`, `PENDENTE_DECISAO` (há documentos com problema; nada é entregue até a decisão), `MONTANDO`, `CONCLUIDO`, `CANCELADO` ou `EXPIRADO`. Não existe mais `FALHA` como estado final. O front para o polling em `CONCLUIDO`, `PENDENTE_DECISAO`, `CANCELADO` e `EXPIRADO`.
- `itens[].situacao` (RF16): `AGUARDANDO`, `OBTENDO`, `OK`, `FALHA` ou `REMOVIDO`. A ordem é a cronológica (RN03). `itens[].motivo` aparece em `FALHA` e continua no item `REMOVIDO` (é o motivo que o PDF registra em "Documentos não incluídos", docs/06).
- `processados`: itens resolvidos (`OK` ou `REMOVIDO`). **Não volta a zero** numa continuação: os já obtidos continuam contando.
- `falhas` (S2.2): a lista completa dos itens em `FALHA` (`origem`, `id`, `titulo`, `motivo`), que a decisão vai refazer ou remover.
- `erro`: motivo **geral**, só quando não é de um documento: credencial recusada (com `codigo`) ou falha na montagem. Falha de documento fica em `falhas`.
- `expiraEm` (S2.2): conta desde a **última atividade** (criação, cada documento processado, decisão, conclusão), inclusive enquanto o pedido espera a decisão.
- `itens[].tentativas` (S2.2): pedidos feitos à API de arquivos para o item (0 = nenhum ainda; mais de 1 = houve nova tentativa automática, docs/05). Não traz chave nem URL.
- `paginas`: total de páginas do PDF final (só em `CONCLUIDO`). `expiraEm`: horário de Cuiabá, sem fuso.
- `tamanhoBytes` e `entregaPorTicket` (S2.2, só em `CONCLUIDO`): o tamanho do PDF final e se ele passa de `DOSSIE_BLOB_MAX_MB` (padrão 150). Abaixo do limite, o front baixa e pré-visualiza por `Blob`; acima, a pré-visualização some (com aviso) e o download usa o ticket (D49).
- `GET /dossies/{id}/arquivo`: 409 `DOSSIE_SEM_ARQUIVO` se não há arquivo (em andamento, aguardando decisão, cancelado); 410 `DOSSIE_EXPIRADO` se expirou ou já foi baixado; 400 se `disposicao` for outra.

Em `PENDENTE_DECISAO` por falha de documento, `falhas` traz cada documento e o motivo, e `GET /dossies/{id}/arquivo` retorna 409 (não há arquivo):

```json
"falhas": [
  { "origem": "COMPLEMENTACAO", "id": "900118", "titulo": "Resposta da complementação", "motivo": "tempo limite excedido ao obter o arquivo" }
]
```

Quando a API de arquivos recusa a credencial (401/403), a obtenção para na hora (os já obtidos são mantidos), o pedido vai para `PENDENTE_DECISAO` e `erro` traz o código (sem o valor do token):

```json
"erro": { "origem": "DOCUMENTO_PROCESSO", "id": "804328", "titulo": "RG e CPF", "motivo": "Token da API de arquivos expirado ou recusado: capture um novo token.", "codigo": "CREDENCIAL_ARQUIVOS_RECUSADA" }
```

Motivos possíveis: tempo limite excedido ao obter o arquivo; tempo total máximo excedido ao obter o arquivo; espaço em disco insuficiente; arquivo não encontrado no repositório; repositório de arquivos indisponível; API de arquivos não configurada; o documento está protegido por senha; o arquivo está corrompido; o arquivo não tem páginas; o arquivo não é um PDF (tipo detectado: …); S3: as falhas de conversão ("documento protegido por senha", "arquivo corrompido ou ilegível pelo conversor", "tempo limite da conversão excedido", "planilha sem conteúdo imprimível", "a planilha não coube inteira no PDF: as últimas linhas não aparecem", "o PDF convertido excede o número máximo de páginas permitido", "conversor de documentos indisponível", "conversão interrompida" e as de imagem), "arquivo compactado (ZIP): não entra no dossiê" e, com a conversão desligada, "o arquivo não é um PDF (tipo detectado: planilha XLSX) e a conversão está desligada".

Falha na montagem (rara: os documentos já foram validados) também deixa o pedido em `PENDENTE_DECISAO`, com `erro.motivo` "falha ao montar o dossiê: …" e `falhas` vazia. `TENTAR_NOVAMENTE` refaz só a montagem.

Para continuar, o cliente usa `POST /dossies/{id}/continuacao` (acima), **não** um novo `POST /dossies`: com credencial nova (token por arquivo, docs/05), `TENTAR_NOVAMENTE` obtém só o que falta. **Auditoria** é da S5: nenhum evento é registrado na S2.

## Matriz de acesso por endpoint

**S4, bloco 1 (RNF10).** Inventário de **todos** os mapeamentos dos controllers, com quem pode chamar e qual regra se aplica. Todas as decisões de perfil, de vínculo com o interessado (RN07), de sigilo (RF20) e de dono do pedido saem de **um lugar**: `acesso.application.PoliticaDeAcesso` (D60). Os casos de uso só perguntam à política; os controllers não decidem acesso (`ArchitectureTest`). Os `errorCode` não mudaram.

Legenda: **Op** = Operador (`OPERADOR`), **Ge** = Gestor (`OPERADOR_SIGILO`), **Ad** = Administrador (`ADMIN`). "RN07" = Operador só como interessado: requerimento de outro interessado dá 403 `NAO_INTERESSADO`, requerimento inexistente dá 404 `NAO_ENCONTRADO`. "Sigilo" = documento sigiloso para quem não o acessa dá 403 `DOCUMENTO_SIGILOSO` (na lista, aparece mascarado). "Dono" = pedido de dossiê de outro usuário dá **404** para quem não o acessa (não revela que existe).

| Método e rota | Autenticação | Op | Ge | Ad | RN07 | Sigilo | Dono do pedido | Onde a política decide |
|---|---|:---:|:---:|:---:|---|---|---|---|
| `POST /auth/login` | pública | ✓ | ✓ | ✓ | — | — | — | — |
| `GET /auth/me` | sessão | ✓ | ✓ | ✓ | — | — | — | só o próprio usuário |
| `POST /auth/logout` | sessão | ✓ | ✓ | ✓ | — | — | — | só a própria sessão |
| `GET /busca` (por número de requerimento) | sessão | ✓ | ✓ | ✓ | 403/404 | — | — | `exigirAcessoAoRequerimento` |
| `GET /busca` (por número de processo, 1:N) | sessão | ✓ | ✓ | ✓ | filtro na consulta; nenhum dele e o número existe: 403; não existe: 404 | — | — | `restricaoDoUsuarioAtual` |
| `GET /busca` (por CPF/CNPJ e/ou nome) | sessão | ✓ | ✓ | ✓ | filtro na consulta (total e paginação já filtrados); Operador sem CPF/CNPJ: página vazia | — | — | `restricaoDoUsuarioAtual` |
| `GET /requerimentos/{id}` | sessão | ✓ | ✓ | ✓ | 403/404 | — | — | `exigirAcessoAoRequerimento`; responsável pela tarefa só para Ge e Ad (`podeVerResponsavelTarefa`, RN12) |
| `GET /requerimentos/{id}/documentos` | sessão | ✓ | ✓ | ✓ | 403/404 (antes de ler os documentos) | listados para todos; para o Op, mascarados e bloqueados; geometria sigilosa não aparece para o Op (só `totais.geometriasOmitidas`) | — | `exigirAcessoAoRequerimento`, `podeAcessarDocumento`, `mascararTitulo` |
| `GET /documentos/{origem}/{id}/conteudo?requerimento=` | sessão | ✓ | ✓ | ✓ | 403/404 do requerimento; origem e id fora do requerimento: 404 | 403 | — | `exigirAcessoAoRequerimento`, `exigirAcessoAoDocumento` (a cada requisição, mesmo com cache) |
| `GET /requerimentos/{id}/geometrias/arquivo` (S4) | sessão | ✓ | ✓ | ✓ | 403/404 (antes de ler os documentos) | sigilosa fica fora do ZIP do Op; só sigilosas: o mesmo 404 de "sem geometrias" | — | `exigirAcessoAoRequerimento`, `podeAcessarDocumento` |
| `POST /dossies` | sessão | ✓ | ✓ | ✓ | 403/404 | 403 se a seleção tiver sigiloso; documento fora do requerimento: 400 `PARAMETRO_INVALIDO` (é corpo da requisição) | o pedido nasce com o login de quem criou | `exigirAcessoAoRequerimento`, `exigirAcessoAoDocumento`; "Documentos não incluídos" com `mascararTitulo` |
| `GET /dossies/{id}` | sessão | só os seus | todos | todos | o Op perde o pedido se deixar de ser o interessado gravado nele | — | 404 | `podeAcessarPedido` |
| `GET /dossies/{id}/arquivo` (com sessão) | sessão | só os seus | todos | todos | idem | — | 404 | `podeAcessarPedido` |
| `POST /dossies/{id}/arquivo/ticket` | sessão | só os seus | todos | todos | idem | — | 404 | `podeAcessarPedido` (o ticket fica preso ao pedido e ao usuário) |
| `GET /dossies/{id}/arquivo?ticket=` | **ticket** de uso único (sem sessão) | quem recebeu o ticket | idem | idem | conferido ao emitir | — | conferido ao emitir | ticket inválido, usado, vencido, de outro pedido ou com sessão de outro usuário: 403 `TICKET_INVALIDO` |
| `POST /dossies/{id}/continuacao` | sessão | só os seus | todos | todos | idem | — | 404 | `podeAcessarPedido` |
| `DELETE /dossies/{id}` | sessão | só os seus | todos | todos | idem | — | 404 | `podeAcessarPedido` |

Fora de `/api`: `GET /actuator/health` (pública, só a situação) e a documentação OpenAPI (`/v3/api-docs`, `/swagger-ui`), desligada por padrão (`OPENAPI_ENABLED=false`) e ligada só no perfil `local`. Toda rota de `/api/**` sem o token responde 401 `NAO_AUTENTICADO`, exceto o login e o download por ticket.

As rotas de **Auditoria** e **Configurações** abaixo ainda **não existem** no código (S5). Quando forem criadas, entram nesta matriz e no `MatrizDeAcessoTest` (bloco 9 da S4), que falha para endpoint sem linha.

**Achados do inventário (bloco 1):** nenhuma rota sem verificação. Dois reforços, registrados em D60: (1) pedido de **outro usuário** passa a exigir explicitamente perfil que acessa qualquer requerimento **e** qualquer sigiloso (antes bastava "não ser restrito ao interessado", o que hoje dá no mesmo, mas não valeria para um perfil futuro restrito ou sem sigilo); (2) o **dono** Operador só acessa o pedido enquanto o interessado gravado no pedido continuar sendo o dele (antes bastava o login).

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
| 409 | `DOSSIE_SEM_ARQUIVO` | Arquivo inexistente: o pedido aguarda decisão, foi cancelado ou ainda está em andamento |
| 409 | `DOSSIE_SEM_DECISAO_PENDENTE` | Continuação pedida para um dossiê que não está em `PENDENTE_DECISAO` (S2.2) |
| 410 | `DOSSIE_EXPIRADO` | Dossiê expirado ou já baixado (o arquivo foi apagado) |
| 415 | `FORMATO_NAO_SUPORTADO` | Conteúdo do modal: tipo sem conversão para PDF ou conversão desligada (S3) |
| 422 | `DOCUMENTO_PROTEGIDO_POR_SENHA` | Conteúdo do modal: documento protegido por senha (S3) |
| 422 | `DOCUMENTO_CORROMPIDO` | Conteúdo do modal: arquivo corrompido ou ilegível pelo conversor (S3) |
| 422 | `CONVERSAO_RECUSADA` | Conteúdo do modal: conversão acima dos limites (páginas, tamanho, megapixels) ou planilha vazia (S3) |
| 429 | `MUITAS_CONVERSOES` | Fila de conversões do modal cheia (S3) |
| 413 | `GEOMETRIAS_GRANDES_DEMAIS` | Geometrias acima de `GEOMETRIAS_MAX_MB` ou de `GEOMETRIAS_MAX_ARQUIVOS` (S4) |
| 502/504 | `GEOMETRIAS_INCOMPLETAS` | Algum arquivo das geometrias falhou: nenhum ZIP; `arquivos` lista nome e motivo (504 quando todas por tempo limite; S4) |
| 403 | `TICKET_INVALIDO` | Ticket de download inexistente, já usado, vencido, de outro pedido ou de outro usuário (D49) |
| 502 | `CREDENCIAL_ARQUIVOS_RECUSADA` | API de arquivos recusou a credencial (na geração assíncrona vira `erro.codigo` do pedido) |
| 502 | `REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL` | Conteúdo do modal: API de arquivos fora do ar, não configurada ou com resposta inesperada (S3) |
| 503 | `CONVERSOR_INDISPONIVEL` | Conteúdo do modal: LibreOffice ausente ou indisponível (S3) |
| 504 | `TEMPO_LIMITE_EXCEDIDO` | Conteúdo do modal: tempo limite da obtenção ou da conversão (S3) |
| 503 | `SIGA_INDISPONIVEL` | Banco do SIGA indisponível |
| 500 | `ERRO_INTERNO` | Demais erros |

`405` (método não permitido) e `415` de corpo da requisição com tipo não suportado usam o `errorCode` `PARAMETRO_INVALIDO` com o status correspondente; um código próprio pode ser criado depois. Erros de validação trazem `campos` (`campo` e `mensagem`). Sem `SIGA_DB_URL`, ou com o SIGA fora do ar, toda consulta responde `503 SIGA_INDISPONIVEL`. Coluna inexistente ou falta de permissão no SIGA aparecem como `500 ERRO_INTERNO`, sem detalhe técnico na resposta.

Listas paginadas (busca por interessado, auditoria) usam `PageResponse<T>`.
