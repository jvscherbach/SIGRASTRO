-- S8, bloco 5. Plano B: eventos do requerimento em HISTORICO_REQUERIMENTO, só data e nome do evento. A coluna
-- CPF_USUARIO NUNCA é lida. Limite de 500 eventos (a linha 501 só indica que havia mais).
-- Sem índice conhecido em ID_REQUERIMENTO (regra 12; recomendação de índice no bloco 7): a consulta só roda para
-- requerimento SEM instância no Camunda (legado 2021/2022), nunca na lista ou no painel.
SELECT h.DATA_CADASTRO AS QUANDO,
       h.EVENTO        AS EVENTO
FROM   SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO h
WHERE  h.ID_REQUERIMENTO = :idRequerimento
ORDER  BY h.DATA_CADASTRO, h.ID
FETCH  FIRST 501 ROWS ONLY
