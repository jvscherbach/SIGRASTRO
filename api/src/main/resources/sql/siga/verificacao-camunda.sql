-- Teste de conexão do Camunda (S7, bloco 10): lê uma linha de uma tabela do Camunda sem trazer dado nenhum (só a
-- constante 1). Prova a conexão e o grant de SELECT no SIGA2_CAMUNDA, que o teste do banco do SIGA (DUAL) não prova.
SELECT 1 AS OK
FROM   SIGA2_CAMUNDA.ACT_RE_PROCDEF
FETCH  FIRST 1 ROWS ONLY
