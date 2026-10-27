# Roteiro de homologação da S2 (revisado na S2.1 e na S2.2) — documentos, token e dossiê em PDF

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA, à API de arquivos nem ao SCORP (D27, D34). Tudo o que foi provado até aqui usa portas falsas, `MockMvc`, um servidor HTTP local de teste, Oracle 23 Free por Testcontainers e Playwright com a API simulada. O que **só você** pode provar está neste roteiro: as tabelas e colunas de documentos do SIGA real, os índices, o token, os arquivos reais e o tempo de geração.

**S2.1:** a lista de documentos agora é a da seção "Arquivos do Processo" da Vista do SIGA: **seis tabelas** (`DOCUMENTO_PROCESSO`, `DOCUMENTO_REQUERIMENTO`, `COMPLEMENTACAO`, `IMOVEL`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`). O passo 0 da primeira versão deste roteiro falhou com `ORA-00904: "A"."NOME_ARQUIVO"` em `COMPLEMENTACAO_ITEM_ARQUIVO`; as colunas abaixo vêm do SQL realmente implementado.

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em "Validação da S2" de [`docs/14`](../14-plano-de-sprints.md), preencha [`docs/casos-de-teste.md`](../casos-de-teste.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que não bater.

> **Dados pessoais:** neste roteiro, CPF, CNPJ e números de processo de teste ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<REQUERIMENTO_N>`). Não grave valores reais em nenhum arquivo versionado. Os resultados abaixo são contagens; nunca copie nome, CPF, chave ou título de documento para o repositório.

## 0. Testes de fumaça (somente leitura)

**SIGA, com um requerimento que não existe (`-1`)** — executa o SQL real de **cada** origem; valida tabela, coluna e junção sem depender de dado. A falha aponta o **nome da origem**, sem imprimir credenciais:

```bash
cd api
SIGA_SMOKE=true SIGA_DB_URL=... SIGA_DB_USER=... SIGA_DB_PASSWORD=... ./mvnw -Dtest=SigaFumacaTest test
```

Resultado esperado: 3 testes executados e verdes (1 pulado: o do requerimento real). `ORA-00942` = grant faltando (passo 1); `ORA-00904` = coluna com outro nome (passo 2).

**SIGA, com um requerimento real** — acrescente `SIGA_FUMACA_REQUERIMENTO=<ID_DO_REQUERIMENTO>` (definido só por você). O teste imprime a contagem por origem e, por documento, apenas `origem`, `id`, situação, tipo e sigilo (**sem chave, sem título, sem nome de arquivo**):

```bash
cd api
SIGA_SMOKE=true SIGA_FUMACA_REQUERIMENTO=<ID_DO_REQUERIMENTO> SIGA_DB_URL=... SIGA_DB_USER=... SIGA_DB_PASSWORD=... ./mvnw -Dtest=SigaFumacaTest test
```

Resultado esperado: 4 testes verdes; as linhas `[fumaca]` mostram a contagem por origem (compare com o passo 5).

**API de arquivos** — baixa **um** arquivo, confere a assinatura `%PDF-` e não imprime token, URL nem chave:

```bash
cd api
ARQUIVOS_SMOKE=true LICENCIAMENTO_API_URL=... LICENCIAMENTO_API_TOKEN=... ARQUIVOS_SMOKE_CHAVE=<CHAVE_DE_UM_PDF> ./mvnw -Dtest=ArquivosFumacaTest test
```

(No modo cookie, acrescente `LICENCIAMENTO_API_AUTH_MODE=cookie` e `LICENCIAMENTO_API_COOKIE_NAME=...`.)

- [ ] OK / [ ] Falhou — observação:

## 1. Grants

### 1.1 Script de referência para o DBA

Somente `SELECT` (o usuário `VISTAS_LEITURA_SIGA` já tem `CREATE SESSION` e os grants da S1 e da S1.1). Lista tirada das consultas realmente implementadas em `api/src/main/resources/sql/siga/documentos/` (11 tabelas; **sem** `DOCUMENTO_PROCESSO_SIGNATARIO`: a Vista não usa a data de assinatura):

```sql
GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO                 TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO             TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO                     TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM                TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO        TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.IMOVEL                             TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL                   TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO_REPRESENTANTE_LEGAL   TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO            TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO_RESPONSAVEL_TECNICO   TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO         TO VISTAS_LEITURA_SIGA;
```

Nenhuma consulta lê `PESSOA_DOCUMENTO_DOENCA_GRAVE` (RN02) nem as tabelas de origens não listadas pela Vista (docs/04).

Conferência, **conectado com o próprio usuário de leitura** (cada linha deve devolver 0 ou 1, sem `ORA-00942`):

```sql
SELECT 'DOCUMENTO_PROCESSO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO WHERE ROWNUM = 1 UNION ALL
SELECT 'DOCUMENTO_REQUERIMENTO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO WHERE ROWNUM = 1 UNION ALL
SELECT 'COMPLEMENTACAO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO WHERE ROWNUM = 1 UNION ALL
SELECT 'COMPLEMENTACAO_ITEM' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM WHERE ROWNUM = 1 UNION ALL
SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO WHERE ROWNUM = 1 UNION ALL
SELECT 'IMOVEL' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.IMOVEL WHERE ROWNUM = 1 UNION ALL
SELECT 'DOCUMENTO_IMOVEL' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL WHERE ROWNUM = 1 UNION ALL
SELECT 'REQUERIMENTO_REPRESENTANTE_LEGAL' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.REQUERIMENTO_REPRESENTANTE_LEGAL WHERE ROWNUM = 1 UNION ALL
SELECT 'REQ_REP_LEGAL_DOCUMENTO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO WHERE ROWNUM = 1 UNION ALL
SELECT 'REQUERIMENTO_RESPONSAVEL_TECNICO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.REQUERIMENTO_RESPONSAVEL_TECNICO WHERE ROWNUM = 1 UNION ALL
SELECT 'REQ_RESP_TECNICO_DOCUMENTO' t, COUNT(*) n FROM SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO WHERE ROWNUM = 1;
```

- [ ] OK / [ ] Falhou — observação:

## 2. Dicionário de dados (somente as colunas realmente usadas)

Esta consulta lista toda coluna usada pelo SQL implementado que **não existe** (esperado: **0 linhas**). `IMOVEL.ID_REQUERIMENTO` é a única que a Vista usa mas **ainda não foi conferida no dicionário**: se ela aparecer aqui, é o ponto a resolver primeiro.

```sql
WITH ESPERADO (TABELA, COLUNA) AS (
  SELECT 'DOCUMENTO_PROCESSO', 'ID' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'CHAVE' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'TITULO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'NOME_ARQUIVO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'GRUPO_DOCUMENTO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'DATA_CRIACAO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_PROCESSO', 'SIGILOSO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'ID' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'CHAVE' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'TITULO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'NOME_ARQUIVO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'TIPO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'DATA_UPLOAD' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'SIGILOSO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO', 'ID' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM', 'ID' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM', 'ID_COMPLEMENTACAO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'ID' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'ID_COMPLEMENTACAO_ITEM' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'ID_COMP_ITEM_RESPOSTA' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'CHAVE_PANDORA' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'TITULO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'DATA_INICIO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'SIGILOSO' FROM DUAL UNION ALL
  SELECT 'IMOVEL', 'ID' FROM DUAL UNION ALL
  SELECT 'IMOVEL', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'ID' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'ID_IMOVEL' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'CHAVE' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'TITULO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'NOME_ARQUIVO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'DATA_INICIO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'SIGILOSO' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_REPRESENTANTE_LEGAL', 'ID' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_REPRESENTANTE_LEGAL', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'ID' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'ID_REQUERIMENTO_REP_LEGAL' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'CHAVE' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'NOME_ARQUIVO' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'TIPO' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'DATA_INICIO' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'SIGILO' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_RESPONSAVEL_TECNICO', 'ID' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_RESPONSAVEL_TECNICO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'ID' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'ID_REQUERIMENTO_RESP_TECNICO' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'CHAVE' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'NOME_ARQUIVO' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'TIPO' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'DATA_INICIO' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'DATA_FIM' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'SIGILO' FROM DUAL
)
SELECT e.TABELA, e.COLUNA
FROM   ESPERADO e
WHERE  NOT EXISTS (SELECT 1 FROM ALL_TAB_COLUMNS c
                   WHERE  c.OWNER = 'SIGA2_LICENCIAMENTO' AND c.TABLE_NAME = e.TABELA AND c.COLUMN_NAME = e.COLUNA)
ORDER  BY e.TABELA, e.COLUNA;
```

Se aparecer alguma linha, liste as colunas da tabela (`SELECT COLUMN_NAME, DATA_TYPE FROM ALL_TAB_COLUMNS WHERE OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = '...'`), corrija o nome no arquivo `.sql` da origem (e no recorte `api/src/test/resources/sql/siga/04-documentos-s2.sql`) e registre em `docs/04`. Uma origem quebrada pode ser desligada sem mexer em código: `vistas.documentos.origens.<origem>=false`.

- [ ] OK (0 linhas) / [ ] Falhou — observação:

### 2.1 Sigilo: tipos e valores distintos (fecha a P05)

Tipos esperados: `NUMBER` nas seis colunas.

```sql
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_PRECISION
FROM   ALL_TAB_COLUMNS
WHERE  OWNER = 'SIGA2_LICENCIAMENTO'
  AND  (TABLE_NAME, COLUMN_NAME) IN (
         ('DOCUMENTO_PROCESSO', 'SIGILOSO'), ('DOCUMENTO_REQUERIMENTO', 'SIGILOSO'),
         ('COMPLEMENTACAO_ITEM_ARQUIVO', 'SIGILOSO'), ('DOCUMENTO_IMOVEL', 'SIGILOSO'),
         ('REQ_REP_LEGAL_DOCUMENTO', 'SIGILO'), ('REQ_RESP_TECNICO_DOCUMENTO', 'SIGILO'))
ORDER  BY TABLE_NAME;
```

Valores distintos (esperado: só `0` e `1`; a normalização trata número ≠ 0 como sigiloso e nulo como não sigiloso, e **qualquer outro texto como sigiloso**):

```sql
SELECT 'DOCUMENTO_PROCESSO.SIGILOSO' ORIGEM, TO_CHAR(SIGILOSO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO GROUP BY TO_CHAR(SIGILOSO) UNION ALL
SELECT 'DOCUMENTO_REQUERIMENTO.SIGILOSO' ORIGEM, TO_CHAR(SIGILOSO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO GROUP BY TO_CHAR(SIGILOSO) UNION ALL
SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO.SIGILOSO' ORIGEM, TO_CHAR(SIGILOSO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO GROUP BY TO_CHAR(SIGILOSO) UNION ALL
SELECT 'DOCUMENTO_IMOVEL.SIGILOSO' ORIGEM, TO_CHAR(SIGILOSO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL GROUP BY TO_CHAR(SIGILOSO) UNION ALL
SELECT 'REQ_REP_LEGAL_DOCUMENTO.SIGILO' ORIGEM, TO_CHAR(SIGILO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO GROUP BY TO_CHAR(SIGILO) UNION ALL
SELECT 'REQ_RESP_TECNICO_DOCUMENTO.SIGILO' ORIGEM, TO_CHAR(SIGILO) VALOR, COUNT(*) QTD FROM SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO GROUP BY TO_CHAR(SIGILO);
```

| Coluna | Tipo esperado | Tipo encontrado | Valores encontrados (0/1 esperado) | OK? |
|---|---|---|---|---|
| `DOCUMENTO_PROCESSO.SIGILOSO` | `NUMBER` | | | [ ] |
| `DOCUMENTO_REQUERIMENTO.SIGILOSO` | `NUMBER` | | | [ ] |
| `COMPLEMENTACAO_ITEM_ARQUIVO.SIGILOSO` | `NUMBER` | | | [ ] |
| `DOCUMENTO_IMOVEL.SIGILOSO` | `NUMBER` | | | [ ] |
| `REQ_REP_LEGAL_DOCUMENTO.SIGILO` | `NUMBER` | | | [ ] |
| `REQ_RESP_TECNICO_DOCUMENTO.SIGILO` | `NUMBER` | | | [ ] |

Se aparecer um valor fora de 0/1 (ou nulo em grande quantidade), registre: ele será tratado como sigiloso (erra para o lado seguro, R18). Se tudo for 0/1, a P05 está fechada.

- [ ] OK / [ ] Falhou — observação:

## 3. Índices, dados sujos e duplicidade

### 3.1 Índices (R17)

As consultas filtram por `ID_REQUERIMENTO` e juntam pelas chaves abaixo. Esta consulta lista as colunas **sem** índice que comece por elas (esperado: 0 linhas; cada linha é um risco de varredura completa em tabela grande):

```sql
WITH ESPERADO (TABELA, COLUNA) AS (
  SELECT 'DOCUMENTO_PROCESSO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_REQUERIMENTO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'IMOVEL', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_REPRESENTANTE_LEGAL', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'REQUERIMENTO_RESPONSAVEL_TECNICO', 'ID_REQUERIMENTO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM', 'ID_COMPLEMENTACAO' FROM DUAL UNION ALL
  SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', 'ID_COMPLEMENTACAO_ITEM' FROM DUAL UNION ALL
  SELECT 'DOCUMENTO_IMOVEL', 'ID_IMOVEL' FROM DUAL UNION ALL
  SELECT 'REQ_REP_LEGAL_DOCUMENTO', 'ID_REQUERIMENTO_REP_LEGAL' FROM DUAL UNION ALL
  SELECT 'REQ_RESP_TECNICO_DOCUMENTO', 'ID_REQUERIMENTO_RESP_TECNICO' FROM DUAL
)
SELECT e.TABELA, e.COLUNA
FROM   ESPERADO e
WHERE  NOT EXISTS (SELECT 1 FROM ALL_IND_COLUMNS i
                   WHERE  i.TABLE_OWNER = 'SIGA2_LICENCIAMENTO' AND i.TABLE_NAME = e.TABELA
                     AND  i.COLUMN_NAME = e.COLUNA AND i.COLUMN_POSITION = 1)
ORDER  BY e.TABELA, e.COLUNA;
```

Anote as linhas devolvidas (e o tempo da listagem dos requerimentos mais pesados no passo 5). **Não crie índice no SIGA**: leve a lista à equipe do SIGA.

- [ ] OK (0 linhas) / [ ] Falhou — observação:

### 3.2 Chave `"0"`, nome em branco e data nula, por origem

Contagens da tabela inteira (a lista **não esconde** nenhum desses casos, nem derruba: chave `"0"` ou vazia vira `INDISPONIVEL`, nome em branco cai no título/`"<origem> <id>"`, data nula vai ao final). Na complementação o nome é o `TITULO`.

```sql
SELECT 'DOCUMENTO_PROCESSO' ORIGEM, COUNT(*) TOTAL,
       SUM(CASE WHEN TRIM(CHAVE) IS NULL OR TRIM(CHAVE) = '0' THEN 1 ELSE 0 END) CHAVE_ZERO_OU_VAZIA,
       SUM(CASE WHEN TRIM(NOME_ARQUIVO) IS NULL THEN 1 ELSE 0 END) NOME_EM_BRANCO,
       SUM(CASE WHEN DATA_CRIACAO IS NULL THEN 1 ELSE 0 END) DATA_NULA
FROM   SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO UNION ALL
SELECT 'DOCUMENTO_REQUERIMENTO', COUNT(*),
       SUM(CASE WHEN TRIM(CHAVE) IS NULL OR TRIM(CHAVE) = '0' THEN 1 ELSE 0 END),
       SUM(CASE WHEN TRIM(NOME_ARQUIVO) IS NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN DATA_UPLOAD IS NULL THEN 1 ELSE 0 END)
FROM   SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO UNION ALL
SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', COUNT(*),
       SUM(CASE WHEN TRIM(CHAVE_PANDORA) IS NULL OR TRIM(CHAVE_PANDORA) = '0' THEN 1 ELSE 0 END),
       SUM(CASE WHEN TRIM(TITULO) IS NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN DATA_INICIO IS NULL THEN 1 ELSE 0 END)
FROM   SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO UNION ALL
SELECT 'DOCUMENTO_IMOVEL', COUNT(*),
       SUM(CASE WHEN TRIM(CHAVE) IS NULL OR TRIM(CHAVE) = '0' THEN 1 ELSE 0 END),
       SUM(CASE WHEN TRIM(NOME_ARQUIVO) IS NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN DATA_INICIO IS NULL THEN 1 ELSE 0 END)
FROM   SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL UNION ALL
SELECT 'REQ_REP_LEGAL_DOCUMENTO', COUNT(*),
       SUM(CASE WHEN TRIM(CHAVE) IS NULL OR TRIM(CHAVE) = '0' THEN 1 ELSE 0 END),
       SUM(CASE WHEN TRIM(NOME_ARQUIVO) IS NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN DATA_INICIO IS NULL THEN 1 ELSE 0 END)
FROM   SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO UNION ALL
SELECT 'REQ_RESP_TECNICO_DOCUMENTO', COUNT(*),
       SUM(CASE WHEN TRIM(CHAVE) IS NULL OR TRIM(CHAVE) = '0' THEN 1 ELSE 0 END),
       SUM(CASE WHEN TRIM(NOME_ARQUIVO) IS NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN DATA_INICIO IS NULL THEN 1 ELSE 0 END)
FROM   SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO;
```

| Origem | Total | Chave `"0"` ou vazia | Nome em branco | Data nula |
|---|---|---|---|---|
| `DOCUMENTO_PROCESSO` | | | | |
| `DOCUMENTO_REQUERIMENTO` | | | | |
| `COMPLEMENTACAO_ITEM_ARQUIVO` | | | | |
| `DOCUMENTO_IMOVEL` | | | | |
| `REQ_REP_LEGAL_DOCUMENTO` | | | | |
| `REQ_RESP_TECNICO_DOCUMENTO` | | | | |

Nome em branco ou sem extensão aparece como "tipo não informado" no SIGA Vistas; o tipo real é conferido ao gerar o dossiê. Data nula e representante ou responsável técnico sem `ID_CORPORATIVO` derrubam a listagem da própria Vista (R22), mas não a do SIGA Vistas.

- [ ] OK / [ ] Falhou — observação:

### 3.3 Duplicidade entre origens (informativo)

Quantas chaves de cada origem **também** existem em `DOCUMENTO_PROCESSO` (a deduplicação por chave, RN05, mostra o documento uma vez; vence a origem que vem primeiro; a Vista **não** deduplica):

```sql
SELECT 'DOCUMENTO_REQUERIMENTO' ORIGEM, COUNT(*) CHAVES_TAMBEM_EM_DOCUMENTO_PROCESSO FROM SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO o
WHERE  o.CHAVE IS NOT NULL AND o.CHAVE <> '0'
  AND  EXISTS (SELECT 1 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO p WHERE p.CHAVE = o.CHAVE) UNION ALL
SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO' ORIGEM, COUNT(*) CHAVES_TAMBEM_EM_DOCUMENTO_PROCESSO FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO o
WHERE  o.CHAVE_PANDORA IS NOT NULL AND o.CHAVE_PANDORA <> '0'
  AND  EXISTS (SELECT 1 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO p WHERE p.CHAVE = o.CHAVE_PANDORA) UNION ALL
SELECT 'DOCUMENTO_IMOVEL' ORIGEM, COUNT(*) CHAVES_TAMBEM_EM_DOCUMENTO_PROCESSO FROM SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL o
WHERE  o.CHAVE IS NOT NULL AND o.CHAVE <> '0'
  AND  EXISTS (SELECT 1 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO p WHERE p.CHAVE = o.CHAVE) UNION ALL
SELECT 'REQ_REP_LEGAL_DOCUMENTO' ORIGEM, COUNT(*) CHAVES_TAMBEM_EM_DOCUMENTO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO o
WHERE  o.CHAVE IS NOT NULL AND o.CHAVE <> '0'
  AND  EXISTS (SELECT 1 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO p WHERE p.CHAVE = o.CHAVE) UNION ALL
SELECT 'REQ_RESP_TECNICO_DOCUMENTO' ORIGEM, COUNT(*) CHAVES_TAMBEM_EM_DOCUMENTO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO o
WHERE  o.CHAVE IS NOT NULL AND o.CHAVE <> '0'
  AND  EXISTS (SELECT 1 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO p WHERE p.CHAVE = o.CHAVE);
```

Resultado: o número só informa quanto a deduplicação vai atuar; **não é erro**. As origens que a Vista não lista (`PARECER_TECNICO_ANEXO`, `JUNTADA_PROCESSO` etc.) estão em "Origens não listadas pela Vista" de [`docs/04`](../04-modelo-de-dados.md) e só voltam após decisão sua (P04).

- [ ] OK / [ ] Falhou — observação:

## 4. Token da API de arquivos (P08)

> **Os acessos ficam registrados no nome de quem capturou o token.** Uso local, na sua máquina, nunca versionado e nunca colado em chat, e-mail ou arquivo do projeto. O token **expira**.

### 4.1 Como capturar

1. Entre no SIGA Licenciamento e abra a Vista do Processo de um requerimento.
2. DevTools (F12) → aba **Network**, com **Preserve log** marcado.
3. Visualize um documento: aparece uma chamada `GET .../api/arquivo/<chave>`.
4. Em **Request Headers**, copie o valor de `Authorization` (**sem** a palavra `Bearer`). Se não houver esse cabeçalho, copie o valor do cookie de autorização e anote o **nome** do cookie.

### 4.2 Onde colocar

No `.env.local` (que não vai para o Git). **Cada linha começa na coluna 1, sem espaço antes do nome** (uma linha com espaço no início não é lida como você espera):

```
LICENCIAMENTO_API_URL=https://HOST_DA_API_DE_ARQUIVOS
LICENCIAMENTO_API_TOKEN=O_TOKEN_COPIADO
# somente se for cookie:
# LICENCIAMENTO_API_AUTH_MODE=cookie
# LICENCIAMENTO_API_COOKIE_NAME=NOME_DO_COOKIE
```

- [ ] OK — modo usado: [ ] bearer [ ] cookie

### 4.3 Testar a API de arquivos de fora do SIGA Vistas (sem imprimir o token)

Bash — o `read -s` não ecoa o que você cola e a saída é só o código HTTP:

```bash
read -rs -p "Token: " TOKEN; echo
read -rp "URL base (sem barra final): " URL
read -rp "Chave de um PDF (do banco, ex.: DOCUMENTO_PROCESSO.CHAVE): " CHAVE
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" -H "Authorization: Bearer $TOKEN" "$URL/api/arquivo/$CHAVE"
# modo cookie:  -H "Cookie: NOME_DO_COOKIE=$TOKEN"
unset TOKEN
```

PowerShell:

```powershell
$t = Read-Host -AsSecureString "Token"
$url = Read-Host "URL base (sem barra final)"
$chave = Read-Host "Chave de um PDF"
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($t)
$token = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
(Invoke-WebRequest -Uri "$url/api/arquivo/$chave" -Headers @{ Authorization = "Bearer $token" } -Method Head -SkipHttpErrorCheck).StatusCode
$token = $null
```

Esperado: `200 application/pdf`. `401`/`403` = token expirado ou no formato errado (tente o modo cookie). `404` = chave inexistente. `503` = falha do repositório de arquivos.

### 4.4 O que fazer quando expira

A gaveta mostra "Token da API de arquivos expirado ou recusado. Capture um novo token". Capture outro (4.1), troque o valor no `.env.local` e suba de novo (`docker compose ... up -d --build`).

- [ ] OK / [ ] Falhou — observação:

## 5. Conferência com a Vista (3 requerimentos de `casos-de-teste.md`)

Escolha **3** requerimentos de [`casos-de-teste.md`](../casos-de-teste.md), **um deles que hoje falha na Vista do SIGA**. Para cada um, compare **a contagem por origem** da seção "Arquivos do Processo" da Vista do SIGA (o "de N" do paginador, **descontados os itens do SCORP**, que só entram na S4) com a lista do SIGA Vistas (seção **Documentos**) e com as linhas `[fumaca]` do teste com `SIGA_FUMACA_REQUERIMENTO` (passo 0):

| Requerimento | Fonte | Documento do processo | Documento do requerimento | Complementação | Imóvel | Rep. legal | Resp. técnico | Total |
|---|---|---|---|---|---|---|---|---|
| <REQUERIMENTO_1> (falha hoje) | Vista do SIGA (sem SCORP) | | | | | | | |
| | SIGA Vistas (antes da deduplicação, fumaça) | | | | | | | |
| | SIGA Vistas (tela, após deduplicação) | | | | | | | [ ] bate |
| <REQUERIMENTO_2> | Vista do SIGA (sem SCORP) | | | | | | | |
| | SIGA Vistas (antes da deduplicação, fumaça) | | | | | | | |
| | SIGA Vistas (tela, após deduplicação) | | | | | | | [ ] bate |
| <REQUERIMENTO_3> | Vista do SIGA (sem SCORP) | | | | | | | |
| | SIGA Vistas (antes da deduplicação, fumaça) | | | | | | | |
| | SIGA Vistas (tela, após deduplicação) | | | | | | | [ ] bate |

A contagem **antes da deduplicação** (fumaça) deve ser igual à da Vista. Na tela do SIGA Vistas a diferença esperada (não é erro) é: compactados e geometrias não são listados (até a S4) e chaves repetidas entre origens aparecem uma vez (RN05; a Vista não deduplica). A ordem inicial deve ser a mesma da Vista (data da própria origem; sem data ao final).

Confira na tela: os totais do cabeçalho (vigentes, indisponíveis, sigilosos, não PDF e "sem tipo informado"); documentos de nome em branco ou sem extensão aparecem com a etiqueta "tipo não informado", selecionáveis e fora da pré-seleção; representante legal e responsável técnico aparecem como sigilosos quando a coluna `SIGILO` é 1, **mesmo que a Vista os mostre sempre como "Não"** (divergência consciente, D43).

Confira também no DevTools (aba Rede) que **nenhuma** resposta de `/documentos` ou `/dossies` traz a chave do arquivo nem o token.

- [ ] OK / [ ] Falhou — observação:

### 5.1 Documentos que a Vista lista e o SIGA Vistas não (P21)

Para cada ID que aparece na Vista e falta no SIGA Vistas, descubra em **qual tabela** ele está e a qual requerimento pertence. A consulta devolve só origem, ID e o requerimento (nenhum nome, chave ou título). Troque a lista de IDs pelos que faltam:

```sql
SELECT 'DOCUMENTO_PROCESSO' ORIGEM, d.ID, d.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO d WHERE d.ID IN (<ID_1>, <ID_2>) UNION ALL
SELECT 'DOCUMENTO_REQUERIMENTO', d.ID, d.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.DOCUMENTO_REQUERIMENTO d WHERE d.ID IN (<ID_1>, <ID_2>) UNION ALL
SELECT 'COMPLEMENTACAO_ITEM_ARQUIVO', a.ID, c.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM_ARQUIVO a
  JOIN SIGA2_LICENCIAMENTO.COMPLEMENTACAO_ITEM i ON i.ID = a.ID_COMPLEMENTACAO_ITEM
  JOIN SIGA2_LICENCIAMENTO.COMPLEMENTACAO c ON c.ID = i.ID_COMPLEMENTACAO WHERE a.ID IN (<ID_1>, <ID_2>) UNION ALL
SELECT 'DOCUMENTO_IMOVEL', d.ID, i.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.DOCUMENTO_IMOVEL d
  JOIN SIGA2_LICENCIAMENTO.IMOVEL i ON i.ID = d.ID_IMOVEL WHERE d.ID IN (<ID_1>, <ID_2>) UNION ALL
SELECT 'REQ_REP_LEGAL_DOCUMENTO', d.ID, r.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.REQ_REP_LEGAL_DOCUMENTO d
  JOIN SIGA2_LICENCIAMENTO.REQUERIMENTO_REPRESENTANTE_LEGAL r ON r.ID = d.ID_REQUERIMENTO_REP_LEGAL WHERE d.ID IN (<ID_1>, <ID_2>) UNION ALL
SELECT 'REQ_RESP_TECNICO_DOCUMENTO', d.ID, r.ID_REQUERIMENTO FROM SIGA2_LICENCIAMENTO.REQ_RESP_TECNICO_DOCUMENTO d
  JOIN SIGA2_LICENCIAMENTO.REQUERIMENTO_RESPONSAVEL_TECNICO r ON r.ID = d.ID_REQUERIMENTO_RESP_TECNICO WHERE d.ID IN (<ID_1>, <ID_2>);
```

Leitura do resultado:
- ID **em nenhuma** das tabelas: é documento de outra fonte (provavelmente o SCORP, S4). Não é defeito da S2.1.
- ID numa tabela **com outro `ID_REQUERIMENTO`**: o vínculo da Vista é diferente do nosso (registre a tabela e o caminho).
- ID numa tabela **com o requerimento certo**: o documento é lido pelo SQL; confira se foi deduplicado (RN05, mesma chave de outro documento) ou se é compactado/geometria (RN10, RN11). O `SigaFumacaTest` com `SIGA_FUMACA_REQUERIMENTO` lista a contagem por origem **antes** da deduplicação.

Registre a conclusão em P21 de [`docs/10`](../10-pendencias-e-riscos.md).

## 6. Geração real

Para cada um dos 3 requerimentos (ou para os de `casos-de-teste.md`): **Gerar dossiê** com a seleção inicial (desde a S2.2, todos os selecionáveis), abrir o PDF e conferir:

| Item | Esperado | OK? |
|---|---|---|
| Capa | "Dossiê do Processo N/AAAA" (ou "Requerimento"), interessado, CPF/CNPJ, tipo de processo, situação, abertura, objetivos e atividades, "N documentos incluídos de M listados" (S2.2), data/hora e usuário; acentos corretos | [ ] |
| Sumário | uma linha por documento (data, título, origem, página inicial) e a página inicial confere com o documento | [ ] |
| Marcadores | um por documento, mais "Capa", "Sumário" e, se houver, "Documentos não incluídos" (S2.2), levando à página certa | [ ] |
| Numeração | "Página X de Y" no rodapé, a partir da 2ª página, inclusive em páginas em paisagem ou giradas | [ ] |
| Ordem | cronológica pela data da própria origem; sem data ao final | [ ] |
| Tamanho e rotação | cada página mantém o tamanho e a rotação do original (nada forçado para A4) | [ ] |
| Pré-visualizar / Baixar | abre sem baixar; baixa com o nome `dossie-processo-N-AAAA.pdf`; depois do download o arquivo some (410 ao pedir de novo) | [ ] |

Anote em `casos-de-teste.md`, para cada caso: resultado, **tempo de geração**, **páginas** e **tamanho do arquivo**.

- [ ] OK / [ ] Falhou — observação:

## 7. Falhas

Desde a S2.2 as falhas são **coletadas**: a geração vai até o fim, o pedido fica "Documentos com problema" (`PENDENTE_DECISAO`) e nada é entregue até a decisão. A decisão continua o mesmo pedido sem baixar de novo o que já foi obtido (detalhes no passo 10).

| Cenário | Como provocar | Esperado | OK? |
|---|---|---|---|
| Token vazio | `LICENCIAMENTO_API_TOKEN=` (ou arquivo do token vazio) e subir de novo | a obtenção para; gaveta: "Token da API de arquivos expirado ou recusado. Capture um novo token, atualize o arquivo do token e clique em Tentar novamente."; nenhum arquivo | [ ] |
| Token expirado | token antigo | idem; os já obtidos ficam guardados | [ ] |
| Tempo limite | `DOWNLOAD_TIMEOUT_SEGUNDOS=1` e subir de novo (requerimento com PDF grande) | o item mostra "Falhou: tempo limite excedido ao obter o arquivo" depois de 3 tentativas (`tentativas` = 3 no andamento); os demais continuam sendo obtidos; nenhum PDF entregue | [ ] |
| Tentar novamente | depois de uma recusa de token, trocar o arquivo do token (passo 10.5; mudar variável recria o container e perde o pedido em memória) e clicar em **Tentar novamente os N com problema** | só os com problema são obtidos de novo; o progresso não volta a zero; o dossiê sai completo | [ ] |
| Remover e gerar | provocar a falha de novo e clicar em **Remover os N com problema e gerar** | gera sem eles; aparecem como "Removido" na gaveta e em "Documentos não incluídos" no PDF; saem também da seleção da tela | [ ] |
| Sem a API de arquivos | `LICENCIAMENTO_API_URL=` | a lista funciona; cada documento falha com "API de arquivos não configurada" | [ ] |
| Tipo não informado que não é PDF | documento sem extensão (ou de nome em branco) cujo arquivo não é PDF | "Falhou: o arquivo não é um PDF (tipo detectado: X)…"; nada entregue sem decisão (RN09) | [ ] |
| Tipo não informado que é PDF | documento sem extensão cujo arquivo é PDF | entra no dossiê normalmente | [ ] |
| Pouco disco | `DOSSIE_ESPACO_MINIMO_MB` maior que o espaço livre do volume | cada documento falha com "espaço em disco insuficiente", sem chamar a API | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 8. Acesso (RN07 e sigilo)

- [ ] **Gestor e Administrador** veem todos os requerimentos e os documentos sigilosos com o nome real, e podem selecioná-los e gerar.
- [ ] **Operador:** o usuário `operador.demo` tem o CNPJ fictício `12.345.678/0001-90`. Para testar com um interessado real, **só na sua máquina e sem commitar**, troque o CPF/CNPJ dele em `api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java` pelo de um interessado de teste (`<CNPJ_DE_TESTE>`), suba com `--build` e verifique: (a) a seção Documentos de um requerimento **dele** lista tudo; (b) um documento sigiloso aparece como "Documento sigiloso", sem seleção, sem nome de arquivo, e a busca por texto não o encontra pelo título; (c) a URL `/consulta/requerimentos/{id}` de **outro** interessado dá "Você não é o interessado deste processo ou requerimento."; (d) em `/api/requerimentos/{id}/documentos` de outro interessado a resposta é 403 `NAO_INTERESSADO`. **Desfaça a troca depois.**

- [ ] OK / [ ] Falhou — observação:

## 9. Quadro do topo com altura estável (A1)

**Opção A (automática):** na pasta `front`, `npx playwright install chromium` (uma vez) e `npm run e2e`. Mede a altura do quadro e a escala da ilustração em 1280×720, 1920×1080 e 390×844 nos estados inicial, carregando, 400, 403, 404, 503 e erro de rede, e exige diferença de **0 px**. Resultado esperado: `26 passed`.

**Opção B (manual, no seu navegador com o SIGA Vistas real).** Abra a **Consulta**, cole o script abaixo no console (F12 → Console) e rode. Depois, provoque cada estado e rode `medir('nome do estado')`; a tabela final mostra a diferença para o estado inicial, que tem de ser **0**.

```js
(() => {
  const lidas = [];
  window.medir = (estado) => {
    const quadro = document.querySelector('.dash-hero').getBoundingClientRect();
    const cena = document.querySelector('.dash-scene svg').getBoundingClientRect();
    const atual = { estado, quadro: +quadro.height.toFixed(2), ilustracaoAltura: +cena.height.toFixed(2), ilustracaoLargura: +cena.width.toFixed(2) };
    lidas.push({ ...atual, diferencaDoInicial: +(atual.quadro - lidas[0]?.quadro || 0).toFixed(2) });
    console.table(lidas);
    return atual;
  };
  window.medir('inicial');
})();
```

| Estado | Como provocar | `medir(...)` → diferença | OK? |
|---|---|---|---|
| Inicial | abrir a tela | 0 (referência) | [ ] |
| 400 | número `3331` (validação do front) ou CPF com 5 dígitos | | [ ] |
| 404 | Requerimento `999999/1900` | | [ ] |
| 403 | como Operador, processo de outro interessado | | [ ] |
| 503 | `SIGA_DB_URL` inacessível e consultar | | [ ] |
| Carregando | consultar e rodar `medir('carregando')` logo em seguida | | [ ] |
| Resultados | nome `zzzzzz` ou um CPF/CNPJ de teste | | [ ] |

Repita 404, 403 e 503 no celular (DevTools, largura 390 px). **Não declare resolvido sem a medição.**

- [ ] OK / [ ] Falhou — observação:

## 10. S2.2 — seleção total, ordem, continuação, token por arquivo e dossiê grande

Use os mesmos 3 requerimentos do passo 5 (marcadores `<REQUERIMENTO_1>` a `<REQUERIMENTO_3>`), com o token real e a API de arquivos real (uso normal, **sem** o `compose.verificacao.yaml`).

### 10.1 Tudo selecionado ao abrir

Para cada requerimento, abra o detalhe e confira a linha da seção Documentos: "N documento(s) · V vigente(s) · S sem efeito · I indisponível(is)".

| Requerimento | N | V | S | I | V + S + I = N? | Selecionados ao abrir | = N − I − não PDF − sigilosos bloqueados? | OK? |
|---|---|---|---|---|---|---|---|---|
| `<REQUERIMENTO_1>` | | | | | [ ] | | [ ] | [ ] |
| `<REQUERIMENTO_2>` | | | | | [ ] | | [ ] | [ ] |
| `<REQUERIMENTO_3>` | | | | | [ ] | | [ ] | [ ] |

Os sem efeito e os de tipo não informado vêm marcados; indisponíveis, não PDF e (para o Operador) sigilosos ficam desmarcados e bloqueados.

### 10.2 Um documento que falha: só ele é refeito e o progresso não volta a zero

1. Provoque uma falha num só documento **sem precisar recriar o container** (o pedido fica em memória e se perde ao recriar): por exemplo, selecione um documento sem extensão cujo arquivo não é PDF.
2. **Gerar dossiê**: a geração vai até o fim, e a gaveta mostra "Documentos com problema", a lista com o documento e o motivo e "Aguardando sua decisão · X/N" (X = obtidos).
3. Clique em **Tentar novamente o 1 com problema**.
4. Confira: o contador continua de X/N (não volta a 0/N) e só aquele documento volta a "Obtendo". Ele falha de novo, porque o problema é do arquivo, mas os demais não são baixados outra vez. Para ver um "Tentar novamente" que **resolve**, use o token por arquivo (10.5): a credencial é trocada sem recriar o container.

- [ ] OK / [ ] Falhou — observação:

### 10.3 "Remover e gerar": seção "Documentos não incluídos" e capa

Provoque a falha de novo e clique em **Remover o 1 com problema e gerar**. No PDF:

| Item | Esperado | OK? |
|---|---|---|
| Capa | "N documentos incluídos de M listados", com M igual ao N da linha de totais (10.1) | [ ] |
| Seção | logo após o sumário, "Documentos não incluídos" com o removido ("removido pelo usuário após falha: …") e os indisponíveis, não PDF e desmarcados, cada um com data (ou "sem data"), título, origem, id e motivo | [ ] |
| Sumário e marcadores | a página inicial de cada documento continua certa depois da seção; marcador "Documentos não incluídos" | [ ] |
| Gaveta | o documento aparece como "Removido" e sai da seleção da tabela | [ ] |

- [ ] OK / [ ] Falhou — observação:

### 10.4 Ordem cronológica do PDF contra a tabela

Num requerimento que tenha **documentos com a mesma data** e **um sem data**: ordene a tabela por Documento (ou filtre) antes de gerar, e confira no sumário:

| Esperado | OK? |
|---|---|
| a ordem do PDF é a da tabela ordenada por **Data** crescente (não a da ordenação escolhida) | [ ] |
| datas iguais: desempate pela origem (processo, requerimento, complementação, imóvel, representante legal, responsável técnico) e depois pelo id numérico | [ ] |
| o documento sem data aparece no fim como "sem data", com a nota no rodapé do sumário | [ ] |
| um documento criado perto da meia-noite aparece no mesmo dia na tela e no sumário | [ ] |

- [ ] OK / [ ] Falhou — observação:

### 10.5 Trocar o token pelo arquivo, sem recriar o container

1. Configure o token por arquivo (README, "Token por arquivo"): `.secrets/token-arquivos`, a linha do `compose.local.yaml` descomentada e `LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos` no `.env.local`. Suba uma vez.
2. Grave no arquivo um token **expirado** (ou um texto qualquer) com um editor de texto. Não use `echo`, porque o token fica no histórico do shell. Gere um dossiê: a obtenção para com "Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.".
3. Capture um token novo (passo 4.1), grave-o no mesmo arquivo **sem** reiniciar nada e clique em **Tentar novamente os N com problema**.
4. Confira: o pedido continua (os documentos obtidos antes da recusa não são baixados de novo) e o dossiê sai completo. Nos logs (`docker compose ... logs api`) não aparece o token nem o caminho do arquivo.

- [ ] OK / [ ] Falhou — observação:

### 10.6 Dossiê grande: tempo, tamanho e disco

Escolha o maior requerimento disponível (de preferência acima de 150 MB de PDFs).

1. Enquanto gera, meça o disco do pedido (sem imprimir nada sensível):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T api du -sh /var/tmp/vistas
```

2. Anote em [`casos-de-teste.md`](../casos-de-teste.md), na tabela "Dossiê grande (S2.2)": documentos, soma dos arquivos, tempo até "Dossiê pronto", tamanho do PDF, maior uso de `/var/tmp/vistas` e se o download foi por ticket.
3. Acima de 150 MB, confira: sem **Pré-visualizar**, aviso "Dossiê grande (N MB)", e **Baixar PDF** baixando direto pelo navegador (o download aparece na barra de downloads com o nome `dossie-processo-N-AAAA.pdf`). Depois que o download terminar, um novo **Baixar** responde que o dossiê expirou.

- [ ] OK / [ ] Falhou — observação:

## 11. Registro do resultado

Preencha a seção **"Validação da S2"** de [`docs/14`](../14-plano-de-sprints.md) (requerimento, falha hoje, documentos SIGA × Vistas, dossiê, páginas, tempo, bate/não bate), anote os casos em [`casos-de-teste.md`](../casos-de-teste.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que não bater. Itens resolvidos aqui (P04, P05, P08, P09, P10) saem das pendências.
