-- Total da lista, com os mesmos filtros de requerimentos-por-interessado.sql.
SELECT COUNT(*)
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  1 = 1/*FILTROS*/
