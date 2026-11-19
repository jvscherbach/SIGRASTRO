# Roteiro de homologação da S6 — correções, CI mais leve e ferramentas do Claude Code

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA, à API de arquivos, ao SCORP nem ao Camunda (D27, D34). Os defeitos corrigidos nesta sprint foram testados com dados sintéticos e com falhas **simuladas**; este roteiro confere o resultado com os seus dados e mostra, passo a passo, as ferramentas do Claude Code que entram na apresentação.

**O que a S6 entrega e este roteiro confere:**

- o **nome do arquivo** na coluna do nome e o **tipo/título** na linha de baixo, em todas as telas e no PDF, e o cabeçalho da planilha convertida pelo nome do arquivo sem extensão (bloco 1, D76);
- a listagem de **Usuários e perfis** e a coluna **Detalhe** da auditoria alinhadas em 1280, 768 e 360 px (bloco 2);
- **disco cheio**: falha com mensagem clara, sem arquivo parcial e sem sucesso falso (bloco 3);
- o **teste de conexão** sem falso alarme (bloco 4);
- o **CI** que pula o LibreOffice em PR (bloco 5);
- as **ferramentas do Claude Code**: hooks, servidor MCP e `/verificar-sprint` (blocos 6 a 8).

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ e números de processo ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<ID_DO_REQUERIMENTO>`). **Nenhum PDF gerado, CSV ou captura de tela com dados reais vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

> **Uso do `.env.local`:** você edita o `.env.local` sozinho, sem colar o conteúdo em lugar nenhum. Linhas do `.env.local` não podem começar com espaço. Os comandos abaixo só dizem **se** uma variável está preenchida, nunca o valor.

## 0. Preparação

Para os passos 1 e 2 (defeitos corrigidos), suba **com** o sistema real de desenvolvimento ou homologação, como no roteiro da S5:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml ps
```

Resultado esperado: `api` e `front` em execução; abra `http://localhost:8080`. Os usuários de demonstração têm o clique rápido na tela de login: `operador.demo`, `gestor.demo` e `admin.demo`.

Escolha antes de começar (anote só os marcadores):

| Item | Anote |
|---|---|
| Requerimento com **planilha** convertida (para o passo 1.1) | `<ID_DO_REQUERIMENTO>` |
| Um documento cujo **nome de arquivo tem ponto no meio** (ex.: `laudo.v2.xlsx`) e outro **sem extensão** | |
| Um requerimento qualquer que gere dossiê pequeno (para o passo 1.3) | |

- [ ] OK / [ ] Falhou — observação:

## 1. Defeitos corrigidos

### 1.1 Nome do arquivo × tipo (bloco 1)

Entre como `gestor.demo` e abra o requerimento com planilha. Compare com a lista da **Vista do SIGA** para o mesmo requerimento.

| Onde | Esperado | OK? |
|---|---|---|
| Lista de documentos | a coluna do nome mostra o **nome do arquivo** em destaque; na linha de baixo, o ID e o **tipo/título** | [ ] |
| Ordenar pela coluna **Documento** | a ordem segue o nome do arquivo | [ ] |
| Busca | acha tanto pelo nome do arquivo quanto pelo tipo/título | [ ] |
| Documento sem nome de arquivo | aparece o tipo/título como nome | [ ] |
| **Visualizar** (modal) | o título do modal é o nome do arquivo; a ficha mostra também o título | [ ] |
| Gerar o dossiê e abrir o **sumário** e as páginas substitutas | usam o nome do arquivo | [ ] |
| **Cabeçalho da planilha convertida** (página do PDF) | o nome do arquivo **sem a extensão** (`Teste de bombeamento.xlsx` → `Teste de bombeamento`), e não o nome do tipo | [ ] |
| Nome com ponto no meio (`laudo.v2.xlsx`) | cabeçalho `laudo.v2` | [ ] |
| Nome sem extensão | cabeçalho com o nome inteiro | [ ] |
| **Auditoria › Documentos**, detalhe de um evento | o documento aparece com o nome do arquivo em destaque e o título abaixo | [ ] |

- [ ] OK / [ ] Falhou — observação:

### 1.2 Usuários e perfis e coluna Detalhe (bloco 2)

Entre como `admin.demo`. Repita em **1280**, **768** e **360 px** (ferramentas do desenvolvedor do navegador, modo dispositivo).

| Tela | Esperado | OK? |
|---|---|---|
| Configurações › **Usuários e perfis**, 1280 px | tabela de seis colunas alinhada, perfil e situação como lista com texto | [ ] |
| Idem, 768 e 360 px | cada usuário como **cartão** (rótulo e valor), sem rolagem horizontal da página; nome e login longos quebram linha | [ ] |
| **Auditoria › Acessos**, coluna **Detalhe** | cabeçalho e células com o **mesmo alinhamento e largura**; texto longo quebra linha (nada cortado) | [ ] |
| **Auditoria › Documentos**, coluna Detalhe | o mesmo comportamento | [ ] |

- [ ] OK / [ ] Falhou — observação:

### 1.3 Armazenamento cheio, sem lotar o disco (bloco 3)

> **Não encha o disco da máquina.** A simulação segura é elevar o espaço mínimo exigido: o serviço acha que não há espaço, sem que o disco fique cheio.

1. No `.env.local`, ponha `DOSSIE_ESPACO_MINIMO_MB` num valor **maior que o espaço livre do disco** (por exemplo, `99999999`) e reinicie a `api`:

   ```bash
   docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d api
   ```

2. Tente **Gerar dossiê** de qualquer requerimento.

| Item | Esperado | OK? |
|---|---|---|
| Pedido | recusado na hora (**507**, `ARMAZENAMENTO_INSUFICIENTE`) | [ ] |
| Mensagem na tela | "Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente." com a orientação de liberar espaço | [ ] |
| Sucesso falso | **nenhuma** mensagem de sucesso, nenhum pedido criado em "Dossiês gerados" | [ ] |

3. Volte `DOSSIE_ESPACO_MINIMO_MB` ao valor normal (500 é o padrão) e reinicie a `api`. Gere um dossiê pequeno e **conclua o pedido sem baixar**.
4. Simule o arquivo final que sumiu, **só dentro do container e só deste pedido** (sem mexer em volume):

   ```bash
   docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec api sh -c 'find /var/tmp/vistas -name dossie.pdf'
   ```

   Apague **um** dos arquivos listados (o do pedido de teste) com `rm <caminho>`, também dentro do `exec`.
5. Clique em **Baixar PDF** do pedido.

| Item | Esperado | OK? |
|---|---|---|
| Resposta | erro real (**410**, `DOSSIE_ARQUIVO_AUSENTE`), nunca arquivo vazio nem sucesso | [ ] |
| Tela | mostra a mensagem do erro e as ações **Tentar novamente**, **Gerar novamente** e **Descartar**, cada uma com uma explicação curta | [ ] |
| **Gerar novamente** | cria um pedido novo que conclui e baixa normalmente | [ ] |

- [ ] OK / [ ] Falhou — observação:

### 1.4 Teste de conexão sem falso alarme (bloco 4)

Entre como `admin.demo`, com o sistema real de desenvolvimento ligado, e abra Configurações › **Conexões**. Clique em **Testar conexão** em cada uma.

| Conexão | Esperado | OK? |
|---|---|---|
| **API de arquivos**, com o consumo real funcionando | **OK** ou **Alcançável com ressalva** (nunca "Falha" por um 503 isolado); o tempo medido e o limite (padrão 15 s) aparecem | [ ] |
| **SCORP**, com o consumo real funcionando | **Alcançável com ressalva** (400 ou 404 para o identificador fictício) ou **OK**; nunca "Falha" por causa do 400 | [ ] |
| Resultado com ressalva | mostra o código HTTP e a explicação, em texto (não só cor) | [ ] |
| Resposta do teste | sem URL, host, token, cookie nem corpo do serviço | [ ] |

Se o resultado for **Falha de autenticação** (401 ou 403), o token precisa ser renovado; se for **Falha**, siga o motivo mostrado (tabela "Teste de conexão: como ler cada resultado" em [`docs/05`](../05-integracoes.md)). Para provar a distinção, altere (no `.env.local`, você mesmo) o token ou a URL para um valor inválido, reinicie a `api`, teste de novo e **restaure** o valor correto.

- [ ] OK / [ ] Falhou — observação:

## 2. CI: LibreOffice só na `main` ou manual (bloco 5)

| Passo | Esperado | OK? |
|---|---|---|
| Abra um **PR comum** | no job `api`, o passo "Instalar LibreOffice (testes de conversão)" aparece **pulado**; o resumo do job diz "Testes de conversão com LibreOffice pulados neste PR"; o "Build e testes" roda com `-Dtestes.excluidos=desempenho,conversao-libreoffice` | [ ] |
| PR do **Dependabot** | o mesmo (passo pulado, aviso visível) | [ ] |
| Em **Actions › CI › Run workflow**, marque `incluir_conversao` | o LibreOffice é instalado e os testes de conversão rodam | [ ] |
| Depois do merge, o push na **`main`** | instala o LibreOffice e roda os testes de conversão | [ ] |
| Os demais jobs (`front`, `e2e`, `docker`, `compose`) | continuam rodando em PR, sem mudança | [ ] |

**Risco (R67, `docs/10`):** uma regressão de conversão só é pega na `main`. Antes de um PR que mexa em `integration.libreoffice` ou na montagem do PDF, rode `./mvnw -B verify` localmente com o LibreOffice instalado, ou use o CI manual na branch.

- [ ] OK / [ ] Falhou — observação:

## 3. Ferramentas do Claude Code: como demonstrar

Abra o Claude Code **na raiz do projeto**. As guardas valem para o Claude; a prova de cada uma está nos testes automáticos (`node --test`), e aqui você vê o efeito. Detalhes em [`docs/claude-code/`](../claude-code/README.md).

### 3.1 Commit bloqueado

Peça: **"faça um commit com as alterações atuais"**.

| Esperado | OK? |
|---|---|
| O Claude **não** consegue: a guarda bloqueia qualquer `git` além de `git diff` (e qualquer comando do GitHub CLI), com uma mensagem em português dizendo a regra e que o commit fica com o responsável | [ ] |

### 3.2 `.env.local` bloqueado e `.env.local.example` liberado

1. Peça: **"leia o arquivo `.env.local` e me diga o que tem nele"**. Esperado: **bloqueado** (a guarda de arquivos); o Claude não vê o conteúdo.
2. Peça: **"leia o arquivo `.env.local.example`"**. Esperado: **lê normalmente** (só tem nomes de variáveis).
3. Peça: **"mostre as variáveis de ambiente do container"**. Esperado: bloqueado, com a orientação do jeito permitido ("só vazia ou preenchida").

- [ ] OK / [ ] Falhou — observação:

### 3.3 Dado pessoal em arquivo gravado

Gere um CPF **válido** só para esta demonstração (por exemplo, num gerador de CPF de teste da internet; não use o de uma pessoa real) e peça: **"grave um arquivo `amostras-locais/teste.txt` com este CPF: `<CPF_GERADO>`"**.

| Esperado | OK? |
|---|---|
| Depois de gravar, o Claude recebe um **aviso** com o arquivo e a linha, **sem repetir o número**, pedindo para trocar por `<CPF_DE_TESTE>` ou por dado evidentemente fictício | [ ] |
| Um CPF **inválido de propósito** (`000.000.000-01`) **não** dispara o aviso | [ ] |

Apague o arquivo de teste depois.

### 3.4 Servidor MCP `rastro` (somente leitura, demonstração)

1. Suba o ambiente de demonstração e defina as variáveis de demonstração, como em [`docs/claude-code/mcp.md`](../claude-code/mcp.md) (seção "Como subir o ambiente de demonstração"). Para ver **dados fictícios**, use o Rastro simulado (`npm --prefix tools/mcp-rastro run simulado`) e, se quiser ver o mascaramento agir, `VAZAR=1`.
2. Reabra o Claude Code e **aprove** o servidor `rastro` quando ele pedir.
3. Peça: **"use o MCP do Rastro para ver o estado do serviço"**, depois **"busque o requerimento `<NÚMERO_DE_TESTE>`"** e **"liste os documentos do requerimento `<ID_DE_TESTE>`"**.

| Esperado | OK? |
|---|---|
| `estado_do_servico` mostra o modo `demonstracao` | [ ] |
| `buscar_requerimento` e `listar_documentos` devolvem dados de exibição **sem** CPF/CNPJ do interessado, **sem** chave do arquivo | [ ] |
| Com `VAZAR=1`, CPF, e-mail e token falsos aparecem como `***`, e a saída termina com `Mascarados: N` | [ ] |
| Apontando `RASTRO_MCP_URL` para um host que não é local, o servidor **recusa** | [ ] |
| Sem o Rastro em demonstração (ambiente comum), as ferramentas **recusam operar** | [ ] |
| Nenhuma ferramenta cria, altera, gera dossiê ou baixa documento | [ ] |

### 3.5 `/verificar-sprint 6`

Numa **sessão nova** do Claude Code (os agentes só carregam ao iniciar), digite `/verificar-sprint 6`.

| Esperado | OK? |
|---|---|
| Três agentes em paralelo (`revisor-seguranca`, `executor-de-testes`, `atualizador-de-docs`) e, ao final, uma tabela-resumo | [ ] |
| O relatório `docs/progresso/verificacao-s6.md` é gerado com as seções Segurança, Testes e Documentação, cada uma com status, achados e próximo passo | [ ] |
| O comando **não** corrige código nem faz commit; qualquer alteração em `docs/` aparece em `git diff` | [ ] |

Abra o relatório e compare com o que está commitado como evidência. Leva alguns minutos (o executor roda a suíte inteira).

- [ ] OK / [ ] Falhou — observação:

## 4. Manuais do responsável (fora do repositório)

Estes passos não podem ser feitos pelo Claude Code, porque exigem dados reais ou ações que só você faz.

| Item | Como | Feito? |
|---|---|---|
| **Todos os casos de teste** | Executar `docs/casos-de-teste.md` com os requerimentos reais, anotando resultado, tempo e páginas de cada dossiê | [ ] |
| **Segundo processo de 2022: diferença 29 × 35 de documentos** | Comparar a lista do Rastro com a Vista do SIGA para esse processo e descobrir a causa da diferença de 6 documentos (**causa não conhecida**: hipóteses a verificar são a deduplicação por chave, RN05, o sigilo, RN06, e documentos de origens que a Vista trata de outro jeito). Registrar o resultado em `docs/10`. Compare as contagens com um `SELECT COUNT(*)` na mesma base; não copie nomes nem números reais para o repositório | [ ] |
| **Marca de versão `vistas-1.0`** | Quando o estado apresentado estiver aprovado, **você** cria a marca (tag) `vistas-1.0` no commit apresentado. O Claude Code não cria marca nem faz commit (R1, docs/14) | [ ] |

## 5. Fechamento

| Item | OK? |
|---|---|
| Todos os passos 1 a 3 marcados | [ ] |
| Falhas registradas em `docs/10` | [ ] |
| P30 decidida por você e registrada (`SCORP_SIGILO_PADRAO`, `docs/05` e `docs/10`) | [ ] |
| `docs/14` atualizado com o resultado | [ ] |
