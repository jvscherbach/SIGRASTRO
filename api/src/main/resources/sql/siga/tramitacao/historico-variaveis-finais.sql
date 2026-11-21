-- S8, bloco 2. Valor FINAL das variáveis da lista branca (ACT_HI_VARINST guarda só o último valor de cada uma). Só os
-- nomes da lista branca do domínio; por PROC_INST_ID_.
SELECT v.PROC_INST_ID_ AS INSTANCIA,
       v.NAME_         AS NOME,
       v.VAR_TYPE_     AS TIPO,
       v.TEXT_         AS TEXTO,
       v.LONG_         AS NUMERO,
       v.CREATE_TIME_  AS QUANDO
FROM   SIGA2_CAMUNDA.ACT_HI_VARINST v
WHERE  v.PROC_INST_ID_ IN (:instancias)
  AND  v.NAME_ IN (:nomes)
ORDER  BY v.CREATE_TIME_, v.ID_
FETCH  FIRST 200 ROWS ONLY
