-- S7, bloco 3. Tarefas ativas da instância raiz e das filhas (lista curta de PROC_INST_ID_), com os grupos candidatos
-- (ACT_RU_IDENTITYLINK, TYPE_ = 'candidate'). Uma linha por tarefa e grupo; o adaptador agrupa.
-- Multi-instância (regra 6): EXECUTION_ID_ não é confiável; o agrupamento é por PROC_INST_ID_ + TASK_DEF_KEY_.
-- ASSIGNEE_ é o CPF: sai daqui só para o adaptador, que o guarda protegido (nunca em resposta, log ou cache).
SELECT t.ID_            AS ID,
       t.PROC_INST_ID_  AS INSTANCIA,
       t.TASK_DEF_KEY_  AS ATIVIDADE,
       t.ASSIGNEE_      AS RESPONSAVEL,
       t.CREATE_TIME_   AS CRIADA_EM,
       l.GROUP_ID_      AS GRUPO
FROM   SIGA2_CAMUNDA.ACT_RU_TASK t
LEFT   JOIN SIGA2_CAMUNDA.ACT_RU_IDENTITYLINK l
       ON  l.TASK_ID_ = t.ID_
       AND l.TYPE_ = 'candidate'
       AND l.GROUP_ID_ IS NOT NULL
WHERE  t.PROC_INST_ID_ IN (:instancias)
ORDER  BY t.CREATE_TIME_, t.ID_
FETCH  FIRST 500 ROWS ONLY
