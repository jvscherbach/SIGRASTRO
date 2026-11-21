-- S7, bloco 4. Para os ASSIGNEE_ (CPF de 11 dígitos) das tarefas de um requerimento: o nome em SERVIDOR (a versão mais
-- recente por CPF, como na S1) e se é o interessado do requerimento (PESSOA de REQUERIMENTO.ID_PESSOA). Uma consulta por
-- requerimento, lista curta em IN (no máximo 500 CPFs). SERVIDOR.CPF e PESSOA.CPF_CNPJ sem índice (R17): leitura em
-- lote, nunca uma por tarefa. O CPF só volta para o adaptador casar com o identificador que ele mesmo mandou.
SELECT CPF, NOME, INTERESSADO
FROM (
    SELECT s.CPF AS CPF,
           MAX(s.NOME) KEEP (DENSE_RANK FIRST ORDER BY s.DATA_ATUALIZACAO DESC NULLS LAST, s.ID DESC) AS NOME,
           0 AS INTERESSADO
    FROM   SIGA2_LICENCIAMENTO.SERVIDOR s
    WHERE  s.CPF IN (:cpfs)
    GROUP  BY s.CPF
    UNION ALL
    SELECT p.CPF_CNPJ AS CPF,
           CAST(NULL AS VARCHAR2(400)) AS NOME,
           1 AS INTERESSADO
    FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
    JOIN   SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
    WHERE  r.ID = :idRequerimento
      AND  p.CPF_CNPJ IN (:cpfs)
)
FETCH  FIRST 1000 ROWS ONLY
