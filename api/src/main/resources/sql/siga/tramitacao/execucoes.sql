-- S7, bloco 3. Execuções da instância com atividade: esperas de pagamento (gateway por evento, regra 4: não são
-- tarefas), automáticas paradas, chamadas de emissão e o corpo da multi-instância (<tarefa>#multiInstanceBody, cujo
-- início marca o ciclo atual). Por PROC_INST_ID_; as subconsultas também (ACT_HI_ACTINST tem ~1,5 milhão de linhas:
-- regra 12) e por EXECUTION_ID_ na assinatura de evento (ACT_RU_EVENT_SUBSCR, sem o "RIPTION").
SELECT e.ACT_ID_ AS ATIVIDADE,
       (SELECT MAX(a.START_TIME_)
        FROM   SIGA2_CAMUNDA.ACT_HI_ACTINST a
        WHERE  a.PROC_INST_ID_ = e.PROC_INST_ID_
          AND  a.ACT_ID_ = e.ACT_ID_
          AND  a.END_TIME_ IS NULL) AS DESDE,
       (SELECT MIN(s.CREATED_)
        FROM   SIGA2_CAMUNDA.ACT_RU_EVENT_SUBSCR s
        WHERE  s.EXECUTION_ID_ = e.ID_) AS ASSINADA_EM
FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
WHERE  e.PROC_INST_ID_ = :instancia
  AND  e.ACT_ID_ IS NOT NULL
ORDER  BY e.ID_
FETCH  FIRST 200 ROWS ONLY
