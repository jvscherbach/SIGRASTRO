-- S8, bloco 2. Valor DA ÉPOCA das variáveis da lista branca (regra 11): cada alteração em ACT_HI_DETAIL
-- (TYPE_ = 'VariableUpdate'), em ordem. A lista de nomes vem do domínio (VariavelBruta.PERMITIDAS): variável do Camunda
-- pode guardar CPF e nome, então nenhum outro nome é lido. Por PROC_INST_ID_.
SELECT d.PROC_INST_ID_    AS INSTANCIA,
       d.NAME_            AS NOME,
       d.VAR_TYPE_        AS TIPO,
       d.TEXT_            AS TEXTO,
       d.LONG_            AS NUMERO,
       d.TIME_            AS QUANDO
FROM   SIGA2_CAMUNDA.ACT_HI_DETAIL d
WHERE  d.PROC_INST_ID_ IN (:instancias)
  AND  d.TYPE_ = 'VariableUpdate'
  AND  d.NAME_ IN (:nomes)
ORDER  BY d.TIME_, d.SEQUENCE_COUNTER_, d.ID_
FETCH  FIRST 2000 ROWS ONLY
