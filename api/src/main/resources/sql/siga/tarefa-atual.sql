-- Tarefa ativa da instância raiz cujo BUSINESS_KEY_ é o ID do requerimento. Sem linhas: instância encerrada
-- ou parada em gateway/evento.
-- O responsável é resolvido aqui e só o NOME sai: o CPF (ASSIGNEE_, 11 dígitos) não é selecionado.
-- Nome: SERVIDOR (versão mais recente por CPF) e, se não houver, PESSOA (a mais recente por ID).
-- ASSIGNEE_ é NVARCHAR2; a conversão com TO_CHAR fica do lado dele para não impedir o índice de SERVIDOR.CPF e
-- PESSOA.CPF_CNPJ (VARCHAR2). Só tenta resolver quando o valor tem exatamente 11 dígitos.
WITH tarefa AS (
    SELECT t.NAME_ AS NOME_TAREFA, t.CREATE_TIME_ AS CRIADA_EM, d.NAME_ AS FLUXO,
           CASE WHEN TRIM(t.ASSIGNEE_) IS NULL THEN 'VAZIO'
                WHEN REGEXP_LIKE(t.ASSIGNEE_, '^[0-9]{11}$') THEN 'CPF'
                ELSE 'OUTRO' END AS FORMATO,
           CASE WHEN REGEXP_LIKE(t.ASSIGNEE_, '^[0-9]{11}$') THEN TO_CHAR(t.ASSIGNEE_) END AS CPF
    FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
    JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t    ON t.PROC_INST_ID_ = e.PROC_INST_ID_
    JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d ON d.ID_ = t.PROC_DEF_ID_
    WHERE  e.PARENT_ID_ IS NULL
      AND  e.ID_ = e.PROC_INST_ID_
      AND  e.BUSINESS_KEY_ = TO_CHAR(:idRequerimento)
    ORDER  BY t.CREATE_TIME_ DESC
    FETCH  FIRST 1 ROWS ONLY
), nomes AS (
    SELECT tarefa.*,
           COALESCE(
               (SELECT NULLIF(TRIM(MAX(s.NOME) KEEP (DENSE_RANK FIRST ORDER BY s.DATA_ATUALIZACAO DESC NULLS LAST, s.ID DESC)), '')
                FROM   SIGA2_LICENCIAMENTO.SERVIDOR s
                WHERE  s.CPF = tarefa.CPF),
               (SELECT NULLIF(TRIM(MAX(p.NOME) KEEP (DENSE_RANK FIRST ORDER BY p.ID DESC)), '')
                FROM   SIGA2_LICENCIAMENTO.PESSOA p
                WHERE  p.CPF_CNPJ = tarefa.CPF)) AS NOME_RESPONSAVEL
    FROM   tarefa
)
SELECT NOME_TAREFA, CRIADA_EM, FLUXO,
       CASE WHEN FORMATO = 'VAZIO' THEN 'NAO_ATRIBUIDA'
            WHEN FORMATO = 'CPF' AND NOME_RESPONSAVEL IS NOT NULL THEN 'ATRIBUIDO'
            ELSE 'NAO_IDENTIFICADO' END AS SITUACAO_RESPONSAVEL,
       CASE WHEN FORMATO = 'CPF' THEN NOME_RESPONSAVEL END AS NOME_RESPONSAVEL
FROM   nomes
