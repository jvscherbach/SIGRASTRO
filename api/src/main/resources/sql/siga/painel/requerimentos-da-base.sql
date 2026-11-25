-- S10, bloco 2. Base da fila por etapa e da carga por responsável: os requerimentos protocolados no ano (mesmo intervalo
-- do gráfico 1), em ordem de ID, com o tipo de processo (que escolhe o modelo no catálogo de etapas) e a situação crua.
-- Pede o teto + 1: a linha a mais só indica que passou (truncado) e é descartada em Java.
SELECT r.ID                     AS ID,
       tp.INSTANCIA_REQUERIMENTO AS TIPO,
       r.SITUACAO_PROCESSO      AS SITUACAO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
WHERE  r.DATA_PROTOCOLO_PROCESSO >= CAST(:inicio AS DATE)
  AND  r.DATA_PROTOCOLO_PROCESSO <  CAST(:fim AS DATE)
ORDER  BY r.ID
FETCH  FIRST :limiteMaisUm ROWS ONLY
