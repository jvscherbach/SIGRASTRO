# 04 — Modelo de dados

Somente leitura. Schemas: `SIGA2_LICENCIAMENTO` (negócio), `SIGA2_CAMUNDA` (motor de processos), `SIGA2_OUTORGA` (objeto da outorga e títulos legados) e `SIGA2_ARRECADACAO` (descrição das atividades).

## Requerimento e processo

| Tabela | Uso | Colunas principais |
|---|---|---|
| `REQUERIMENTO` | Requerimento | `ID`, `NUMERO` (`N/AAAA`), `NUMERO_PROCESSO`, `DATA_ABERTURA`, `DATA_PROTOCOLO_PROCESSO`, `DATA_ALTERACAO`, `SITUACAO_PROCESSO`, `ID_PESSOA`, `ID_TIPO_PROCESSO`, `ID_TIPO_REQUERIMENTO`, `ID_PROCESSO`, `PESO_PRIORIDADE` |
| `PROCESSO` | Histórico do que foi protocolado (o Rastro **não lê**; ver [P01](#número-de-processo-requerimentonumero_processo)) | `ID`, `NUMERO`, `NUMERO_PROTOCOLO`, `DATA_ABERTURA` |
| `PESSOA` | Interessado, representantes legais e responsáveis técnicos (S4, SCORP) e nome de reserva do responsável pela tarefa | `ID`, `ID_CORPORATIVO` (chave no SCORP), `NOME`, `CPF_CNPJ` (só dígitos, 11 ou 14) |
| `TIPO_PROCESSO` | Tipo de processo | `DESCRICAO` |
| `TIPO_REQUERIMENTO` | Tipo do requerimento | `ID`, `DESCRICAO` |
| `HISTORICO_REQUERIMENTO` | Eventos do requerimento | `ID_REQUERIMENTO`, `DATA_CADASTRO`, `EVENTO` |
| `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO` | Prioridade de atendimento | ver [P01](#mapeamento-de-objeto-e-prioridade-p01) |
| `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO` | Objeto do licenciamento | ver [P01](#mapeamento-de-objeto-e-prioridade-p01) |
| `SERVIDOR` | Nome do responsável pela tarefa (cache do SCORP) | `ID`, `CPF`, `NOME`, `DATA_ATUALIZACAO` |

Observações:

- `REQUERIMENTO.NUMERO_PROCESSO` **não é único** (1:N): a busca por número de processo pode devolver mais de um requerimento. A chave é sempre `REQUERIMENTO.ID`. Detalhes na seção P01.
- `SITUACAO_PROCESSO` guarda o nome do enum (ex.: `EM_ANALISE`). A descrição para exibição é mapeada no back-end (tabela em [Apêndice A](#apêndice-a--situações)).
- Objeto (objetivo, finalidade, atividade, Tipo/Objetivo), prioridade e tipo do requerimento: mapeados na [seção P01](#mapeamento-de-objeto-e-prioridade-p01). `REQUERIMENTO.PESO_PRIORIDADE` **não** é usado.

## Documentos

**S2.1:** a lista é a da seção "Arquivos do Processo" da Vista do SIGA: **seis tabelas**, com as colunas confirmadas no banco. Nenhuma origem entra sem prova de que a Vista a lista. A S2 tinha assumido colunas por padrão de nome e ampliado as origens por suposição (o teste de fumaça falhou com `ORA-00904: "A"."NOME_ARQUIVO"` em `COMPLEMENTACAO_ITEM_ARQUIVO`).

**Uma consulta por origem**, cada uma em `resources/sql/siga/documentos/<origem>.sql` (D33), com parâmetro nomeado `:idRequerimento`, sem `SELECT *`, compatível com 19c e **sem filtro de vigência, de grupo ou de chave** (a Vista não filtra). Todas devolvem as mesmas colunas:

`ORIGEM, ID, ID_REQUERIMENTO, CHAVE, TITULO, NOME_ARQUIVO, TIPO, DATA_DOC, DATA_FIM, SIGILO`

A situação é calculada pelo domínio (RN01). A chave única do documento é **`(ORIGEM, ID)`**: `ID` isolado repete entre origens; no contrato HTTP `origem` e `id` seguem separados.

| Origem (código) | Arquivo `.sql` | Tabelas e junção a partir de `REQUERIMENTO.ID` | Chave do arquivo | Título / nome | Data | Vigência | Sigilo (`NUMBER` 0/1) |
|---|---|---|---|---|---|---|---|
| `DOCUMENTO_PROCESSO` | `documento-processo` | `DOCUMENTO_PROCESSO.ID_REQUERIMENTO` | `CHAVE` | `TITULO` / `NOME_ARQUIVO` | `DATA_CRIACAO` | `DATA_FIM` | `SIGILOSO` |
| `DOCUMENTO_REQUERIMENTO` | `documento-requerimento` | `DOCUMENTO_REQUERIMENTO.ID_REQUERIMENTO` | `CHAVE` | `TITULO` / `NOME_ARQUIVO` | `DATA_UPLOAD` | `DATA_FIM` | `SIGILOSO` |
| `COMPLEMENTACAO` | `complementacao` | `COMPLEMENTACAO.ID_REQUERIMENTO` → `COMPLEMENTACAO_ITEM.ID_COMPLEMENTACAO` → `COMPLEMENTACAO_ITEM_ARQUIVO.ID_COMPLEMENTACAO_ITEM` (inclui arquivos de resposta, `ID_COMP_ITEM_RESPOSTA` preenchido) | `CHAVE_PANDORA` | `TITULO` nos dois (não existe `NOME_ARQUIVO`) | `DATA_INICIO` | `DATA_FIM` | `SIGILOSO` |
| `IMOVEL` | `imovel` | `IMOVEL.ID_REQUERIMENTO` (**a confirmar no dicionário**) → `DOCUMENTO_IMOVEL.ID_IMOVEL` (todos os imóveis) | `CHAVE` | `TITULO` / `NOME_ARQUIVO` | `DATA_INICIO` | `DATA_FIM` | `SIGILOSO` |
| `REPRESENTANTE_LEGAL` | `representante-legal` | `REQUERIMENTO_REPRESENTANTE_LEGAL.ID_REQUERIMENTO` → `REQ_REP_LEGAL_DOCUMENTO.ID_REQUERIMENTO_REP_LEGAL` | `CHAVE` | `NOME_ARQUIVO` nos dois | `DATA_INICIO` | `DATA_FIM` | `SIGILO` |
| `RESPONSAVEL_TECNICO` | `responsavel-tecnico` | `REQUERIMENTO_RESPONSAVEL_TECNICO.ID_REQUERIMENTO` → `REQ_RESP_TECNICO_DOCUMENTO.ID_REQUERIMENTO_RESP_TECNICO` (não filtra `HABILITADO`, como a Vista) | `CHAVE` | `NOME_ARQUIVO` nos dois | `DATA_INICIO` | `DATA_FIM` | `SIGILO` |
| `SCORP` | `pessoas-do-requerimento` (pessoas) + API | Pessoas do requerimento (abaixo) → API do SCORP por `ID_CORPORATIVO` (docs/05) | `chave` | `descricao` / `nomeArquivo` | `dataInicio` | `dataFim` | não informado: `SCORP_SIGILO_PADRAO` (P30) |
| `DAR` | desativada | Arrecadação | **NÃO CONFIRMADO** | — | — | — | — |

Colunas auxiliares usadas: `DOCUMENTO_PROCESSO.GRUPO_DOCUMENTO` (tipo), `DOCUMENTO_REQUERIMENTO.TIPO`, `REQ_REP_LEGAL_DOCUMENTO.TIPO`, `REQ_RESP_TECNICO_DOCUMENTO.TIPO` e, na complementação, `RESPOSTA` ou `ITEM` pela presença de `ID_COMP_ITEM_RESPOSTA`. Todas as colunas acima foram confirmadas no banco, **exceto `IMOVEL.ID_REQUERIMENTO`**, que a Vista usa mas ainda não foi conferida no dicionário (passo do [roteiro da S2](homologacao/S2-roteiro-homologacao.md)). `SIGILOSO` e `SIGILO` são `NUMBER(1)` com valores só 0 e 1, conferido no SIGA em 22/10/2026 (P05 resolvida)).

**Escopo:** só o requerimento (D31); nada por `ID_PROCESSO`. O PDF do parecer, os títulos e o arquivo da juntada já entram via `DOCUMENTO_PROCESSO` (grupos `PARECER_TECNICO`, `EMISSAO_*`, `JUNTADA_DE_PROCESSO`).

### Pessoas do requerimento para o SCORP (S4)

`resources/sql/siga/documentos/pessoas-do-requerimento.sql`, parâmetro `:idRequerimento`, só `SELECT`. Devolve `PAPEL` (`INTERESSADO`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`) e `ID_CORPORATIVO`, distintos por papel (`UNION`), sem `ID_CORPORATIVO` nulo. A mesma pessoa em vários papéis vem em várias linhas; o domínio faz **uma** chamada ao SCORP por pessoa e usa o papel de maior precedência (interessado, representante, técnico).

| Papel | Junção a partir do requerimento |
|---|---|
| `INTERESSADO` | `REQUERIMENTO.ID_PESSOA` → `PESSOA.ID` |
| `REPRESENTANTE_LEGAL` | `REQUERIMENTO_REPRESENTANTE_LEGAL.ID_REQUERIMENTO`, `.ID_PESSOA` → `PESSOA.ID` |
| `RESPONSAVEL_TECNICO` | `REQUERIMENTO_RESPONSAVEL_TECNICO.ID_REQUERIMENTO`, `.ID_PESSOA` → `PESSOA.ID` (não filtra `HABILITADO`, como a origem `RESPONSAVEL_TECNICO`) |

**Colunas conferidas no banco pelo responsável (P31, `ALL_TAB_COLUMNS` e `ALL_CONSTRAINTS`):** `REQUERIMENTO_REPRESENTANTE_LEGAL` tem `ID`, `DATA_VINCULO` (`TIMESTAMP(6)`), `ID_PESSOA` e `ID_REQUERIMENTO` (`NUMBER`, todas `NOT NULL`); `REQUERIMENTO_RESPONSAVEL_TECNICO` tem `ID`, `CADASTRO_TEC_FEDERAL` (`VARCHAR2`, nulo), `CONSELHO`, `DATA_VINCULO`, `HABILITADO` (`NUMBER`), `REGISTRO_CONSELHO`, `ID_PESSOA` e `ID_REQUERIMENTO` (`NOT NULL`). Nas duas, `ID_PESSOA` tem chave estrangeira para `PESSOA` e `ID_REQUERIMENTO` para `REQUERIMENTO`. O tipo de `PESSOA.ID_CORPORATIVO` não foi conferido: é lido como texto. **Nenhum grant novo:** as quatro tabelas já estão na lista do usuário de leitura. O resultado esperado no banco real está no roteiro de homologação da S4.

### Origens não listadas pela Vista (avaliar após a homologação)

Removidas na S2.1 (SQL, mapeamento, propriedades, testes e grants). Só voltam com prova de que a Vista as lista ou com decisão do responsável (P04):

| Tabela | Observação |
|---|---|
| `PARECER_TECNICO_ANEXO` | anexos do parecer (via `PARECER_TECNICO`) |
| `PRORROG_PRAZO_COMPLEM_ANEXO` | anexo do pedido de prorrogação da complementação |
| `TITULACAO_DOCUMENTO` | documento da titulação |
| `JUNTADA_PROCESSO` | a chave dela **difere** da chave do `DOCUMENTO_PROCESSO` correspondente (não deduplicaria) |
| `EXTRATO_PUBLICACAO` | colunas conferidas em 22/10/2026 (`ID`, `CHAVE`, `DATA_CRIACAO`, `DATA_FIM`, `DATA_LIMITE`, `NUMERO`, `ID_SERVIDOR`, `TIPO_TITULO`; a única chave estrangeira é `SERVIDOR`, **sem `ID_REQUERIMENTO`**); liga-se por `EXTRATO_EMISSAO_TITULO` → `EMISSAO_TITULO` → `REQUERIMENTO_TITULACAO`; **um extrato serve vários requerimentos** |
| `PESSOA_DOCUMENTO_DOENCA_GRAVE` | **nunca** (RN02): nenhuma consulta a lê |

**Sem chave armazenada:** a cópia do requerimento em PDF só existe se houver `DOCUMENTO_PROCESSO` com `GRUPO_DOCUMENTO = 'COPIA_PROCESSO'`. O protótipo usa a última cópia salva e não tenta regenerar. `SIGA2_OUTORGA.TITULO_LEGADO_ANEXO` referencia `DOCUMENTO_PROCESSO`, já coberto (não é lida). A Vista não usa a data de assinatura: `DOCUMENTO_PROCESSO_SIGNATARIO` **deixou de ser lida**.

**Metadados ausentes no banco:** tipo MIME, extensão real e tamanho só são conhecidos ao obter o arquivo. A extensão do `NOME_ARQUIVO` (ou do `TITULO`, na complementação) é a indicação prévia; ao baixar, o dossiê confere a assinatura `%PDF-`.

**Regras do domínio (`documento.domain`):**

- *Título exibido:* `TITULO`; senão `NOME_ARQUIVO`; senão `"<descrição da origem> <id>"`. `NOME_ARQUIVO` nulo ou em branco (imóvel, documento do processo) nunca derruba a listagem.
- *Data:* a da própria origem (tabela acima); a regra "maior data de assinatura" foi removida. `DATA_DOC` nula não derruba nada: o documento vai ao final (RN03).
- *Situação (RN01):* `INDISPONIVEL` se a chave for nula, vazia ou `"0"` (precedência); `SUBSTITUIDO` se `DATA_FIM` estiver preenchida; senão `VIGENTE`. A Vista exibe esses documentos com a data em "Sem efeito"; o Rastro também os lista.
- *Sigilo (RN06):* as seis colunas são `NUMBER` (0/1); normalização tolerante e "na dúvida, sigiloso": número diferente de 0 e `S`, `SIM`, `Y`, `YES`, `T`, `TRUE`, `1` são sigilosos; nulo, 0 e `N`, `NAO`, `NÃO`, `NO`, `F`, `FALSE`, `0` não são; qualquer outro valor não nulo é sigiloso (R18). **Divergência consciente da Vista:** em representante legal e responsável técnico o Rastro usa a coluna real `SIGILO`, **mesmo que a Vista do SIGA a ignore e mostre sempre "Não"**; a nossa regra é mais restritiva, de propósito (D43, `docs/09`).
- *Deduplicação (RN05):* mesma chave válida (diferente de `"0"`) em mais de uma origem aparece uma vez; vence a primeira origem na ordem da tabela acima. **Divergência da Vista**, que não deduplica: aqui a deduplicação evita repetir páginas no dossiê. Os documentos de representante legal e responsável técnico não duplicam por construção do SQL (uma linha por documento).
- *Ordem (RN03):* data crescente; sem data ao final; empate por origem (ordem da tabela) e id (numérico).
- *Tipo de arquivo pela extensão:* PDF; PLANILHA (xls, xlsx, ods); TEXTO (doc, docx, odt, rtf, html, txt); IMAGEM (png, jpg, jpeg); GEOMETRIA (shp, kml, kmz); COMPACTADO (zip, rar, 7z). **Sem extensão ou extensão não reconhecida: `DESCONHECIDO`** (o antigo `OUTRO` deixou de existir). **Compactados nunca aparecem (RN11); geometrias saem da lista de documentos e vêm à parte, em `geometrias`, com download em ZIP (RN10, S4).**
- *Bloqueio:* `INDISPONIVEL` > `SIGILOSO` (sem perfil) > `NAO_PDF`. `DESCONHECIDO` **não** recebe `NAO_PDF`: é selecionável (se vigente, chave válida e acessível pelo perfil).
- *Seleção inicial:* a Vista não pré-seleciona nada; desde a S2.2 o Rastro seleciona **todo documento selecionável** (sem bloqueio: vigente ou sem efeito, inclusive `DESCONHECIDO`), RN13 e D47.
- *Tipo desconhecido ao baixar:* o validador do dossiê confere a assinatura `%PDF-`. Se for PDF, segue normal; se não, falha **do documento** com "o arquivo não é um PDF (tipo detectado: X)" e vale a RN09. Desde a S3, com a conversão ligada, o tipo real (planilha, texto, HTML, imagem) é convertido para PDF em vez de falhar; só o compactado e o tipo irreconhecível continuam falhando (docs/06). O mesmo vale para documento marcado como PDF cujo conteúdo não é PDF. O tipo detectado vem dos primeiros bytes; o `Content-Type` do repositório não chega à porta de arquivos e não é usado (não é confiável, RNF09).

## Camunda

| Tabela | Uso |
|---|---|
| `ACT_RU_EXECUTION` | Instância ativa. `BUSINESS_KEY_` = `REQUERIMENTO.ID` (texto), na execução raiz |
| `ACT_RU_TASK` | Tarefa ativa: `NAME_`, `TASK_DEF_KEY_`, `ASSIGNEE_`, `CREATE_TIME_` |
| `ACT_RE_PROCDEF` | Definição do processo: `KEY_`, `NAME_` |
| `ACT_HI_PROCINST`, `ACT_HI_TASKINST` | Histórico (uso futuro) |

## Consultas de referência

Validar os nomes de colunas no banco antes de usar.

### Requerimento por número

```sql
SELECT r.ID, r.NUMERO, r.NUMERO_PROCESSO, r.DATA_ABERTURA, r.DATA_ALTERACAO,
       r.SITUACAO_PROCESSO, tp.DESCRICAO AS TIPO_PROCESSO,
       p.NOME AS INTERESSADO, p.CPF_CNPJ, p.ID_CORPORATIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
JOIN   SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p   ON p.ID  = r.ID_PESSOA
WHERE  r.NUMERO = :numero;
```

Para processo, troque o filtro por `r.NUMERO_PROCESSO = :numero`. O resultado pode ter mais de um requerimento (1:N); ordene por `r.DATA_ABERTURA DESC, r.ID DESC`.

### Tarefa atual

```sql
SELECT t.NAME_, t.TASK_DEF_KEY_, t.ASSIGNEE_, t.CREATE_TIME_, d.NAME_ AS PROCESSO
FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t    ON t.PROC_INST_ID_ = e.PROC_INST_ID_
JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d ON d.ID_ = t.PROC_DEF_ID_
WHERE  e.PARENT_ID_ IS NULL
  AND  e.ID_ = e.PROC_INST_ID_
  AND  e.BUSINESS_KEY_ = TO_CHAR(:idRequerimento);
```

Sem linhas significa que a instância está encerrada ou parada em gateway/evento. A versão implementada (`tarefa-atual.sql`) não seleciona `ASSIGNEE_`: devolve só a situação e o nome do responsável (seção P01).

### Documentos

Ver a seção "Documentos" no início deste documento: uma consulta por origem, em arquivo próprio (D33), nas seis origens da Vista (S2.1). O modelo das demais é `documento-processo.sql`.

## Leitura implementada na S1 e na S1.1 (inventário)

Tudo que o `integration.siga` lê hoje. É a base da lista de grants do usuário de leitura e dos roteiros de homologação ([`S1`](homologacao/S1-roteiro-homologacao.md), [`S1.1`](homologacao/S1.1-roteiro-homologacao.md)). Os nomes da S1.1 vêm da seção P01; **os tipos das colunas foram conferidos pelo responsável em 21/10/2026**: `ACT_RU_TASK.ASSIGNEE_` é `NVARCHAR2(510)`, `PESSOA.CPF_CNPJ` é `VARCHAR2(20)` e `SERVIDOR.CPF` é `VARCHAR2(11)` (o recorte de teste usa hipóteses para as demais). Nenhuma consulta lê a tabela `PROCESSO`.

| Arquivo (`resources/sql/siga/`) | Tabelas | Colunas lidas |
|---|---|---|
| `requerimento-por-numero.sql`, `requerimento-por-id.sql`, `requerimentos-por-interessado.sql`, `requerimentos-contagem.sql` | `SIGA2_LICENCIAMENTO.REQUERIMENTO` | `ID`, `NUMERO`, `NUMERO_PROCESSO`, `DATA_ABERTURA`, `DATA_ALTERACAO`, `SITUACAO_PROCESSO`, `ID_PESSOA`, `ID_TIPO_PROCESSO`, `ID_TIPO_REQUERIMENTO` (só por id e por número) |
| idem | `SIGA2_LICENCIAMENTO.PESSOA` (LEFT JOIN: interessado pode ser nulo) | `ID`, `NOME`, `CPF_CNPJ` |
| idem (não usada na contagem) | `SIGA2_LICENCIAMENTO.TIPO_PROCESSO` (LEFT JOIN) | `ID`, `DESCRICAO` |
| `requerimento-por-numero.sql`, `requerimento-por-id.sql` | `SIGA2_LICENCIAMENTO.TIPO_REQUERIMENTO` (LEFT JOIN) | `ID`, `DESCRICAO` |
| `objetivos-outorga.sql` | `SIGA2_OUTORGA.REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA` | `REQUERIMENTO_OUTORGA.ID`, `ID_REQUERIMENTO`; `OBJETIVO_REQUERIMENTO_OUTORGA.ID`, `ID_REQUERIMENTO_OUTORGA`, `ID_OBJETIVO_OUTORGA` (**NÃO CONFIRMADO**), `DATA_FIM`; `OBJETIVO_OUTORGA.ID`, `DESCRICAO`, `TIPO_OUTORGA` |
| `finalidades-atividades-outorga.sql` | `SIGA2_OUTORGA.REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE`; `SIGA2_ARRECADACAO.ATIVIDADE` (LEFT JOIN) | `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID`, `ID_REQUERIMENTO`, `ID_FINALIDADE_ATIVIDADE` (**NÃO CONFIRMADO**); `FINALIDADE_ATIVIDADE.ID`, `ID_FINALIDADE` (**NÃO CONFIRMADO**), `ID_ATIVIDADE_ARRECADACAO`; `FINALIDADE.ID`, `DESCRICAO`; `ATIVIDADE.ID`, `DESCRICAO` |
| `objeto-licenciamento.sql` | `SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO` (LEFT JOIN) | `REQUERIMENTO_LICENCIAMENTO.ID`, `ID_REQUERIMENTO`, `DESCRICAO_ATIVIDADE`, `DESCRICAO_GRUPO_ATIVIDADE`; `OBJETIVO_LICENCIAMENTO.ID`, `ID_REQUERIMENTO_LICENCIAMENTO`, `DESCRICAO_OBJETIVO_LICENCIAMENTO` |
| `prioridade.sql` | `SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO` | `PRIORIDADE_PROCESSO.ID`, `ID_REQUERIMENTO`; `PRIORIDADE_PROCESSO_CRITERIO.ID_PRIORIDADE_PROCESSO`, `ID_TIPO_PRIORIDADE_PROCESSO` |
| `tarefa-atual.sql` | `SIGA2_CAMUNDA.ACT_RU_EXECUTION` | `ID_`, `PROC_INST_ID_`, `PARENT_ID_`, `BUSINESS_KEY_` |
| idem | `SIGA2_CAMUNDA.ACT_RU_TASK` | `PROC_INST_ID_`, `PROC_DEF_ID_`, `NAME_`, `ASSIGNEE_` (só dentro do SQL; não é selecionado), `CREATE_TIME_` |
| idem | `SIGA2_CAMUNDA.ACT_RE_PROCDEF` | `ID_`, `NAME_` |
| idem | `SIGA2_LICENCIAMENTO.SERVIDOR`, `PESSOA` | `SERVIDOR.ID`, `CPF`, `NOME`, `DATA_ATUALIZACAO`; `PESSOA.ID`, `CPF_CNPJ`, `NOME` |

As três colunas de ligação foram **confirmadas em `ALL_TAB_COLUMNS`** pelo responsável em 21/10/2026 (`FINALIDADE_ATIVIDADE.ID_FINALIDADE`, `OBJETIVO_REQUERIMENTO_OUTORGA.ID_OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_FINALIDADE_ATIVIDADE`). Índices: existem `IDX_PRIORIDADE_PROC_REQ` (`PRIORIDADE_PROCESSO.ID_REQUERIMENTO`) e as chaves únicas de `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` e `REQUERIMENTO_OUTORGA.ID_REQUERIMENTO`; **não** há índice em `PESSOA.CPF_CNPJ`, `SERVIDOR.CPF` e `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_REQUERIMENTO` (R17).

Comportamento das consultas:

- **Por número de requerimento:** `REQUERIMENTO.NUMERO = :numero`, exato. Mais de uma linha é anomalia: usa-se a mais recente (maior `ID`) e registra-se aviso no log.
- **Por número de processo:** `REQUERIMENTO.NUMERO_PROCESSO = :numero` pela mesma consulta paginada da lista: devolve **todos** os requerimentos daquele número (1:N), com a restrição do interessado (RN07) no SQL. Para o Operador, se nenhum é dele, o caso de uso confere se o número existe (mesma consulta, sem restrição, 1 linha) para decidir entre 403 e 404.
- **Lista** (`CPF/CNPJ`, nome, número): predicados opcionais acrescentados pelo adapter ao `WHERE`: CPF/CNPJ por comparação direta (`p.CPF_CNPJ = :cpfCnpj`, só dígitos; o domínio normaliza a entrada), nome por `UPPER(p.NOME) LIKE :nome ESCAPE '\'` (com `%`, `_` e `\` digitados escapados) e, para perfis restritos ao interessado (RN07), `p.CPF_CNPJ = :restricao`. Ordem: `DATA_ABERTURA DESC, ID DESC`; paginação `OFFSET/FETCH`; total por `COUNT(*)` com os mesmos filtros. **Risco:** o `LIKE` com `UPPER` e a busca por `NUMERO_PROCESSO` (sem índice) não usam índice; ver R09 e R14.
- **Tarefa atual:** consulta de referência acima, acrescida de `ORDER BY t.CREATE_TIME_ DESC FETCH FIRST 1 ROWS ONLY` e da resolução do nome do responsável (seção P01). Sem linhas = `SEM_TAREFA`; qualquer falha de leitura = `INDISPONIVEL`.
- **Objeto e prioridade:** uma consulta por bloco, sem junção entre os blocos. Falha de leitura derruba o detalhe (503 `SIGA_INDISPONIVEL` ou 500), sem estado parcial.
- Situação: `SITUACAO_PROCESSO` é traduzida pelo Apêndice A; valor fora da lista aparece com o texto cru.

## Mapeamento de objeto e prioridade (P01)

> Origem: análise do código do SIGA Licenciamento (front `cabecalho-requerimento`, back `RequerimentoOutorgaService`, `PrioridadeProcessoService`, `ServidorService`, entidades JPA) e conferência no banco pelo responsável em 21/10/2026. Os nomes de colunas foram confirmados em `ALL_TAB_COLUMNS`. O usuário de leitura do Rastro tem `SELECT` em todas as tabelas abaixo. A tela atual foi comparada campo a campo com o processo 6023/2025 (outorga), e todos os campos bateram.

### Campos

| Campo da tela | Origem | Junção a partir de `REQUERIMENTO.ID` | Cardinalidade | Regra |
|---|---|---|---|---|
| Objetivo (outorga) | `SIGA2_OUTORGA.OBJETIVO_OUTORGA.DESCRICAO` | `REQUERIMENTO_OUTORGA.ID_REQUERIMENTO` → `OBJETIVO_REQUERIMENTO_OUTORGA.ID_REQUERIMENTO_OUTORGA` → `OBJETIVO_OUTORGA.ID` | 1:N | Só objetivos vigentes: `OBJETIVO_REQUERIMENTO_OUTORGA.DATA_FIM IS NULL OR DATA_FIM > SYSTIMESTAMP` |
| Tipo (bloco Tipo/Objetivo) | `SIGA2_OUTORGA.OBJETIVO_OUTORGA.TIPO_OUTORGA` | igual ao Objetivo | 1:N | Valores `SUBTERRANEA`, `SUPERFICIAL`, `BARRAMENTO`. O Rastro exibe "Subterrânea", "Superficial" e "Barramento"; valor desconhecido aparece cru |
| Finalidade (outorga) | `SIGA2_OUTORGA.FINALIDADE.DESCRICAO` | `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_REQUERIMENTO` → `FINALIDADE_ATIVIDADE.ID` → `FINALIDADE.ID` | 1:N pares finalidade/atividade | Remoção física no SIGA (sem vigência) |
| Atividade (outorga) | `SIGA2_ARRECADACAO.ATIVIDADE.DESCRICAO` | mesma cadeia da Finalidade, por `FINALIDADE_ATIVIDADE.ID_ATIVIDADE_ARRECADACAO = ATIVIDADE.ID` | 1 por par | O SIGA busca pela API da Arrecadação; o Rastro lê a tabela diretamente (mesmo texto, conferido no processo 6023/2025, atividade 4054). `LEFT JOIN`: sem linha, "—" |
| Objetivo (licenciamento) | `SIGA2_LICENCIAMENTO.OBJETIVO_LICENCIAMENTO.DESCRICAO_OBJETIVO_LICENCIAMENTO` | `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` → `OBJETIVO_LICENCIAMENTO.ID_REQUERIMENTO_LICENCIAMENTO` | 1:N | Sem vigência |
| Atividade (licenciamento) | `SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO.DESCRICAO_ATIVIDADE` (e `DESCRICAO_GRUPO_ATIVIDADE`) | `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` | 1:1 | — |
| Prioridade de atendimento | `SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO_CRITERIO` | `PRIORIDADE_PROCESSO.ID_REQUERIMENTO` → `PRIORIDADE_PROCESSO_CRITERIO.ID_PRIORIDADE_PROCESSO` | 1:N critérios | Pelo menos 1 critério: "Atendimento prioritário"; nenhum: "Atendimento normal". **Não usar `REQUERIMENTO.PESO_PRIORIDADE`** (o cadastro manual de critérios não o atualiza) |
| Tipo do requerimento | `SIGA2_LICENCIAMENTO.TIPO_REQUERIMENTO.DESCRICAO` | `REQUERIMENTO.ID_TIPO_REQUERIMENTO` (NOT NULL) | N:1 | Texto de cadastro, exibido mesmo se o tipo estiver inativo |

**Regras de apresentação:**
- Objetivos e pares Finalidade/Atividade são **listas independentes**, como na tela do SIGA. Não existe pareamento objetivo↔finalidade, e nunca se faz JOIN entre as duas listas.
- Processos de outorga têm linhas em `REQUERIMENTO_OUTORGA` e os de licenciamento, em `REQUERIMENTO_LICENCIAMENTO`. O Rastro consulta os dois blocos e mostra o que tiver dados; sem dados, "—". O bloco Tipo/Objetivo só aparece quando há objetivos de outorga.

### Critérios de prioridade (`PRIORIDADE_PROCESSO_CRITERIO.ID_TIPO_PRIORIDADE_PROCESSO`)

Descrições do enum `EnumTipoPrioridadeProcesso` do código do SIGA. O domínio oficial fica em outro serviço; a equivalência é provável, mas **NÃO CONFIRMADA**. Código desconhecido aparece como "Critério N".

| Código | Descrição |
|---|---|
| 1 | Decisão Administrativa |
| 2 | Pessoa com idade igual ou superior a 80 anos |
| 3 | Pessoa com idade igual ou superior a 60 anos |
| 4 | Pessoa com deficiência física ou mental |
| 5 | Pessoa com doença grave |
| 6 | Trata de embargo ou suspensão |
| 7 | Pré-requisito para licenciamento |
| 8 | Utilidade pública |
| 9 | Risco de grave prejuízo |

### Responsável pela tarefa

- `SIGA2_CAMUNDA.ACT_RU_TASK.ASSIGNEE_` guarda o CPF do servidor, só com 11 dígitos. Formatos encontrados em 21/10/2026: 7.745 tarefas com CPF de 11 dígitos, 39.374 nulas e 5 em outro formato (`demo` e `admin`, do console do motor de fluxo, sem uso; e uma mensagem de erro gravada no campo).
- **Nome:** `SIGA2_LICENCIAMENTO.SERVIDOR.NOME`, versão mais recente por CPF (`ORDER BY DATA_ATUALIZACAO DESC, ID DESC`); se não houver, `SIGA2_LICENCIAMENTO.PESSOA.NOME` por `CPF_CNPJ`, a mais recente por `ID`. A `SERVIDOR` é um cache do SCORP versionado por inserção, e o nome muda entre versões (casamento, correção de grafia).
- Cobertura em 21/10/2026, entre tarefas com responsável: 1.561 com nome nas duas tabelas, 5.597 só em `PESSOA`, 1 só em `SERVIDOR` e 591 em nenhuma.
- Exibição:
  - `ASSIGNEE_` nulo ou vazio: "Não atribuída";
  - CPF de 11 dígitos com nome encontrado: o nome;
  - CPF sem nome, ou qualquer outro formato: "Responsável não identificado".
  - O CPF do servidor **nunca** sai na API nem aparece na tela.
- Visibilidade (regra do Rastro; o SIGA não tem regra própria): o nome só aparece para **Gestor** (`OPERADOR_SIGILO`) e **Administrador**. O Operador não recebe o campo.

### Número de processo (`REQUERIMENTO.NUMERO_PROCESSO`)

- Não é único: não há constraint nem índice em `NUMERO_PROCESSO` (conferido em `ALL_IND_COLUMNS`), e o código do SIGA não impede repetição. O SIGA também tem um fluxo que copia o número de processo legado para um segundo requerimento.
- A tabela `PROCESSO` é um histórico do que foi protocolado, usado pelo SIGA para abrir alteração, renovação, cancelamento ou transferência sobre título emitido. O Rastro **não a consulta** e não filtra por origem: só a `REQUERIMENTO` importa (decisão do responsável).
- Em 21/10/2026 havia 1 número repetido, `445/2022`, nos requerimentos 3391/2022 (concluído, protocolado) e 3798/2023 (cancelado pelo cadastrante, sem data de protocolo), ambos no mesmo `ID_PROCESSO` e de origem SIGA. É anomalia de dado, a comunicar à equipe do SIGA.
- Regra do Rastro: a busca por número de processo trata o resultado como **1:N**. Com 1 resultado, abre o detalhe; com mais de 1, lista todos para o usuário escolher. A chave é sempre `REQUERIMENTO.ID`.
- Sem índice em `NUMERO_PROCESSO`, a busca por número de processo lê a tabela inteira, cerca de 63 mil requerimentos (risco de desempenho baixo, registrado em `docs/10`).

### Busca por CPF/CNPJ

`PESSOA.CPF_CNPJ` guarda só dígitos (11 ou 14). A busca compara direto (`p.CPF_CNPJ = :digitos`), sem `REGEXP_REPLACE`.

### Tabelas do SIGA lidas pelo Rastro (para o grant do usuário de leitura)

`SIGA2_LICENCIAMENTO`: `REQUERIMENTO`, `PESSOA`, `TIPO_PROCESSO`, `TIPO_REQUERIMENTO`, `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO`, `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO`, `SERVIDOR`.
`SIGA2_OUTORGA`: `REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE`.
`SIGA2_ARRECADACAO`: `ATIVIDADE`.
`SIGA2_CAMUNDA`: as tabelas já usadas pela tarefa atual.

### Ainda NÃO CONFIRMADO

- Descrições oficiais dos critérios de prioridade (domínio fora do banco do SIGA Licenciamento).
- Conferência na tela de um processo de **licenciamento** e de um processo **com critério de prioridade** (pendente no roteiro de homologação).
- Tarefas atribuídas a grupo (`ASSIGNEE_` nulo com candidatos): hoje aparecem como "Não atribuída".

## Privilégios do usuário de leitura (`VISTAS_LEITURA_SIGA`)

Somente `CREATE SESSION` e `SELECT` em (na versão definitiva, preferir views `VW_` criadas pelo DBA):

- `SIGA2_LICENCIAMENTO`: `REQUERIMENTO`, `PESSOA`, `TIPO_PROCESSO`, `TIPO_REQUERIMENTO`, `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO`, `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO`, `SERVIDOR`, `HISTORICO_REQUERIMENTO`, `REQUERIMENTO_REPRESENTANTE_LEGAL`, `REQUERIMENTO_RESPONSAVEL_TECNICO`, `IMOVEL`, `COMPLEMENTACAO`, `COMPLEMENTACAO_ITEM`, `PARECER_TECNICO`, as tabelas de documentos acima e `DOCUMENTO_PROCESSO_SIGNATARIO`. `PROCESSO` não é necessária (P03).
- `SIGA2_OUTORGA`: `REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE` (e `TITULO_LEGADO_ANEXO` na S2, se for preciso).
- `SIGA2_ARRECADACAO`: `ATIVIDADE`.
- `SIGA2_CAMUNDA`: `ACT_RU_EXECUTION`, `ACT_RU_TASK`, `ACT_RE_PROCDEF`, `ACT_HI_PROCINST`, `ACT_HI_TASKINST`.

**Grants da S2.1:** `SELECT` nas 11 tabelas lidas pelas seis origens, no script do [roteiro da S2](homologacao/S2-roteiro-homologacao.md): `DOCUMENTO_PROCESSO`, `DOCUMENTO_REQUERIMENTO`, `COMPLEMENTACAO`, `COMPLEMENTACAO_ITEM`, `COMPLEMENTACAO_ITEM_ARQUIVO`, `IMOVEL`, `DOCUMENTO_IMOVEL`, `REQUERIMENTO_REPRESENTANTE_LEGAL`, `REQ_REP_LEGAL_DOCUMENTO`, `REQUERIMENTO_RESPONSAVEL_TECNICO`, `REQ_RESP_TECNICO_DOCUMENTO`.

**Grants necessários até a S1.1** (só estes): `CREATE SESSION` e `SELECT` nas tabelas da lista "Tabelas do SIGA lidas pelo Rastro" (seção P01), mais `SIGA2_CAMUNDA.ACT_RU_EXECUTION`, `ACT_RU_TASK` e `ACT_RE_PROCDEF`. Os testes de integração criam um usuário com exatamente esses grants. O script de referência para o DBA está no [roteiro da S1.1](homologacao/S1.1-roteiro-homologacao.md).

## Apêndice A — Situações

| Valor | Descrição |
|---|---|
| `EM_CADASTRO` | Em cadastro |
| `EM_CORRECAO_CADASTRO` | Em correção de cadastro |
| `AGUARDANDO_CONFERENCIA` | Aguardando conferência |
| `EM_CONFERENCIA` | Em conferência |
| `CHECKLIST_REPROVADO` | Checklist reprovado |
| `ENCAMINHADO_PARA_ARRECADACAO` | Encaminhado para arrecadação |
| `CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE` | Cancelado por solicitação do cadastrante |
| `CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO` | Cancelado por expiração de prazo de cadastro |
| `CANCELADO_POR_FALTA_DE_PAGAMENTO` | Cancelado por falta de pagamento |
| `AGUARDANDO_EMISSAO_DE_TAXAS` | Aguardando emissão de taxas |
| `AGUARDANDO_PAGAMENTO_DE_TAXAS` | Aguardando pagamento de taxas |
| `AGUARDANDO_ASSINATURA_LAC` | Aguardando assinatura LAC |
| `AGUARDANDO_ATRIBUICAO` | Aguardando atribuição |
| `AGUARDANDO_ANALISE` | Aguardando análise |
| `AGUARDANDO_ELABORACAO` | Aguardando elaboração |
| `AGUARDANDO_ACEITE_ATRIBUICAO` | Aguardando aceite de atribuição |
| `EM_ANALISE` | Em análise |
| `AGUARDANDO_PUBLICACAO` | Aguardando publicação |
| `AGUARDANDO_CHECKLIST_DOCUMENTOS` | Aguardando checklist de documentos |
| `ARQUIVADO_DEFINITIVAMENTE` | Arquivado definitivamente |
| `ARQUIVADO_TEMPORARIAMENTE` | Arquivado temporariamente |
| `CONCLUIDO` | Concluído |
| `CANCELADO` | Cancelado |
| `AGUARDANDO_REVISAO` | Aguardando revisão |
| `EM_REVISAO` | Em revisão |
| `REQUERIMENTO_REVISADO` | Requerimento revisado |
| `AGUARDANDO_CORRECAO` | Aguardando correção |
| `EM_CORRECAO` | Em correção |
| `REQUERIMENTO_CORRIGIDO` | Requerimento corrigido |

As descrições exatas devem ser copiadas do enum `EnumSituacaoProcesso` do SIGA.
