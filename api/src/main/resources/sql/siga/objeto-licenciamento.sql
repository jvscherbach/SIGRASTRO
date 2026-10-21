-- Objeto de licenciamento: atividade e grupo (1:1) e objetivos (1:N, sem vigência). Uma linha por objetivo, com a
-- atividade repetida; uma linha com OBJETIVO nulo quando não há objetivos; nenhuma linha quando o requerimento
-- não é de licenciamento.
SELECT rl.DESCRICAO_GRUPO_ATIVIDADE AS GRUPO, rl.DESCRICAO_ATIVIDADE AS ATIVIDADE,
       ol.DESCRICAO_OBJETIVO_LICENCIAMENTO AS OBJETIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO rl
LEFT   JOIN SIGA2_LICENCIAMENTO.OBJETIVO_LICENCIAMENTO ol ON ol.ID_REQUERIMENTO_LICENCIAMENTO = rl.ID
WHERE  rl.ID_REQUERIMENTO = :idRequerimento
ORDER  BY ol.ID
