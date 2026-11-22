-- S8, blocos 2 e 7. Atividades de todas as instâncias do requerimento (raiz, filhas e complementações), sem interpretar
-- nada. Sempre por PROC_INST_ID_ (regra 12: ACT_HI_ACTINST tem ~1,5 milhão de linhas) e a tarefa histórica por chave
-- primária. DELETE_REASON_ vira uma categoria (o texto original pode ser livre e não sai). CASE só com literais VARCHAR2
-- (ORA-12704).
--
-- Bloco 7: REPETIÇÕES COLAPSADAS ANTES DO LIMITE. Um trecho de atividades seguidas com o mesmo ACT_ID_, encerradas e sem
-- tarefa (script repetido por erro de API, milhares de vezes) fica só com a primeira e a última linha; REPRESENTA diz
-- quantas linhas cada uma vale (a primeira, o trecho menos um; a última, uma). Tarefas e atividades abertas nunca são
-- colapsadas. A numeração por instância (POSICAO) é contada DEPOIS do colapso, na ordem de gravação (início e id: o id
-- tem contador crescente por instância); o limite vale por instância, pedindo o limite + 1 (a linha a mais só indica
-- que passou e é descartada em Java). Trecho = "ilhas": a diferença entre a numeração geral e a numeração por ACT_ID_ é
-- constante dentro de cada sequência seguida.
SELECT *
FROM  (SELECT c.*,
              ROW_NUMBER() OVER (PARTITION BY c.INSTANCIA ORDER BY c.INICIO, c.ID) AS POSICAO
       FROM  (SELECT m.*,
                     CASE WHEN m.COLAPSAVEL = 0 OR m.TAMANHO_DO_TRECHO = 1 THEN 1
                          WHEN m.ORDEM_NO_TRECHO = 1 THEN m.TAMANHO_DO_TRECHO - 1
                          ELSE 1 END AS REPRESENTA
              FROM  (SELECT b.*,
                            COUNT(*) OVER (PARTITION BY b.INSTANCIA, b.ATIVIDADE, b.COLAPSAVEL, b.TRECHO) AS TAMANHO_DO_TRECHO,
                            ROW_NUMBER() OVER (PARTITION BY b.INSTANCIA, b.ATIVIDADE, b.COLAPSAVEL, b.TRECHO
                                               ORDER BY b.INICIO, b.ID) AS ORDEM_NO_TRECHO
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
                                   CASE WHEN a.TASK_ID_ IS NULL AND a.END_TIME_ IS NOT NULL THEN 1 ELSE 0 END AS COLAPSAVEL,
                                   ROW_NUMBER() OVER (PARTITION BY a.PROC_INST_ID_ ORDER BY a.START_TIME_, a.ID_)
                                 - ROW_NUMBER() OVER (PARTITION BY a.PROC_INST_ID_, a.ACT_ID_,
                                                          CASE WHEN a.TASK_ID_ IS NULL AND a.END_TIME_ IS NOT NULL
                                                               THEN 1 ELSE 0 END
                                                      ORDER BY a.START_TIME_, a.ID_) AS TRECHO
                            FROM   SIGA2_CAMUNDA.ACT_HI_ACTINST a
                            LEFT   JOIN SIGA2_CAMUNDA.ACT_HI_TASKINST t ON t.ID_ = a.TASK_ID_
                            WHERE  a.PROC_INST_ID_ IN (:instancias)) b) m
              WHERE  m.COLAPSAVEL = 0 OR m.ORDEM_NO_TRECHO = 1 OR m.ORDEM_NO_TRECHO = m.TAMANHO_DO_TRECHO) c)
WHERE POSICAO <= :limiteMaisUm
ORDER BY INICIO, ID
