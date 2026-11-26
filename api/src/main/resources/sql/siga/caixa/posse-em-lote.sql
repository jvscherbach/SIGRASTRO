-- S11, bloco 4. Detalhe da posse de um lote de requerimentos (até 500 ids), numa só consulta: tarefas ATIVAS da instância
-- RAIZ em atividade de análise, revisão ou correção, a última tarefa de análise ou revisão CONCLUÍDA (ACT_HI_TASKINST, por
-- PROC_INST_ID_; DELETE_REASON_ = 'completed'; mais recente por fim e id), o nome do interessado e o tipo de processo.
-- Raiz e complementação como em tarefas-ativas-em-lote.sql (BUSINESS_KEY_ sem função, índice; TO_CHAR do lado do REQUERIMENTO).
-- Uma linha por tarefa ativa (a análise multi-instância tem uma por analista); a última tarefa repete em cada linha.
-- Privacidade: o CPF só é devolvido quando é de um dos analistas pedidos (:analistas); o de qualquer outra pessoa, como o
-- interessado da tarefa de correção, vira NULL no próprio SQL. O adaptador troca o CPF pela chave opaca e o descarta.
WITH raiz AS (
    SELECT r.ID               AS REQUERIMENTO,
           r.NUMERO           AS NUMERO,
           r.ID_PESSOA        AS ID_PESSOA,
           r.ID_TIPO_PROCESSO AS ID_TIPO,
           TO_CHAR(d.KEY_)    AS MODELO,
           e.PROC_INST_ID_    AS INSTANCIA
    FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
    JOIN   SIGA2_LICENCIAMENTO.REQUERIMENTO r ON e.BUSINESS_KEY_ = TO_CHAR(r.ID)
    JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d     ON d.ID_ = e.PROC_DEF_ID_
    WHERE  e.BUSINESS_KEY_ IN (:chaves)
      AND  r.ID IN (:ids)
      AND  e.PARENT_ID_ IS NULL
      AND  e.ID_ = e.PROC_INST_ID_
      AND  d.KEY_ IN (:modelos)
      AND  d.KEY_ <> :chaveDaComplementacao
), ativa AS (
    SELECT raiz.REQUERIMENTO, raiz.NUMERO, raiz.ID_PESSOA, raiz.ID_TIPO, raiz.MODELO, raiz.INSTANCIA,
           TO_CHAR(t.TASK_DEF_KEY_) AS ATIVIDADE,
           t.CREATE_TIME_           AS INICIO,
           CASE WHEN TO_CHAR(t.ASSIGNEE_) IN (:analistas) THEN TO_CHAR(t.ASSIGNEE_) END AS RESPONSAVEL
    FROM   raiz
    JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t ON t.PROC_INST_ID_ = raiz.INSTANCIA
    WHERE  TO_CHAR(t.TASK_DEF_KEY_) IN (:atividadesDePosse)
       OR  TO_CHAR(t.TASK_DEF_KEY_) IN (:atividadesDeCorrecao)
), ultima AS (
    SELECT h.PROC_INST_ID_ AS INSTANCIA,
           TO_CHAR(h.TASK_DEF_KEY_) AS ATIVIDADE,
           CASE WHEN TO_CHAR(h.ASSIGNEE_) IN (:analistas) THEN TO_CHAR(h.ASSIGNEE_) END AS RESPONSAVEL,
           h.END_TIME_ AS FIM,
           ROW_NUMBER() OVER (PARTITION BY h.PROC_INST_ID_ ORDER BY h.END_TIME_ DESC, h.ID_ DESC) AS ORDEM
    FROM   SIGA2_CAMUNDA.ACT_HI_TASKINST h
    WHERE  h.PROC_INST_ID_ IN (SELECT ativa.INSTANCIA FROM ativa)
      AND  TO_CHAR(h.TASK_DEF_KEY_) IN (:atividadesDePosse)
      AND  h.END_TIME_ IS NOT NULL
      AND  TO_CHAR(h.DELETE_REASON_) = TO_CHAR('completed')
)
SELECT ativa.REQUERIMENTO,
       ativa.NUMERO,
       ativa.MODELO,
       ativa.ATIVIDADE,
       ativa.INICIO,
       ativa.RESPONSAVEL,
       ultima.ATIVIDADE   AS ULTIMA_ATIVIDADE,
       ultima.RESPONSAVEL AS ULTIMA_RESPONSAVEL,
       ultima.FIM         AS ULTIMA_FIM,
       p.NOME             AS INTERESSADO,
       tp.DESCRICAO       AS TIPO
FROM   ativa
LEFT   JOIN ultima ON ultima.INSTANCIA = ativa.INSTANCIA AND ultima.ORDEM = 1
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p        ON p.ID = ativa.ID_PESSOA
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = ativa.ID_TIPO
ORDER  BY ativa.REQUERIMENTO, ativa.INICIO, ativa.ATIVIDADE
FETCH  FIRST :maximoDeLinhas ROWS ONLY
