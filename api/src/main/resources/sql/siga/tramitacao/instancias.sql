-- S7, bloco 3. Instâncias raiz do requerimento no modelo, da mais recente para a mais antiga (docs/15).
-- Regra 1: BUSINESS_KEY_ = TO_CHAR(id), nunca TO_NUMBER(BUSINESS_KEY_) (há chaves não numéricas: ORA-01722). A coluna
-- fica sem função para o índice de BUSINESS_KEY_ valer; o TO_CHAR está do lado do parâmetro.
-- Regra 2: sempre pela chave da definição (ids de requerimento, complementação e título colidem como BUSINESS_KEY_);
-- nunca por PROC_DEF_ID_ (uma por versão do BPMN).
-- O texto de DELETE_REASON_ não sai (pode ser texto livre): só se estava preenchido.
SELECT p.ID_          AS ID,
       p.START_TIME_  AS INICIO,
       p.END_TIME_    AS FIM,
       p.END_ACT_ID_  AS ATIVIDADE_FINAL,
       p.STATE_       AS ESTADO,
       CASE WHEN p.DELETE_REASON_ IS NULL THEN 0 ELSE 1 END AS COM_MOTIVO
FROM   SIGA2_CAMUNDA.ACT_HI_PROCINST p
WHERE  p.BUSINESS_KEY_ = TO_CHAR(:idRequerimento)
  AND  p.PROC_DEF_KEY_ = :chaveDoModelo
  AND  p.SUPER_PROCESS_INSTANCE_ID_ IS NULL
ORDER  BY p.START_TIME_ DESC, p.ID_ DESC
FETCH  FIRST 10 ROWS ONLY
