-- S7, bloco 3. Instâncias filhas ativas (emissões por callActivity, regra 5): ligadas pela instância-mãe
-- (SUPER_EXEC_ da execução raiz da filha aponta para uma execução da mãe), nunca pela BUSINESS_KEY_ (que é o id do
-- título). A chave da definição vem de ACT_RE_PROCDEF.KEY_ e o início, de ACT_HI_PROCINST por chave primária.
SELECT f.PROC_INST_ID_ AS ID,
       d.KEY_          AS CHAVE,
       h.START_TIME_   AS INICIO
FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION m
JOIN   SIGA2_CAMUNDA.ACT_RU_EXECUTION f ON f.SUPER_EXEC_ = m.ID_
JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d   ON d.ID_ = f.PROC_DEF_ID_
LEFT   JOIN SIGA2_CAMUNDA.ACT_HI_PROCINST h ON h.ID_ = f.PROC_INST_ID_
WHERE  m.PROC_INST_ID_ = :instancia
  AND  f.PARENT_ID_ IS NULL
  AND  f.ID_ = f.PROC_INST_ID_
ORDER  BY h.START_TIME_, f.PROC_INST_ID_
FETCH  FIRST 50 ROWS ONLY
