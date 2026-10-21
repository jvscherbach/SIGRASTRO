-- Lista paginada. O marcador de filtros no WHERE é substituído por predicados fixos montados pelo adapter
-- (número, CPF/CNPJ, nome e restrição de interessado da RN07); a entrada do usuário entra como parâmetro nomeado.
SELECT r.ID, r.NUMERO, r.NUMERO_PROCESSO, r.DATA_ABERTURA, r.DATA_ALTERACAO, r.SITUACAO_PROCESSO,
       tp.DESCRICAO AS TIPO_PROCESSO, p.NOME AS NOME_INTERESSADO, p.CPF_CNPJ
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  1 = 1/*FILTROS*/
ORDER  BY r.DATA_ABERTURA DESC, r.ID DESC
OFFSET :inicio ROWS FETCH NEXT :tamanho ROWS ONLY
