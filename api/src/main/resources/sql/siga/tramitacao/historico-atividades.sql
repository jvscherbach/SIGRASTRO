-- S8, bloco 2. Atividades de todas as instâncias do requerimento (raiz, filhas e complementações), brutas, SEM remover
-- ruído nem interpretar nada. Sempre por PROC_INST_ID_ (regra 12: ACT_HI_ACTINST tem ~1,5 milhão de linhas) e a tarefa
-- histórica por chave primária. Limite POR INSTÂNCIA: o número de linhas pedido é o limite + 1; a linha a
-- mais só serve para saber que passou do limite e é descartada em Java. A numeração segue a ordem de gravação (início e
-- id: o id da atividade tem contador crescente por instância).
-- DELETE_REASON_ vira uma categoria (o texto original pode ser livre e não sai). CASE só com literais VARCHAR2 (ORA-12704).
SELECT *
FROM  (SELECT a.ID_                 AS ID,
              a.PARENT_ACT_INST_ID_ AS ID_PAI,
              a.EXECUTION_ID_       AS EXECUCAO,
              a.PROC_INST_ID_       AS INSTANCIA,
              a.ACT_ID_             AS ATIVIDADE,
              a.ACT_NAME_           AS NOME,
              a.ACT_TYPE_           AS TIPO,
              a.START_TIME_         AS INICIO,
              a.END_TIME_           AS FIM,
              a.ASSIGNEE_           AS RESPONSAVEL,
              a.CALL_PROC_INST_ID_  AS CHAMADA,
              t.TASK_DEF_KEY_       AS TAREFA_DEFINICAO,
              t.ASSIGNEE_           AS TAREFA_RESPONSAVEL,
              t.START_TIME_         AS TAREFA_INICIO,
              t.END_TIME_           AS TAREFA_FIM,
              CASE WHEN t.ID_ IS NULL THEN NULL
                   WHEN t.DELETE_REASON_ IS NULL THEN 'ABERTA'
                   WHEN TO_CHAR(t.DELETE_REASON_) = TO_CHAR('completed') THEN 'CONCLUIDA'
                   WHEN TO_CHAR(t.DELETE_REASON_) = TO_CHAR('deleted') THEN 'EXCLUIDA'
                   ELSE 'OUTRO' END AS TAREFA_MOTIVO,
              ROW_NUMBER() OVER (PARTITION BY a.PROC_INST_ID_ ORDER BY a.START_TIME_, a.ID_) AS POSICAO
       FROM   SIGA2_CAMUNDA.ACT_HI_ACTINST a
       LEFT   JOIN SIGA2_CAMUNDA.ACT_HI_TASKINST t ON t.ID_ = a.TASK_ID_
       WHERE  a.PROC_INST_ID_ IN (:instancias))
WHERE POSICAO <= :limiteMaisUm
ORDER BY INICIO, ID
