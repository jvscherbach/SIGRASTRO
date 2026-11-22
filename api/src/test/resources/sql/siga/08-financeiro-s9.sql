-- S9 (bloco 1): LANCAMENTO_TAXA, a tabela das taxas/DAR que o módulo financeiro lê. Só o DDL mínimo; as linhas de cada
-- cenário (FIN01 a FIN16) são gravadas pelo CamundaFicticioBuilder, chamado por SigaOracleTeste ao subir
-- (CenariosFicticiosDaS9).
-- Tudo é FICTÍCIO: números de DAR evidentes ("FICT-..."), sem CPF, CNPJ nem nome. Os TIPOS das colunas são hipótese
-- (NÃO CONFIRMADO, docs/10): só a lista de colunas e o enum de STATUS vêm do prompt da S9.
-- As colunas CHAVE_GED, MENSAGEM e ID_TITULACAO existem de propósito (a tabela real as tem) e ficam SEMPRE nulas aqui: o
-- Rastro nunca as lê, e os testes do bloco 2 e 7 provam que o SQL do financeiro não as menciona.
-- DDL compatível com Oracle 19c.

CREATE TABLE SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA (
    ID                              NUMBER(19)     NOT NULL PRIMARY KEY,
    ID_REQUERIMENTO                 NUMBER(19)     NOT NULL,
    STATUS                          VARCHAR2(30),
    TAXA_ISENTA                     NUMBER(1),
    STATUS_PROCESSADO               VARCHAR2(1),
    NUM_DAR                         VARCHAR2(30),
    DATA_EMISSAO                    TIMESTAMP,
    DATA_VENCIMENTO                 TIMESTAMP,
    DATA_PAGAMENTO                  TIMESTAMP,
    DATA_FIM                        TIMESTAMP,
    VALOR_REAL                      NUMBER(15, 2),
    VALOR_DESCONTO                  NUMBER(15, 2),
    QTDE_TOTAL_UPF                  NUMBER(15, 4),
    PASSOU_POR_REAPROV_TAXA_CORREC  NUMBER(1),
    CHAVE_GED                       VARCHAR2(100),
    MENSAGEM                        VARCHAR2(400),
    ID_TITULACAO                    NUMBER(19)
);

GRANT SELECT ON SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA TO VISTAS_LEITURA_SIGA;
