-- S10, bloco 2. Base da fila por etapa e da carga por responsável: os requerimentos protocolados no ano (mesmo intervalo
-- do gráfico 1), em ordem de ID, com o tipo de processo (que escolhe o modelo no catálogo de etapas) e a situação crua.
-- S11, bloco 9: também o número, o nome do interessado (PESSOA.NOME por REQUERIMENTO.ID_PESSOA, o mesmo campo para pessoa e
-- empresa; nunca CPF/CNPJ) e a data de protocolo, para a lista de requerimentos por analista. Junção, sem consulta por linha.
-- Pede o teto + 1: a linha a mais só indica que passou (truncado) e é descartada em Java.
SELECT r.ID                     AS ID,
       tp.INSTANCIA_REQUERIMENTO AS TIPO,
       r.SITUACAO_PROCESSO      AS SITUACAO,
       r.NUMERO                 AS NUMERO,
       p.NOME                   AS INTERESSADO,
       r.DATA_PROTOCOLO_PROCESSO AS PROTOCOLO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  r.DATA_PROTOCOLO_PROCESSO >= CAST(:inicio AS DATE)
  AND  r.DATA_PROTOCOLO_PROCESSO <  CAST(:fim AS DATE)
ORDER  BY r.ID
FETCH  FIRST :limiteMaisUm ROWS ONLY
