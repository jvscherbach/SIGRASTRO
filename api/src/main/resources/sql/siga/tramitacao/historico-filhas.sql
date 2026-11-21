-- S8, bloco 2. Instâncias filhas (emissões por callActivity, regra 5) de uma instância raiz, encerradas ou não, pela
-- instância-mãe (SUPER_PROCESS_INSTANCE_ID_), nunca pela BUSINESS_KEY_ (que é o id do título). A chave da definição vem
-- de ACT_HI_PROCINST.PROC_DEF_KEY_. Índice em SUPER_PROCESS_INSTANCE_ID_: NÃO CONFIRMADO (docs/15, P40); a busca devolve
-- poucas linhas.
SELECT p.ID_                        AS ID,
       p.PROC_DEF_KEY_              AS CHAVE,
       p.START_TIME_                AS INICIO,
       p.END_TIME_                  AS FIM,
       p.END_ACT_ID_                AS ATIVIDADE_FINAL,
       p.STATE_                     AS ESTADO,
       p.SUPER_PROCESS_INSTANCE_ID_ AS MAE
FROM   SIGA2_CAMUNDA.ACT_HI_PROCINST p
WHERE  p.SUPER_PROCESS_INSTANCE_ID_ = :instancia
ORDER  BY p.START_TIME_, p.ID_
FETCH  FIRST 50 ROWS ONLY
