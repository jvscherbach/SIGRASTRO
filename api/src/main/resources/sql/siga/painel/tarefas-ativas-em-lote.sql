-- S10, bloco 2. Tarefas ativas das instâncias RAIZ de um lote de requerimentos (até 500 ids), numa só consulta.
-- Raiz: PARENT_ID_ nulo e ID_ = PROC_INST_ID_, e só nos modelos do catálogo de etapas (chave da definição). A
-- complementação também é instância raiz com a mesma chave de negócio (ids colidem): fica de fora pela chave da definição,
-- nunca só pelo PARENT_ID_. Instância raiz ativa sem tarefa volta numa linha sem TAREFA (o requerimento tem instância).
-- BUSINESS_KEY_ sem função (índice); o TO_CHAR fica do lado do REQUERIMENTO.
-- Responsável resolvido aqui, como no tarefa-atual.sql: só o NOME sai (SERVIDOR, a versão mais recente por CPF; se não
-- houver, PESSOA, a mais recente por ID), só tenta com exatamente 11 dígitos, e o CPF NUNCA é selecionado na saída.
-- Interessado do requerimento (PESSOA de REQUERIMENTO.ID_PESSOA): categoria INTERESSADO, sem nome.
-- SERVIDOR.CPF e PESSOA.CPF_CNPJ sem índice (R17): um agregado por lote (GROUP BY com IN), nunca subconsulta por linha.
-- Uma linha por tarefa e grupo candidato; o adaptador agrupa pela TAREFA (id técnico que não sai do adaptador).
WITH raiz AS (
    SELECT r.ID              AS REQUERIMENTO,
           r.ID_PESSOA       AS ID_PESSOA,
           TO_CHAR(d.KEY_)   AS MODELO,
           e.PROC_INST_ID_   AS INSTANCIA
    FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
    JOIN   SIGA2_LICENCIAMENTO.REQUERIMENTO r ON e.BUSINESS_KEY_ = TO_CHAR(r.ID)
    JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d     ON d.ID_ = e.PROC_DEF_ID_
    WHERE  e.BUSINESS_KEY_ IN (:chaves)
      AND  r.ID IN (:ids)
      AND  e.PARENT_ID_ IS NULL
      AND  e.ID_ = e.PROC_INST_ID_
      AND  d.KEY_ IN (:modelos)
      AND  d.KEY_ <> :chaveDaComplementacao
), tarefa AS (
    SELECT raiz.REQUERIMENTO, raiz.ID_PESSOA, raiz.MODELO,
           t.ID_                        AS TAREFA,
           TO_CHAR(t.TASK_DEF_KEY_)     AS ATIVIDADE,
           CASE WHEN t.ID_ IS NULL THEN NULL
                WHEN TRIM(t.ASSIGNEE_) IS NULL THEN 'VAZIO'
                WHEN REGEXP_LIKE(t.ASSIGNEE_, '^[0-9]{11}$') THEN 'CPF'
                ELSE 'OUTRO' END        AS FORMATO,
           CASE WHEN REGEXP_LIKE(t.ASSIGNEE_, '^[0-9]{11}$') THEN TO_CHAR(t.ASSIGNEE_) END AS CPF
    FROM   raiz
    LEFT   JOIN SIGA2_CAMUNDA.ACT_RU_TASK t ON t.PROC_INST_ID_ = raiz.INSTANCIA
), servidores AS (
    SELECT s.CPF AS CPF,
           TRIM(MAX(s.NOME) KEEP (DENSE_RANK FIRST ORDER BY s.DATA_ATUALIZACAO DESC NULLS LAST, s.ID DESC)) AS NOME
    FROM   SIGA2_LICENCIAMENTO.SERVIDOR s
    WHERE  s.CPF IN (SELECT tarefa.CPF FROM tarefa WHERE tarefa.CPF IS NOT NULL)
    GROUP  BY s.CPF
), pessoas AS (
    SELECT p.CPF_CNPJ AS CPF,
           TRIM(MAX(p.NOME) KEEP (DENSE_RANK FIRST ORDER BY p.ID DESC)) AS NOME
    FROM   SIGA2_LICENCIAMENTO.PESSOA p
    WHERE  p.CPF_CNPJ IN (SELECT tarefa.CPF FROM tarefa WHERE tarefa.CPF IS NOT NULL)
    GROUP  BY p.CPF_CNPJ
), resolvida AS (
    SELECT tarefa.REQUERIMENTO, tarefa.MODELO, tarefa.TAREFA, tarefa.ATIVIDADE,
           CASE WHEN tarefa.TAREFA IS NULL THEN NULL
                WHEN tarefa.FORMATO = 'VAZIO' THEN 'NAO_ATRIBUIDA'
                WHEN tarefa.FORMATO = 'CPF' AND interessado.CPF_CNPJ = tarefa.CPF THEN 'INTERESSADO'
                WHEN tarefa.FORMATO = 'CPF' AND COALESCE(sv.NOME, ps.NOME) IS NOT NULL THEN 'SERVIDOR'
                ELSE 'NAO_IDENTIFICADO' END AS RESPONSAVEL,
           COALESCE(sv.NOME, ps.NOME)        AS NOME_ENCONTRADO
    FROM   tarefa
    LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA interessado ON interessado.ID = tarefa.ID_PESSOA
    LEFT   JOIN servidores sv ON sv.CPF = tarefa.CPF
    LEFT   JOIN pessoas ps    ON ps.CPF = tarefa.CPF
)
SELECT resolvida.REQUERIMENTO,
       resolvida.MODELO,
       resolvida.TAREFA,
       resolvida.ATIVIDADE,
       resolvida.RESPONSAVEL,
       CASE WHEN resolvida.RESPONSAVEL = 'SERVIDOR' THEN resolvida.NOME_ENCONTRADO END AS NOME_RESPONSAVEL,
       TO_CHAR(l.GROUP_ID_) AS GRUPO
FROM   resolvida
LEFT   JOIN SIGA2_CAMUNDA.ACT_RU_IDENTITYLINK l
       ON  l.TASK_ID_ = resolvida.TAREFA
       AND l.TYPE_ = 'candidate'
       AND l.GROUP_ID_ IS NOT NULL
ORDER  BY resolvida.REQUERIMENTO, resolvida.TAREFA, GRUPO
FETCH  FIRST :maximoDeLinhas ROWS ONLY
