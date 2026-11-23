-- S10, bloco 2. Gráfico 1 do painel numa só consulta agregada: por mês, os requerimentos protocolados no ano e, deles, os
-- concluídos (mês de DATA_ALTERACAO: aproximado, NÃO CONFIRMADO), mais os abertos no ano sem data de protocolo.
-- Não depende do teto de requerimentos: o banco agrega, nenhuma linha de requerimento sai daqui.
-- Recorte por INTERVALO (>= 1º/jan e < 1º/jan seguinte), nunca função na coluna (o índice recomendado em docs/10 vale).
-- CAST(... AS DATE) fica do lado do parâmetro: se a coluna for DATE, comparar com TIMESTAMP converteria a coluna.
-- Colunas de data NÃO CONFIRMADAS: DATA_PROTOCOLO_PROCESSO (protocolo), DATA_ALTERACAO (conclusão) e DATA_ABERTURA (ano
-- de quem não tem protocolo). MEDIDA: PROTOCOLADOS, CONCLUIDOS ou SEM_DATA_DE_PROTOCOLO (esta sem ANO e MES).
WITH base AS (
    SELECT r.DATA_PROTOCOLO_PROCESSO AS PROTOCOLO,
           r.DATA_ALTERACAO          AS ALTERACAO,
           CASE WHEN r.SITUACAO_PROCESSO = 'CONCLUIDO' THEN 1 ELSE 0 END AS CONCLUIDO
    FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
    WHERE  r.DATA_PROTOCOLO_PROCESSO >= CAST(:inicio AS DATE)
      AND  r.DATA_PROTOCOLO_PROCESSO <  CAST(:fim AS DATE)
)
SELECT MEDIDA, ANO, MES, TOTAL
FROM (
    SELECT 'PROTOCOLADOS' AS MEDIDA,
           EXTRACT(YEAR FROM PROTOCOLO)  AS ANO,
           EXTRACT(MONTH FROM PROTOCOLO) AS MES,
           COUNT(*)                      AS TOTAL
    FROM   base
    GROUP  BY EXTRACT(YEAR FROM PROTOCOLO), EXTRACT(MONTH FROM PROTOCOLO)
    UNION ALL
    SELECT 'CONCLUIDOS',
           EXTRACT(YEAR FROM ALTERACAO),
           EXTRACT(MONTH FROM ALTERACAO),
           COUNT(*)
    FROM   base
    WHERE  CONCLUIDO = 1
    GROUP  BY EXTRACT(YEAR FROM ALTERACAO), EXTRACT(MONTH FROM ALTERACAO)
    UNION ALL
    SELECT 'SEM_DATA_DE_PROTOCOLO', NULL, NULL, COUNT(*)
    FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
    WHERE  r.DATA_PROTOCOLO_PROCESSO IS NULL
      AND  r.DATA_ABERTURA >= CAST(:inicio AS DATE)
      AND  r.DATA_ABERTURA <  CAST(:fim AS DATE)
)
ORDER  BY MEDIDA, ANO, MES
FETCH  FIRST :maximoDeLinhas ROWS ONLY
