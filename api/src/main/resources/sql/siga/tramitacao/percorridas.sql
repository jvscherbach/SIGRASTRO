-- S7, bloco 3. Atividades já percorridas pela instância (regra 10): com END_TIME_ e, se for tarefa, só as concluídas
-- (DELETE_REASON_ = 'completed'; as encerradas por correção de outro analista ficam de fora). Serve para: condição de
-- percurso do catálogo (isenção sem passagem pelo gateway de pagamento), última etapa real quando só há "Suporte" e as
-- concluídas do ciclo da multi-instância. Por PROC_INST_ID_ (regra 12); a tarefa histórica, por chave primária.
SELECT a.ACT_ID_     AS ATIVIDADE,
       a.START_TIME_ AS INICIO,
       a.END_TIME_   AS FIM,
       CASE WHEN a.TASK_ID_ IS NULL THEN 0 ELSE 1 END AS TAREFA,
       t.ASSIGNEE_   AS RESPONSAVEL
FROM   SIGA2_CAMUNDA.ACT_HI_ACTINST a
LEFT   JOIN SIGA2_CAMUNDA.ACT_HI_TASKINST t ON t.ID_ = a.TASK_ID_
WHERE  a.PROC_INST_ID_ = :instancia
  AND  a.END_TIME_ IS NOT NULL
  AND  (a.TASK_ID_ IS NULL OR TO_CHAR(t.DELETE_REASON_) = TO_CHAR('completed'))
ORDER  BY a.END_TIME_ DESC, a.ID_ DESC
FETCH  FIRST 500 ROWS ONLY
