-- S8 (bloco 1): o que o plano B (requerimento sem instância no Camunda) lê do Licenciamento, mais as datas de
-- REQUERIMENTO que ele usa. Só o DDL mínimo; as linhas de cada cenário (PB01 a PB03) são gravadas pelo
-- CamundaFicticioBuilder, chamado por SigaOracleTeste ao subir (CenariosFicticiosDaS8).
-- Tudo é FICTÍCIO: CPF inválido de propósito (000000000NN, NN diferente de 00). A coluna CPF_USUARIO existe de propósito
-- (a tabela real a tem) para que os testes do bloco 5 e 11 provem que o plano B NUNCA a lê nem a devolve.
-- DDL compatível com Oracle 19c.

ALTER TABLE SIGA2_LICENCIAMENTO.REQUERIMENTO ADD (
    DATA_PROTOCOLO_PROCESSO       TIMESTAMP,
    DATA_CANCELAMENTO             TIMESTAMP,
    DATA_EXPIRACAO_REQUERIMENTO   TIMESTAMP,
    DATA_EXPIRACAO_PROCESSO       TIMESTAMP
);

CREATE TABLE SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO (
    ID              NUMBER(19)    NOT NULL PRIMARY KEY,
    ID_REQUERIMENTO NUMBER(19)    NOT NULL,
    DATA_CADASTRO   TIMESTAMP     NOT NULL,
    EVENTO          VARCHAR2(80)  NOT NULL,
    CPF_USUARIO     VARCHAR2(11)
);

GRANT SELECT ON SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO TO VISTAS_LEITURA_SIGA;
