-- S8, bloco 2. Complementações do requerimento, encerradas ou não (regra 7): instância independente, com
-- BUSINESS_KEY_ = COMPLEMENTACAO.ID, sempre pela chave da definição (o id da complementação pode ser igual ao de outro
-- requerimento). Parte de COMPLEMENTACAO (poucas linhas por requerimento) e busca a instância pela BUSINESS_KEY_.
SELECT h.ID_            AS ID,
       h.PROC_DEF_KEY_  AS CHAVE,
       h.START_TIME_    AS INICIO,
       h.END_TIME_      AS FIM,
       h.END_ACT_ID_    AS ATIVIDADE_FINAL,
       h.STATE_         AS ESTADO
FROM   SIGA2_LICENCIAMENTO.COMPLEMENTACAO c
JOIN   SIGA2_CAMUNDA.ACT_HI_PROCINST h
       ON  h.BUSINESS_KEY_ = TO_CHAR(c.ID)
       AND h.PROC_DEF_KEY_ = :chaveDaComplementacao
WHERE  c.ID_REQUERIMENTO = :idRequerimento
ORDER  BY h.START_TIME_, h.ID_
FETCH  FIRST 20 ROWS ONLY
