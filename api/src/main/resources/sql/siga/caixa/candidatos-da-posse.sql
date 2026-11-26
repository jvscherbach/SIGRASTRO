-- S11, bloco 4. Requerimentos que podem estar na posse de um lote de analistas (até 500 CPFs), numa só consulta, sem
-- depender de uma lista de ids (a caixa não usa o recorte do ano). Só ids: o detalhe vem de posse-em-lote.sql.
-- Posse direta: tarefa ATIVA da instância RAIZ (PARENT_ID_ nulo e ID_ = PROC_INST_ID_, nos modelos do catálogo; a
-- complementação fica de fora pela chave da definição) em atividade de análise ou revisão, atribuída a um dos analistas.
-- Posse na correção: tarefa ATIVA de correção (com o interessado) e a ÚLTIMA tarefa de análise ou revisão CONCLUÍDA da
-- instância (ACT_HI_TASKINST, por PROC_INST_ID_; DELETE_REASON_ = 'completed'; mais recente por fim e id) foi de um dos analistas.
-- ASSIGNEE_ é NVARCHAR2: TO_CHAR do lado dele. O CPF só entra como parâmetro e NUNCA é selecionado.
-- Pede o teto + 1: a linha a mais só indica que passou e é descartada em Java.
WITH raiz AS (
    SELECT e.BUSINESS_KEY_ AS CHAVE,
           e.PROC_INST_ID_ AS INSTANCIA
    FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
    JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d ON d.ID_ = e.PROC_DEF_ID_
    WHERE  e.PARENT_ID_ IS NULL
      AND  e.ID_ = e.PROC_INST_ID_
      AND  d.KEY_ IN (:modelos)
      AND  d.KEY_ <> :chaveDaComplementacao
), ativa AS (
    SELECT raiz.CHAVE,
           raiz.INSTANCIA,
           TO_CHAR(t.TASK_DEF_KEY_) AS ATIVIDADE,
           TO_CHAR(t.ASSIGNEE_)     AS RESPONSAVEL
    FROM   raiz
    JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t ON t.PROC_INST_ID_ = raiz.INSTANCIA
    WHERE  TO_CHAR(t.TASK_DEF_KEY_) IN (:atividadesDePosse)
       OR  TO_CHAR(t.TASK_DEF_KEY_) IN (:atividadesDeCorrecao)
), correcao AS (
    SELECT ativa.CHAVE, ativa.INSTANCIA
    FROM   ativa
    WHERE  ativa.ATIVIDADE IN (:atividadesDeCorrecao)
), ultima AS (
    SELECT h.PROC_INST_ID_ AS INSTANCIA,
           TO_CHAR(h.ASSIGNEE_) AS RESPONSAVEL,
           ROW_NUMBER() OVER (PARTITION BY h.PROC_INST_ID_ ORDER BY h.END_TIME_ DESC, h.ID_ DESC) AS ORDEM
    FROM   SIGA2_CAMUNDA.ACT_HI_TASKINST h
    WHERE  h.PROC_INST_ID_ IN (SELECT correcao.INSTANCIA FROM correcao)
      AND  TO_CHAR(h.TASK_DEF_KEY_) IN (:atividadesDePosse)
      AND  h.END_TIME_ IS NOT NULL
      AND  TO_CHAR(h.DELETE_REASON_) = TO_CHAR('completed')
), candidatos AS (
    SELECT ativa.CHAVE
    FROM   ativa
    WHERE  ativa.ATIVIDADE IN (:atividadesDePosse)
      AND  ativa.RESPONSAVEL IN (:analistas)
    UNION
    SELECT correcao.CHAVE
    FROM   correcao
    JOIN   ultima ON ultima.INSTANCIA = correcao.INSTANCIA AND ultima.ORDEM = 1
    WHERE  ultima.RESPONSAVEL IN (:analistas)
)
SELECT r.ID AS REQUERIMENTO
FROM   candidatos
JOIN   SIGA2_LICENCIAMENTO.REQUERIMENTO r ON candidatos.CHAVE = TO_CHAR(r.ID)
ORDER  BY r.ID
FETCH  FIRST :maximoDeLinhas ROWS ONLY
