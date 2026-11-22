-- S9, bloco 2. Lançamentos de taxa de um ou mais requerimentos (até 500 por consulta), sem interpretar nada: a regra de
-- validade é do domínio (financeiro.domain.ClassificarValidade).
-- SOMENTE as colunas da lista fechada do prompt (docs/09). A chave do arquivo da guia, o texto livre de retorno e a
-- titulação NUNCA são lidos (o teste LancamentosDoSigaJdbcTest confere o SQL enviado ao banco, comentários incluídos, e
-- LancamentosSqlTest o arquivo) e não há seleção com asterisco.
-- Limite POR REQUERIMENTO: pede o limite + 1 (a linha a mais só indica que passou e é descartada em Java), na ordem de
-- emissão (sem data no fim) e id. O FETCH FIRST total é o teto de segurança (requerimentos x (limite + 1)).
-- Sem índice conhecido em ID_REQUERIMENTO (a tabela tem por volta de 9 mil linhas; recomendação de índice em docs/10).
SELECT ID,
       ID_REQUERIMENTO,
       STATUS,
       TAXA_ISENTA,
       STATUS_PROCESSADO,
       NUM_DAR,
       DATA_EMISSAO,
       DATA_VENCIMENTO,
       DATA_PAGAMENTO,
       DATA_FIM,
       VALOR_REAL,
       VALOR_DESCONTO,
       QTDE_TOTAL_UPF,
       PASSOU_POR_REAPROV_TAXA_CORREC,
       POSICAO
FROM  (SELECT l.ID,
              l.ID_REQUERIMENTO,
              l.STATUS,
              l.TAXA_ISENTA,
              l.STATUS_PROCESSADO,
              l.NUM_DAR,
              l.DATA_EMISSAO,
              l.DATA_VENCIMENTO,
              l.DATA_PAGAMENTO,
              l.DATA_FIM,
              l.VALOR_REAL,
              l.VALOR_DESCONTO,
              l.QTDE_TOTAL_UPF,
              l.PASSOU_POR_REAPROV_TAXA_CORREC,
              ROW_NUMBER() OVER (PARTITION BY l.ID_REQUERIMENTO
                                 ORDER BY l.DATA_EMISSAO NULLS LAST, l.ID) AS POSICAO
       FROM   SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA l
       WHERE  l.ID_REQUERIMENTO IN (:idsRequerimento))
WHERE  POSICAO <= :limiteMaisUm
ORDER  BY ID_REQUERIMENTO, POSICAO
FETCH  FIRST :maximoDeLinhas ROWS ONLY
