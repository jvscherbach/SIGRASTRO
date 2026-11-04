# Roteiro de homologação da S5 — geração em segundo plano, auditoria e configurações

**Quem executa:** o responsável pelo projeto, manualmente, com dados reais de uma base de **desenvolvimento ou homologação**. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA, à API de arquivos nem ao SCORP (D27, D34). Tudo o que a S5 entrega foi testado com dados sintéticos; este roteiro confere o comportamento com os dados e os arquivos reais e fecha as pendências que só a base real resolve.

**O que a S5 entrega e este roteiro confere:**

- a geração do dossiê que **continua em segundo plano** quando a gaveta é fechada (indicador na barra do topo, aviso ao concluir e quando precisa de decisão);
- a **auditoria de documentos** (consulta, visualização, dossiê, falha, download e geometrias) e a **de acessos** (entrada, troca de perfil, alteração de usuário e de configuração), com detalhe, filtros e CSV;
- a divisão por perfil: o Operador vê só os eventos dos **seus** processos, com documento sigiloso mascarado; **Acessos** só para o Administrador;
- **Configurações**: usuários e perfis, parâmetros do dossiê, da conversão, da visualização e da auditoria, e as conexões com teste;
- tudo **em memória**: reiniciar o serviço apaga a auditoria e as alterações (comportamento documentado, não falha).

Este roteiro também fecha ou mantém a **D58** (escolha entre `LARGURA` e `PAGINA_POR_ABA` para as planilhas, aberta desde a S3) e confere a R51, a R56 e a R57.

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em "Validação da S5" de [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** neste roteiro, CPF, CNPJ e números de processo ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<ID_DO_REQUERIMENTO>`, `<REQUERIMENTO_GRANDE>`). **Nenhum PDF gerado, CSV de auditoria ou captura de tela com dados reais vai para o repositório**: guarde-os numa pasta local fora dele (por exemplo, `amostras-locais/`, que está no `.gitignore`) e apague depois.

> **Uso do `.env.local`:** você edita o `.env.local` sozinho, sem colar o conteúdo em lugar nenhum. Linhas do `.env.local` não podem começar com espaço. Os comandos abaixo só dizem **se** uma variável está preenchida, nunca o valor.

## 0. Preparação

Suba **com** o sistema real (SIGA, API de arquivos e SCORP de desenvolvimento), como no passo 2.3 do roteiro da S4, e confira que o serviço está de pé:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml ps
```

Resultado esperado: `api` e `front` em execução. Abra `http://localhost:8080`.

Escolha antes de começar (anote só os marcadores):

| Item | Anote |
|---|---|
| Requerimento **grande** (de ~100 documentos, ou o maior que você tiver): `<REQUERIMENTO_GRANDE>` | |
| Requerimento **com documento sigiloso**, cujo interessado é o CPF/CNPJ do usuário Operador: `<ID_DO_REQUERIMENTO>` | |
| Requerimento de **outro interessado**, que o Operador não pode abrir | |
| Requerimento **com planilha larga e alta** (a pior que você conhecer, para o passo 5.4) | |

Os usuários de demonstração têm o clique rápido na tela de login: `operador.demo` (Operador), `gestor.demo` (Gestor) e `admin.demo` (Administrador). Para que o Operador veja seus processos, o CPF/CNPJ dele precisa ser o do interessado do requerimento escolhido (ajuste no cadastro de demonstração, se for o caso).

- [ ] OK / [ ] Falhou — observação:

## 1. Geração em segundo plano

Entre como `gestor.demo`.

### 1.1 Fechar a gaveta durante a geração

1. Abra `<REQUERIMENTO_GRANDE>`, deixe a seleção padrão e clique em **Gerar dossiê**.
2. Com a gaveta aberta e o andamento correndo, **clique fora da gaveta** (no fundo escuro).
3. Navegue por outras telas (Consulta, Dossiês gerados) e volte.

| Item | Esperado | OK? |
|---|---|---|
| Clique fora | a gaveta **minimiza**; a geração não para e nada é cancelado | [ ] |
| Indicador | na barra do topo aparece "Gerando dossiê… N de M" com uma barra pequena, em todas as telas, e o N **avança** | [ ] |
| Quadro do topo | a altura do quadro da tela não muda com o indicador (compare com e sem) | [ ] |
| Esc e "Fechar" | também minimizam (repita com outro dossiê) | [ ] |
| Aviso | ao concluir, com a gaveta fechada, aparece o aviso "Dossiê pronto para baixar." com o botão **Abrir**, e o indicador vira "Dossiê pronto" | [ ] |
| Reabrir | **Abrir** (ou clicar no indicador) reabre a gaveta **no mesmo pedido**, e **Baixar PDF** entrega o arquivo | [ ] |
| Dispensar | o "x" do indicador o tira da barra | [ ] |

### 1.2 Fechar no instante do envio

1. Abra de novo o requerimento grande e clique em **Gerar dossiê**.
2. **Imediatamente**, antes de aparecer o primeiro "Obtido", clique fora da gaveta (ou aperte Esc).
3. Abra **Dossiês gerados**.

| Item | Esperado | OK? |
|---|---|---|
| Pedido criado | o pedido **existe** na lista de "Dossiês gerados" e está andando (a requisição de envio não foi cancelada) | [ ] |
| Indicador | aparece e avança | [ ] |

### 1.3 Falha provocada e decisão com a gaveta fechada

Provoque uma falha como no roteiro da S2.2 (por exemplo, invalide o token da API de arquivos só no tempo da geração e depois restaure, sem abrir o valor do `.env.local`), gere um dossiê e **feche a gaveta**.

| Item | Esperado | OK? |
|---|---|---|
| Aviso | "A geração do dossiê precisa da sua decisão." com **Abrir**; o indicador fica em destaque âmbar ("precisa da sua decisão") | [ ] |
| Abrir | a gaveta mostra "Documentos com problema" e as opções de decisão (Tentar novamente, Remover e gerar, Cancelar) | [ ] |
| Um pedido por vez | com um pedido ativo, **Gerar dossiê** em outro requerimento mostra "Já existe uma geração em andamento." com **Abrir** | [ ] |
| Cancelar geração | só este botão cancela; fechar a gaveta nunca cancela | [ ] |
| Sair | **Sair** do sistema com um pedido ativo faz o indicador sumir (o servidor continua o pedido até expirar) | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 2. Auditoria de documentos

Faça as ações abaixo com **cada perfil** (`operador.demo`, `gestor.demo`, `admin.demo`), sempre sobre `<ID_DO_REQUERIMENTO>` (o do interessado do Operador, com documento sigiloso):

1. abrir o detalhe do requerimento;
2. **Visualizar** um documento comum e, quando o perfil puder, um sigiloso;
3. gerar um dossiê e baixar o PDF;
4. baixar as geometrias.

Depois entre como `admin.demo` e abra **Auditoria › Documentos** (período "Últimos 7 dias").

| Evento esperado | Quando | Conferir | OK? |
|---|---|---|---|
| Consulta | ao abrir o detalhe | processo ou requerimento e usuário corretos; **um** evento por abertura | [ ] |
| Visualização | ao ver o conteúdo | nome do arquivo do documento | [ ] |
| Dossiê gerado | ao terminar a geração | "N documento(s), K sigiloso(s)"; o nome do arquivo do dossiê; **um** evento mesmo se a gaveta ficou fechada | [ ] |
| Falha na geração | na falha provocada do passo 1.3 | **um evento por documento com falha** e o motivo, sem caminho nem chave | [ ] |
| Download | ao baixar o PDF | uma vez; **pré-visualizar não gera** | [ ] |
| Geometrias baixadas | ao baixar o ZIP | quantidade de arquivos e quantas sigilosas | [ ] |
| Acesso negado | o Operador tenta abrir o requerimento de outro interessado (403) | **não** gera evento de sucesso (a tentativa negada não é registrada: R51) | [ ] |

Na tela, confira também:

| Item | Esperado | OK? |
|---|---|---|
| Indicadores | dossiês gerados, eventos com sigilosos e usuários batem com o que você fez | [ ] |
| Destaque | eventos sigilosos têm a marca lateral vermelha e a etiqueta "Sigiloso" | [ ] |
| Detalhe | **Detalhe** abre a gaveta com usuário, perfil, números, origem da requisição, correlação e, em Dossiê gerado, a lista de documentos **na ordem do PDF, com o sigilo de cada um** | [ ] |
| Filtros | período, texto, tipo de evento, login do usuário e "Somente sigilosos" mudam a lista; a busca ignora maiúsculas e acentos | [ ] |
| Faixa de memória | "Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço." visível | [ ] |

### 2.1 Exportar CSV e abrir no Excel

Clique em **Exportar CSV** (com o filtro "Somente sigilosos" e depois sem filtro) e abra o arquivo no Excel (duplo clique).

| Item | Esperado | OK? |
|---|---|---|
| Nome | `auditoria-documentos-AAAAMMDD.csv` | [ ] |
| Acentos | "Visualização", "Dossiê gerado" aparecem certos (UTF-8 com BOM) | [ ] |
| Separador | as colunas se separam sozinhas (`;`) | [ ] |
| Mesmo filtro | o CSV tem as mesmas linhas que a lista filtrada | [ ] |
| Fórmula | num documento de teste cujo **título comece com `=`** (por exemplo, `=1+1`, criado por você num ambiente de teste), a célula aparece como texto `'=1+1`, **sem calcular** | [ ] |
| Sem dado a mais | o arquivo não tem CPF/CNPJ, token, chave de arquivo nem caminho | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 3. Divisão por perfil

| Item | Como conferir | Esperado | OK? |
|---|---|---|---|
| Operador vê o dos seus processos, de qualquer usuário | gere um dossiê do `<ID_DO_REQUERIMENTO>` como `gestor.demo`; entre como `operador.demo` e abra Auditoria › Documentos | aparecem os eventos feitos pelo Gestor sobre o requerimento dele; o texto da tela diz "Você vê os eventos dos seus processos..." | [ ] |
| Operador não vê o de outro interessado | faça uma consulta do requerimento de outro interessado como `gestor.demo` | **nenhum** evento dele aparece para o Operador, nem nos indicadores | [ ] |
| Sigiloso mascarado | procure, como Operador, o evento do documento sigiloso | aparece "Documento sigiloso", **sem nome de arquivo** nem extensão, na lista, no detalhe e no CSV | [ ] |
| Busca não revela sigiloso | como Operador, busque pelo **título** e pelo **nome do arquivo** do documento sigiloso | a busca **não encontra** o evento | [ ] |
| Sem "somente sigilosos" | tela do Operador | o filtro não é oferecido | [ ] |
| Gestor e Administrador | mesma lista | veem **todos** os eventos, com os nomes dos sigilosos, e têm "Somente sigilosos" | [ ] |
| Acessos só do Administrador | menu de cada perfil | **Auditoria › Acessos** só para o `admin.demo`; como Operador e Gestor, digitar `http://localhost:8080/auditoria/acessos` avisa "Seu perfil não tem permissão..." e volta à Consulta, sem chamada a `/api/auditoria` na aba Rede | [ ] |
| Auditoria de acessos | como `admin.demo`, abra Auditoria › Acessos | veja "Acesso" (login), "Troca de perfil" (entre com um perfil diferente do da sessão aberta) e, depois do passo 4, "Usuário alterado" | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 4. Usuários e perfis

Entre como `admin.demo` e abra **Configurações › Usuários e perfis**.

| Passo | Esperado | OK? |
|---|---|---|
| Incluir um usuário **Operador** sem CPF/CNPJ | a gaveta recusa: "O CPF/CNPJ é obrigatório para o Operador." | [ ] |
| Incluir com `<CPF_DE_TESTE>` inválido (dígito errado) | "CPF/CNPJ inválido." | [ ] |
| Incluir com login já existente | "Já existe um usuário com este login." | [ ] |
| Incluir um Operador válido (login `teste.operador`, CPF/CNPJ de teste válido) | aparece na lista; o CPF/CNPJ vem **mascarado** (`***.456.789-**`), nunca completo | [ ] |
| Mudar o perfil de `teste.operador` para Gestor | pergunta "Mudar o perfil...?", ao confirmar grava e avisa | [ ] |
| Desativar `teste.operador` | pergunta, confirma, a linha fica esmaecida | [ ] |
| Último Administrador | tente rebaixar ou desativar o `admin.demo` (se for o único ativo) | recusa com a mensagem do servidor; o valor volta | [ ] |
| Próprio usuário | como `admin.demo`, tente se desativar (com outro Administrador ativo cadastrado) | recusa: "Não é possível desativar o próprio usuário." | [ ] |
| Entrar com o usuário recém-incluído | **Sair** e entre com `teste.operador` e a senha única (`VISTAS_DEMO_SENHA`) enquanto ele está ativo | entra e vê o menu do perfil dele | [ ] |
| Entrar com o desativado | desative-o de novo e tente entrar | falha com a mensagem de credencial inválida, **sem dizer que está inativo** | [ ] |
| Sessão aberta | com `teste.operador` logado em outra janela anônima, mude o perfil dele | na **próxima ação** a janela já vale com o perfil novo (ou perde o acesso, se desativado), sem sair | [ ] |
| Eventos | Auditoria › Acessos, tipo "Usuário alterado" | **um evento por alteração**, com antes e depois (perfil e situação); a inclusão registra "incluído" e **não** traz CPF/CNPJ | [ ] |
| Faixa de memória | topo da tela | "...as alterações ficam na memória e somem ao reiniciar o serviço." | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 5. Parâmetros

Ainda como `admin.demo`, em **Configurações**.

### 5.1 Salvar e o evento único

1. Aba **Dossiê**: troque a **Expiração do dossiê** de 30 para 45 min e desligue **Sumário**.
2. Aba **Conversão**: marque **Uma página por aba** em Planilhas.
3. Observe a barra inferior, depois clique em **Salvar alterações**.

| Item | Esperado | OK? |
|---|---|---|
| Pendências | a barra mostra "3 alterações pendentes" e nada foi gravado antes de salvar (recarregue a janela: o navegador pergunta antes de perder) | [ ] |
| Resumo | depois de salvar aparece "3 alterações salvas" com "de → para" | [ ] |
| **Evento único** | Auditoria › Acessos, tipo "Configuração alterada": **um só** evento para o salvamento, com a lista antes e depois (os 3 itens) | [ ] |
| Sem mudança, sem evento | salve de novo sem alterar nada: nenhum evento novo | [ ] |
| Erro de faixa | digite 999 na expiração e salve: o campo é marcado com o limite, **nada** é gravado e nenhum evento nasce | [ ] |
| Sair com pendência | com uma alteração pendente, clique em outro item do menu: pede confirmação | [ ] |

### 5.2 Classes de aplicação

| Parâmetro | Como conferir | Esperado | OK? |
|---|---|---|---|
| **Vale já** (ex.: Vida do cache da visualização, retenção, origens) | altere e use | vale na próxima leitura | [ ] |
| **Vale na próxima geração** (ex.: expiração, sumário, modo das planilhas) | inicie um dossiê, **altere** a expiração durante a geração, termine; depois gere outro | o pedido em andamento **mantém** o valor antigo; o novo pedido usa o novo | [ ] |
| **Exige reinício** (ex.: Gerações simultâneas) | altere para 4 | aparece "Pendente de reinício" e o aviso de que só vale pela variável de ambiente; o valor em vigor não muda; o evento registra a alteração | [ ] |
| Restaurar padrão | em um parâmetro alterado, **Restaurar padrão** | volta ao valor do ambiente e a etiqueta "Alterado nesta sessão" some | [ ] |

### 5.3 Origens e retenção

| Item | Esperado | OK? |
|---|---|---|
| Desligar uma origem (por exemplo, Juntadas) | some da lista de documentos do requerimento na hora (recarregue a seção) e fora do dossiê | [ ] |
| Retenção | em Auditoria, as opções são 1, 2, 5 ou 10 anos por grupo | [ ] |

### 5.4 `LARGURA` × `PAGINA_POR_ABA` na pior planilha (fecha a D58, se ainda aberta)

Com o requerimento da planilha mais larga e alta, gere o dossiê **duas vezes**, trocando o modo em **Conversão › Planilhas** (a troca vale na próxima geração):

| Modo | Legível? (texto pequeno demais?) | Nada cortado? (colunas e **última linha**) | Páginas | Tempo | OK? |
|---|---|---|---|---|---|
| Ajustar à largura (`LARGURA`) | | | | | [ ] |
| Uma página por aba (`PAGINA_POR_ABA`) | | | | | [ ] |

Registre a escolha e o motivo em `docs/09` (D58) e em `docs/12`. Se as duas atenderem, o padrão `LARGURA` continua.

- [ ] OK / [ ] Falhou — observação:

## 6. Conexões

Em **Configurações › Conexões**:

| Item | Esperado | OK? |
|---|---|---|
| Lista | Banco do SIGA, API de arquivos, SCORP e Taxas, cada um com o estado (configurada, não configurada, desativada) | [ ] |
| Banco do SIGA | **Testar conexão**: "Conexão funcionando" com o tempo em ms | [ ] |
| API de arquivos | **Testar conexão**: sucesso com o tempo, e **"Token expira em N h"** | [ ] |
| SCORP | **Testar conexão**: sucesso (ou "não configurado", se `SCORP_API_URL` estiver vazia) | [ ] |
| Taxas | desativada, botão desabilitado | [ ] |
| Token vencido | (opcional) com o token vencido, aparece "Token vencido" e a orientação "Capture um novo token, atualize o arquivo do token e clique em Tentar novamente." | [ ] |
| Um teste por vez | cliques repetidos enquanto testa não disparam testes extras | [ ] |
| Tempo limite de download | editável (10 a 300 s), vale na próxima geração | [ ] |

**Nenhuma credencial na tela nem na rede.** Abra DevTools › Rede, limpe a lista e use **Testar conexão** nas quatro conexões; abra `GET /api/configuracoes/conexoes` e `POST .../teste` em "Resposta":

| Item | Esperado | OK? |
|---|---|---|
| Resposta | só nome, rótulo, estado, tempo, mensagem e horas até expirar: **sem URL, host, token, cookie, senha ou caminho** | [ ] |
| Tela | nenhum campo de senha, token ou endereço; o texto da aba diz que a tela não as exibe nem as edita | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 7. Reinício do serviço

Anote antes: quantos eventos há em Auditoria › Documentos e Acessos, e as alterações feitas nos passos 4 e 5.

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml restart api
```

Aguarde o serviço voltar (a tela de login volta a responder) e entre de novo como `admin.demo`.

| Item | Esperado (comportamento documentado, não é falha) | OK? |
|---|---|---|
| Auditoria | as duas listas começam **vazias** (só o novo "Acesso" do login), e a tela diz que os registros ficam na memória | [ ] |
| Usuários | `teste.operador` **não existe mais**; voltam só os três de demonstração | [ ] |
| Parâmetros | voltam aos valores do ambiente; "Alterado nesta sessão" e "Pendente de reinício" somem | [ ] |
| Dossiês gerados | o histórico em memória também foi apagado | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 8. Conferências finais

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs api > amostras-locais/api.log
```

```bash
grep -c -i -E "bearer |authorization|cookie|senha|password|chave-interna" amostras-locais/api.log
```

```bash
git check-ignore -v .env.local
```

```bash
git status --short
```

| Item | Esperado | OK? |
|---|---|---|
| Logs | o `grep -c` devolve **0**; se devolver algo, **não copie a linha**: abra pendência com o número da linha | [ ] |
| Eventos de falha nos logs | não há token, chave de arquivo nem caminho do servidor | [ ] |
| `.env.local` | `git check-ignore` mostra a regra que o ignora | [ ] |
| Repositório limpo | `git status` não mostra `.env.local`, PDFs, CSVs nem `amostras-locais/` | [ ] |
| Dado real | nenhum CPF, CNPJ, nome ou número de processo real em arquivo versionado (revise o `git diff` antes de commitar) | [ ] |

Para derrubar os containers (**sem** `-v`):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml down
```

- [ ] OK / [ ] Falhou — observação:

## Fechamento

1. Preencha a tabela "Validação da S5" em [`docs/14`](../14-plano-de-sprints.md).
2. Registre a escolha da D58 em [`docs/09`](../09-decisoes.md) e [`docs/12`](../12-configuracoes.md).
3. Anote resultados e tempos nos casos de [`docs/casos-de-teste.md`](../casos-de-teste.md).
4. Abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para tudo o que falhou (com o número do passo).
