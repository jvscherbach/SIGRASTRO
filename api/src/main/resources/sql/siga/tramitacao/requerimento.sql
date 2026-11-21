-- S7, bloco 3. Tipo do requerimento (para escolher o modelo no catálogo de etapas) e a situação do Licenciamento
-- (só passa adiante para o confronto do bloco 5; nunca deriva a etapa). Por chave primária.
SELECT tp.INSTANCIA_REQUERIMENTO AS INSTANCIA,
       r.SITUACAO_PROCESSO       AS SITUACAO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
WHERE  r.ID = :idRequerimento
FETCH  FIRST 1 ROWS ONLY
