# Roteiro de homologação da S1 — conexão e leitura reais do SIGA

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA (decisão D27); tudo o que foi provado até aqui usa portas falsas e um Oracle 23 Free local com um recorte do modelo. Este roteiro fecha o que só o banco real pode provar.

**O que é provado aqui e não nos testes automáticos:** que o usuário de leitura tem os grants certos, que os nomes **e os tipos** das colunas batem, que a rede alcança o Oracle, que o SQL funciona no Oracle real (19c, se for o caso) e que os dados mostrados batem com a tela atual do SIGA.

Marque cada passo: `[ ] OK` / `[ ] Falhou` e anote a observação. Se algo falhar, pare e veja "Se algo não bater" (passo 11).

## Antes de começar

Você precisa ter em mãos:

- o endereço (host), a porta e o nome do serviço do Oracle do SIGA (para a URL JDBC `jdbc:oracle:thin:@//HOST:PORTA/SERVICO`);
- um usuário de leitura (passo 1) e a senha dele (a senha **nunca** vai para o repositório);
- 3 requerimentos/processos reais para comparar com a tela atual do SIGA: um **protocolado** (tem processo), um **não protocolado** e, se possível, um **com tarefa ativa** e outro **sem tarefa**;
- os nomes das tabelas de objetivo, finalidade e atividade, e a regra da prioridade de atendimento (passo 3), se quiser validar esses campos.

## 1. Usuário de leitura (P10) — com o DBA

- [ ] O DBA do SIGA criou o usuário `VISTAS_LEITURA_SIGA` com **somente** `CREATE SESSION` e `SELECT` nas tabelas abaixo (as usadas pela S1).

Script de referência para o DBA (a senha é definida por ele e não é versionada):

```sql
CREATE USER VISTAS_LEITURA_SIGA IDENTIFIED BY "<senha definida pelo DBA>";
GRANT CREATE SESSION TO VISTAS_LEITURA_SIGA;

GRANT SELECT ON SIGA2_LICENCIAMENTO.REQUERIMENTO   TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.PESSOA         TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_LICENCIAMENTO.TIPO_PROCESSO  TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_CAMUNDA.ACT_RU_EXECUTION     TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_CAMUNDA.ACT_RU_TASK          TO VISTAS_LEITURA_SIGA;
GRANT SELECT ON SIGA2_CAMUNDA.ACT_RE_PROCDEF       TO VISTAS_LEITURA_SIGA;
```

Na versão definitiva, o DBA pode preferir criar views `VW_` e conceder o `SELECT` nelas; nesse caso, o SQL do `integration.siga` passa a apontar para as views (mudança só nesse pacote).

Conferência, **conectado como `VISTAS_LEITURA_SIGA`**:

```sql
-- Deve listar apenas CREATE SESSION
SELECT PRIVILEGE FROM USER_SYS_PRIVS;
-- Deve listar apenas SELECT, nas seis tabelas acima
SELECT OWNER, TABLE_NAME, PRIVILEGE FROM USER_TAB_PRIVS ORDER BY OWNER, TABLE_NAME;
-- Deve ficar vazia (sem roles com privilégios extras)
SELECT ROLE FROM SESSION_ROLES;
```

- [ ] Só `CREATE SESSION` e `SELECT` nas seis tabelas; nenhum `INSERT`, `UPDATE`, `DELETE` nem role extra.

## 2. Conferência do dicionário (nomes e tipos)

Conectado como `VISTAS_LEITURA_SIGA` (a visão `ALL_TAB_COLUMNS` só mostra o que o usuário enxerga: coluna ausente pode ser **grant faltando** ou **nome errado**):

```sql
SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE
FROM   ALL_TAB_COLUMNS
WHERE  (OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = 'REQUERIMENTO'
        AND COLUMN_NAME IN ('ID','NUMERO','NUMERO_PROCESSO','DATA_ABERTURA','DATA_ALTERACAO','SITUACAO_PROCESSO','ID_PESSOA','ID_TIPO_PROCESSO'))
   OR  (OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = 'PESSOA' AND COLUMN_NAME IN ('ID','NOME','CPF_CNPJ'))
   OR  (OWNER = 'SIGA2_LICENCIAMENTO' AND TABLE_NAME = 'TIPO_PROCESSO' AND COLUMN_NAME IN ('ID','DESCRICAO'))
   OR  (OWNER = 'SIGA2_CAMUNDA' AND TABLE_NAME = 'ACT_RU_EXECUTION' AND COLUMN_NAME IN ('ID_','PROC_INST_ID_','PARENT_ID_','BUSINESS_KEY_'))
   OR  (OWNER = 'SIGA2_CAMUNDA' AND TABLE_NAME = 'ACT_RU_TASK' AND COLUMN_NAME IN ('PROC_INST_ID_','PROC_DEF_ID_','NAME_','ASSIGNEE_','CREATE_TIME_'))
   OR  (OWNER = 'SIGA2_CAMUNDA' AND TABLE_NAME = 'ACT_RE_PROCDEF' AND COLUMN_NAME IN ('ID_','NAME_'))
ORDER  BY OWNER, TABLE_NAME, COLUMN_ID;
```

Esperado: **24 linhas** (8 + 3 + 2 + 4 + 5 + 2). Preencha a tabela:

| Tabela.coluna | Tipo esperado (hipótese do teste) | Tipo encontrado | OK? |
|---|---|---|---|
| `REQUERIMENTO.NUMERO` | texto (`"N/AAAA"`) | | |
| `REQUERIMENTO.NUMERO_PROCESSO` | texto (`"N/AAAA"`) | | |
| `REQUERIMENTO.DATA_ABERTURA` | `DATE` ou `TIMESTAMP` | | |
| `REQUERIMENTO.DATA_ALTERACAO` | `DATE` ou `TIMESTAMP` | | |
| `REQUERIMENTO.SITUACAO_PROCESSO` | texto (nome do enum) | | |
| `REQUERIMENTO.ID`, `ID_PESSOA`, `ID_TIPO_PROCESSO` | número | | |
| `PESSOA.NOME`, `CPF_CNPJ` | texto | | |
| `TIPO_PROCESSO.DESCRICAO` | texto | | |
| `ACT_RU_EXECUTION.BUSINESS_KEY_` | texto (o `ID` do requerimento como texto) | | |
| `ACT_RU_TASK.CREATE_TIME_` | `TIMESTAMP` | | |

- [ ] Todas as 24 colunas existem e os tipos são compatíveis (se uma data for `NUMBER` ou o número do requerimento for outro tipo, registre: o adapter precisará de ajuste).

Verificações de formato (substitua pelos valores reais; use o usuário de leitura):

```sql
-- O número do requerimento e do processo estão no formato N/AAAA, sem zeros à esquerda?
SELECT NUMERO, NUMERO_PROCESSO FROM SIGA2_LICENCIAMENTO.REQUERIMENTO ORDER BY ID DESC FETCH FIRST 10 ROWS ONLY;

-- Há número repetido? (o esperado é nenhuma linha)
SELECT NUMERO, COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO GROUP BY NUMERO HAVING COUNT(*) > 1;
SELECT NUMERO_PROCESSO, COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO
WHERE  NUMERO_PROCESSO IS NOT NULL GROUP BY NUMERO_PROCESSO HAVING COUNT(*) > 1;

-- O CPF/CNPJ é guardado com ou sem máscara? (11/14 = só dígitos; 14/18 = com máscara)
SELECT LENGTH(CPF_CNPJ) AS TAMANHO, COUNT(*) FROM SIGA2_LICENCIAMENTO.PESSOA GROUP BY LENGTH(CPF_CNPJ) ORDER BY 2 DESC;

-- Quais situações existem que o Vistas não conhece? (devem aparecer só valores do Apêndice A de docs/04)
SELECT SITUACAO_PROCESSO, COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO GROUP BY SITUACAO_PROCESSO ORDER BY 2 DESC;
```

- [ ] Formato de número, duplicidades, máscara do CPF/CNPJ e situações conferidos. Anote o que fugir do esperado: _____

## 3. Mapeamento P01 e P03 (pendência de dados)

Se ainda **não** estiver mapeado, o sistema funciona e mostra "—" nos campos abaixo. Para ligar cada um:

| O que levantar | Onde registrar | O que muda no código |
|---|---|---|
| Tabelas e colunas de **objetivo, finalidade, atividade e do bloco Tipo/Objetivo** (a partir do código do SIGA e da tela atual) | Seção "Mapeamento de objeto e prioridade (P01)" de `docs/04-modelo-de-dados.md` | Novos arquivos em `api/src/main/resources/sql/siga/` (`objeto-*.sql`) e o método `objetoDe(...)` do adapter de requerimentos |
| **Regra da prioridade de atendimento** a partir de `REQUERIMENTO.PESO_PRIORIDADE` (ex.: qual faixa vira "Atendimento normal") | idem | Consultas de requerimento passam a trazer a prioridade traduzida |
| **Coluna de descrição de `TIPO_REQUERIMENTO`** | idem | Consultas de requerimento passam a trazer o tipo |
| **P03:** `REQUERIMENTO.NUMERO_PROCESSO` coincide com `PROCESSO.NUMERO`? | idem | Eventualmente `requerimento-por-processo.sql` |

Para o P03, como o usuário de leitura não tem grant em `PROCESSO`, compare como dono/DBA:

```sql
SELECT COUNT(*) AS DIVERGENTES
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
JOIN   SIGA2_LICENCIAMENTO.PROCESSO p ON p.ID = r.ID_PROCESSO
WHERE  NVL(r.NUMERO_PROCESSO, '#') <> NVL(p.NUMERO, '#');
```

(`ID_PROCESSO` consta no modelo de `docs/04`; confirme o nome.) Se der zero, `REQUERIMENTO.NUMERO_PROCESSO` é suficiente.

- [ ] P01 mapeado (ou registrado como pendente). - [ ] P03 resolvido (ou registrado como pendente).

## 4. Rede (P11)

A partir da **máquina onde o Docker roda** (a `api` sai por ela):

PowerShell:

```powershell
Test-NetConnection -ComputerName HOST -Port PORTA
```

Esperado: `TcpTestSucceeded : True`.

Bash:

```bash
timeout 5 bash -c '</dev/tcp/HOST/PORTA' && echo "porta aberta" || echo "sem conexão"
```

De dentro de um container (o que a `api` realmente enxerga):

```bash
docker run --rm busybox nc -zv -w 5 HOST PORTA
```

- [ ] A porta do Oracle responde da máquina e de dentro do container. (Se só responder com VPN, a VPN precisa estar ativa durante os testes.)

## 5. Configuração

Edite o `.env.local` (não é versionado). **Os valores ficam só com você**; aqui estão apenas os nomes:

```
SIGA_DB_URL=jdbc:oracle:thin:@//HOST:PORTA/SERVICO
SIGA_DB_USER=VISTAS_LEITURA_SIGA
SIGA_DB_PASSWORD=...
# opcionais: SIGA_DB_POOL_MAX (2), SIGA_DB_CONN_TIMEOUT_MS (5000), SIGA_DB_READ_TIMEOUT_MS (30000)
```

Suba e confira:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
curl http://localhost:8081/actuator/health
```

- [ ] `health` = `UP`. - [ ] Nos logs (`docker compose ... logs api`) não há erro de conexão ao entrar a primeira consulta (passo 8).

## 6. Teste de fumaça (somente leitura)

Executa as consultas do `integration.siga` com valores que não casam com nada; prova conexão, grants e colunas sem ler dados de pessoas. Roda **fora do Docker**, na pasta `api`:

Bash:

```bash
SIGA_SMOKE=true SIGA_DB_URL='jdbc:oracle:thin:@//HOST:PORTA/SERVICO' \
SIGA_DB_USER=VISTAS_LEITURA_SIGA SIGA_DB_PASSWORD='...' \
./mvnw -Dtest=SigaFumacaTest test
```

PowerShell:

```powershell
$env:SIGA_SMOKE = 'true'
$env:SIGA_DB_URL = 'jdbc:oracle:thin:@//HOST:PORTA/SERVICO'
$env:SIGA_DB_USER = 'VISTAS_LEITURA_SIGA'
$env:SIGA_DB_PASSWORD = '...'
.\mvnw.cmd "-Dtest=SigaFumacaTest" test
```

Esperado: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`. Depois feche o terminal (ou apague as variáveis) para não deixar a senha no ambiente.

| Sintoma no resultado | Significa |
|---|---|
| `ORA-12541`, `ORA-12170`, `Connection refused`, `SIGA indisponível` | Rede/porta/serviço (volte ao passo 4) |
| `ORA-01017` | Usuário ou senha |
| `ORA-00942` (tabela ou view não existe) | **Falta de grant** ou nome da tabela diferente |
| `ORA-00904` (identificador inválido) | Nome de coluna diferente do esperado (passo 2) |
| `IllegalStateException: Falha ao consultar o SIGA` | Qualquer dos dois anteriores; veja a causa no relatório do Maven |
| `leTarefaAtual...` falha com `INDISPONIVEL` | Falta grant nas tabelas do motor de fluxo ou coluna diferente |

- [ ] Teste de fumaça passou.

## 7. Consultas de conferência (comparar banco × API)

As mesmas consultas do `integration.siga`, com exemplos. Rode no seu cliente SQL e compare com a resposta da API/tela.

```sql
-- Requerimento por número (troque :numero por '3331/2025', por exemplo)
SELECT r.ID, r.NUMERO, r.NUMERO_PROCESSO, r.DATA_ABERTURA, r.DATA_ALTERACAO, r.SITUACAO_PROCESSO,
       tp.DESCRICAO AS TIPO_PROCESSO, p.NOME AS NOME_INTERESSADO, p.CPF_CNPJ
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT   JOIN SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE  r.NUMERO = '3331/2025';          -- para processo: r.NUMERO_PROCESSO = '2825/2025'

-- Tarefa atual (troque 12345 pelo ID do requerimento)
SELECT t.NAME_ AS NOME_TAREFA, t.ASSIGNEE_ AS RESPONSAVEL, t.CREATE_TIME_ AS CRIADA_EM, d.NAME_ AS FLUXO
FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t    ON t.PROC_INST_ID_ = e.PROC_INST_ID_
JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d ON d.ID_ = t.PROC_DEF_ID_
WHERE  e.PARENT_ID_ IS NULL AND e.ID_ = e.PROC_INST_ID_ AND e.BUSINESS_KEY_ = TO_CHAR(12345)
ORDER  BY t.CREATE_TIME_ DESC FETCH FIRST 1 ROWS ONLY;

-- Total da busca por CPF/CNPJ (só dígitos) e por nome: compare com o "N resultado(s)" da tela
SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE REGEXP_REPLACE(p.CPF_CNPJ, '[^0-9]', '') = '12345678000190';

SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO r
LEFT JOIN SIGA2_LICENCIAMENTO.PESSOA p ON p.ID = r.ID_PESSOA
WHERE UPPER(p.NOME) LIKE '%VALE%' ESCAPE '\';
```

Meça também o **tempo** das duas últimas consultas (risco R09): anote _____ s (CPF/CNPJ) e _____ s (nome). Se passar de poucos segundos, registre em `docs/10`.

- [ ] Consultas conferidas.

## 8. Validação com 3 processos reais (tela)

Abra `http://localhost:8080`, entre como `gestor.demo` (senha de demonstração; veja o README) e, para cada um dos 3 processos, busque pelo número (processo e requerimento), abra o detalhe e compare **cada campo** com a tela atual do SIGA. Faça também uma busca por CPF/CNPJ e uma por nome (compare o total).

Modelo de tabela (uma por processo):

| Campo | SIGA (tela atual) | SIGA Vistas | Bate? |
|---|---|---|---|
| Identificação (RN04: processo, ou requerimento "não protocolado") | | | |
| Situação | | | |
| Data de abertura | | | |
| Última atualização | | | |
| Interessado (nome) | | | |
| CPF/CNPJ do interessado | | | |
| Tipo de processo | | | |
| Tipo de requerimento | | (— se P01 pendente) | |
| Prioridade de atendimento | | (— se P01 pendente) | |
| Objetivo / Finalidade / Atividade / Tipo | | (— se P01 pendente) | |
| Tarefa atual: nome | | | |
| Tarefa atual: desde | | | |
| Tarefa atual: responsável | | | |

Processo 1 (protocolado): ______ · Processo 2 (não protocolado): ______ · Processo 3: ______

- [ ] Os 3 processos conferidos. As datas de abertura/atualização e o horário da tarefa batem (atenção a fuso).

## 9. Regras de acesso com dados reais (RN07)

- [ ] **Gestor** (`gestor.demo`) e **Administrador** (`admin.demo`) abrem qualquer processo e veem qualquer resultado.
- [ ] **Operador:** o usuário `operador.demo` tem o CNPJ fictício `12.345.678/0001-90`. Para testar com dados reais, **só na sua máquina e sem commitar**, troque o CPF/CNPJ dele em `api/src/main/java/br/gov/mt/sema/vistas/acesso/infrastructure/memoria/RepositorioUsuariosEmMemoria.java` pelo de um interessado real, suba com `--build` e verifique: (a) a busca por número de um processo **dele** abre o detalhe; (b) a busca por número de **outro** interessado mostra "não tem você como interessado"; (c) a busca por nome só lista processos **dele**, e o total confere com o SQL do passo 7 filtrado pelo CPF/CNPJ; (d) abrir pela URL `/consulta/requerimentos/{id}` de outro interessado dá "Você não é o interessado deste processo ou requerimento.". **Desfaça a troca depois.**

## 10. Falhas

- [ ] Com a rede/VPN desligada (ou senha errada no `.env.local` e `up -d` de novo): a consulta responde **503** em poucos segundos (o timeout de conexão é 5 s), a tela mostra "O banco do SIGA está indisponível no momento.", `/actuator/health` continua `UP` e o login continua funcionando.
- [ ] Ao restabelecer a conexão, a consulta volta a funcionar **sem reiniciar** a `api`.

## 11. Registro do resultado

- Anote o resultado dos 3 processos na seção **"Validação da S1"** de [`docs/14-plano-de-sprints.md`](../14-plano-de-sprints.md) (processo, campo, SIGA, SIGA Vistas, bate/não bate).
- **Se algum campo não bater** ou algum passo falhar: abra uma pendência em [`docs/10-pendencias-e-riscos.md`](../10-pendencias-e-riscos.md) com o passo, o valor esperado e o encontrado; peça a correção ao Claude Code citando o arquivo `.sql` do `integration.siga` envolvido.
- Atualize o status de P01, P03, P10 e P11 em `docs/10` e o mapeamento em `docs/04`.
- Quando tudo estiver conferido, a S1 passa de "implementada, validação pendente" para "validada".
