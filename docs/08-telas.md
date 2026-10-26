# 08 — Telas

O protótipo navegável (`prototipo/siga-vistas-prototipo.html`) é a referência visual: tema claro, paleta verde, barra lateral e padrão de quadros sobrepostos do SIGATIC, com ilustração da Cachoeira Véu de Noiva no quadro principal e, no login, ilustração de chapadas com curvas de nível. Este documento define **informações e comportamento**.

## 1. Login (simulado)

- Usuário e senha.
- Três usuários fixos de demonstração, um por perfil: `operador.demo` (`OPERADOR`), `gestor.demo` (`OPERADOR_SIGILO`, o Gestor) e `admin.demo` (`ADMIN`). Cada um entra com um clique, e também é possível digitar usuário e senha.
- Indicação visível de que o acesso é simulado.
- O menu muda conforme o perfil (RN08): o Operador e o Gestor não recebem os itens "Acessos" e "Configurações"; digitar a URL dessas telas é bloqueado (aviso e volta à Consulta). O item "Acessos" com cadeado existia só no protótipo navegável.

## 2. Consulta

Para o Operador, a consulta, os atalhos e os resultados se limitam aos processos e requerimentos em que ele é o interessado; uma busca por número de outro processo exibe "não tem você como interessado".

Critérios (mantidos da tela atual):

- tipo: Requerimento ou Processo;
- número do requerimento ou processo (máscara `N/AAAA`);
- CPF/CNPJ do interessado (máscara);
- nome do interessado.

Ações: Consultar, Limpar.

Quando a busca por interessado retorna mais de um resultado, a tela exibe uma lista com nº do requerimento, nº do processo, interessado, tipo de processo, situação e data de abertura.

Como implementado (S1):

- Validação antes de consultar: ao menos um critério; número no formato `N/AAAA`; CPF (11) ou CNPJ (14 dígitos); nome com 3 ou mais caracteres. A API valida de novo.
- Busca **só por número** com um resultado abre o detalhe direto. Qualquer outra combinação mostra a lista paginada (identificação conforme a RN04: o número do processo; "Requerimento · não protocolado" enquanto não houver processo), com "Abrir" por linha, total e navegação Anterior/Próxima.
- Mensagens da busca aparecem no próprio formulário: "Processo 2825/2025 não encontrado.", "Processo 2825/2025 não tem você como interessado." (Operador), formato inválido e "O banco do SIGA está indisponível no momento." (503).
- "Consultados recentemente": atalhos para o que o usuário abriu neste navegador (até 6, guardados no `localStorage` por login: id, rótulo, número do requerimento e situação; nenhum dado do interessado). Só aparecem se houver; não há dados de exemplo. **Desempate (S2):** quando dois atalhos têm o mesmo rótulo (um processo com dois requerimentos), o título ganha o número do requerimento ("Processo 445/2022 · Req. 3391/2022") e a situação aparece em texto pequeno; atalhos antigos, sem esses campos, continuam valendo.
- Perfil restrito (Operador) vê o aviso "Seu perfil consulta apenas processos e requerimentos em que você é o interessado."

Como ajustado (S1.1):

- **Número de processo repetido (1:N, D31):** a busca só por número de processo com mais de um resultado não abre direto: mostra a lista (com situação e abertura, para o usuário escolher) e o aviso "Este número de processo está associado a mais de um requerimento." Com 1 resultado, abre o detalhe como antes. A busca por número de requerimento continua exata.
- **Regra: o quadro do topo tem altura estável em todos os estados.** A altura do quadro (e, portanto, a escala da ilustração da cachoeira) **não depende do estado da tela**: inicial, carregando, resultados, vazio, 400, 403, 404, 503 e erro de rede medem o mesmo, em qualquer largura (1280, 1920 e 390 px). Para isso:
  - o alerta da busca ocupa um **espaço sempre reservado** dentro do cartão (`.search-feedback`, **altura fixa** de 76 px e `overflow: hidden`), suficiente para um alerta de até duas linhas; vazio, ele fica invisível, não colapsado;
  - o texto do alerta é cortado em **duas linhas** (`line-clamp: 2`; o texto inteiro fica no `title`), então nenhuma mensagem, por mais longa que seja, muda a altura;
  - a ilustração ocupa o quadro com `position: absolute; inset: 0` e o quadro tem `overflow: hidden`: como a altura não muda, a escala não muda;
  - resultados e "Nenhum resultado encontrado" aparecem na folha sobreposta, fora do quadro; o mesmo vale para o quadro do detalhe, que não tem estado variável.
  - **Causa do defeito (reincidente):** `.search-feedback` tinha só `min-height: 56px`, e a caixa do alerta de duas linhas (margem de 14 px mais a caixa) passava disso; o cartão crescia, o quadro (dimensionado pelo conteúdo, com `min-height` abaixo da altura real) crescia 14 px a 1280×720 e o SVG (`preserveAspectRatio="xMidYMid slice"`) se ampliava para cobrir a nova altura. A correção anterior só garantiu a estrutura do DOM.
  - **Como se verifica:** teste e2e que mede `getBoundingClientRect().height` do quadro e da ilustração em cada estado e exige diferença de 0 px (`npm run e2e`, `front/e2e/quadro-topo.e2e.ts`); roteiro manual no passo 9 de [`homologacao/S2-roteiro-homologacao.md`](homologacao/S2-roteiro-homologacao.md).

## 3. Detalhe do requerimento ou processo

### Dados do requerimento

Informações mantidas da tela atual:

- prioridade de atendimento (ex.: "Atendimento normal");
- tipo de requerimento;
- nº do processo (ou nº do requerimento, se ainda não protocolado);
- situação;
- data de abertura e última atualização;
- nome e CPF/CNPJ do interessado;
- tipo de processo;
- objetivos, finalidades e atividades;
- bloco Tipo / Objetivo (ex.: SUBTERRÂNEA / Captação de água subterrânea).

Informação nova:

- **tarefa atual**: nome da tarefa, desde quando e, para Gestor e Administrador, o nome do responsável (RN12). Três estados: tarefa ativa; "Sem tarefa ativa" (instância encerrada ou ainda sem fluxo); e "Tarefa indisponível no momento" quando a leitura falha (o restante do detalhe aparece normalmente).

Como implementado (S1): topo com a identificação (RN04), situação em tag, prioridade (quando mapeada) e "Não protocolado"; cartões de vidro de Interessado, Datas e Tarefa atual; bloco "Objeto do requerimento" (ver "Objeto do requerimento" abaixo e [04](04-modelo-de-dados.md)); a seção "Documentos" foi entregue na S2 (ver abaixo). Erros (403, 404, 503) aparecem no corpo da página, com "Voltar para a consulta". O topo da tela mostra a trilha `Vistas do Processo / Consulta / Processo N/AAAA`.

Como ajustado (S1.1), com o mapeamento P01:

- **Prioridade:** chip no topo, "Atendimento normal" (cinza) ou "Atendimento prioritário" (azul). No prioritário, os critérios aparecem como dica do chip e em lista ("Critérios de prioridade") no cartão de Datas e identificação.
- **Tipo do requerimento** no cartão de Datas e identificação ("—" sem dado).
- **Objeto do requerimento (A2, S2)**, em cartões que só existem quando há dados, em grade `auto-fit` (mesma altura na linha, **sem altura fixa**, sem estourar no celular). Os textos vêm do SIGA e são exibidos como estão (sem `text-transform`, sem negrito):
  - **Objetivos:** um cartão só, que une "Objetivos" e "Tipo | Objetivo" (são a mesma lista; o SIGA os mostra duplicados). Cada objetivo é uma linha, com a etiqueta pequena do tipo ("Superficial", "Subterrânea", "Barramento"; sem etiqueta para licenciamento) e o texto em peso normal, separadas por divisor fino. Com mais de 1 item, um contador no cabeçalho; com mais de 5, mostra 5 e o botão "Mostrar todos (N)" (que vira "Mostrar menos").
  - **Finalidade e atividade** (outorga): agrupado por finalidade, com o nome da finalidade como subtítulo e as atividades dela em lista; finalidade repetida aparece uma vez.
  - **Atividade** (licenciamento): a atividade é o texto principal; o grupo só aparece, como legenda "Grupo: ...", se for diferente da atividade (comparação sem acento e sem diferenciar maiúsculas).
  - **Sem nenhum dado de objeto:** um aviso único, "Sem objeto cadastrado para este requerimento". Não há cartões com "—".
  - Pendência P20: o SIGA mostra "LAC" como Objetivo em licenciamento por adesão sem objetivos cadastrados; o SIGA Vistas não mostra (regra de origem ainda não levantada).
- **Responsável na tarefa atual (RN12):** a linha "Responsável" só é renderizada quando a API manda o campo, isto é, para Gestor e Administrador: o nome, "Não atribuída" ou "Responsável não identificado". Para o Operador a linha não aparece (RN08). O front não esconde dado que a API mandou: a regra é do back-end, e a tela só reflete a resposta.

O requerimento vira processo após o pagamento da taxa e a conclusão do protocolo. O topo da tela mostra sempre o número do processo; o número do requerimento só aparece, com a marca "Não protocolado", enquanto não houver processo. A mesma regra vale para a navegação, os atalhos, a lista de resultados e o dossiê.

### Geometrias

Seção exibida quando houver SHP, KML ou KMZ vigentes: lista dos arquivos (nome, arquivo, origem, data) e ação **Baixar geometrias**, que baixa todos num ZIP. Não entram no dossiê.

### Documentos

Como implementado (S2), no detalhe, abaixo do objeto. Colunas: seleção, documento (título, ID e nome do arquivo), origem (descrição), data e sigilo. A coluna "Sem efeito" da tela atual e a situação (vigente, substituído) não são exibidas como coluna.

Comportamento:

- ordenação padrão por data crescente (RN03); clicar em **Documento**, **Origem** ou **Data** ordena (e inverte); sem data vai sempre ao final; `aria-sort` no cabeçalho;
- **busca** por texto (título, ID e nome do arquivo, sem acento e sem diferenciar maiúsculas) e **filtro por origem** (as opções vêm dos documentos recebidos);
- todos os **selecionáveis** vêm selecionados (S2.2, RN13): vigentes e sem efeito, inclusive tipo não informado; **Seleção padrão** volta a marcar todos eles e **Limpar seleção** zera; a caixa do cabeçalho seleciona (ou desmarca) todos os **listados e selecionáveis** (respeita o filtro);
- substituídos (sem efeito) aparecem selecionados e podem ser desmarcados;
- linha de totais no cabeçalho da seção: "N documento(s) · V vigente(s) · S sem efeito · I indisponível(is)". Os três últimos são excludentes e somam N. "N selecionados" (barra) conta os itens marcados; logo ao abrir, é N menos os indisponíveis, os não PDF e, para o Operador, os sigilosos. No 769/2025, "145 documentos · 71 vigentes" com "144 selecionados" parecia não fechar porque a linha antiga não mostrava os sem efeito (selecionáveis) nem os indisponíveis; "vigentes" nunca foi o total selecionável. Com a linha nova, confira: selecionados ao abrir = itens sem bloqueio (N − I − não PDF − sigilosos bloqueados, contando cada documento uma vez);
- **indisponíveis** esmaecidos, sem seleção, com "arquivo indisponível" junto ao nome;
- **não PDF** bloqueados, com "conversão disponível em breve", e um aviso no topo da seção: "N documento(s) não são PDF e entram no dossiê a partir da próxima etapa";
- **sigilosos sem perfil** visíveis, com o nome mascarado ("Documento sigiloso"), sem seleção, com "documento sigiloso" e "Requer perfil autorizado"; Gestor e Administrador os veem e podem selecioná-los;
- contador "N selecionados" e rodapé "Exibindo X de Y";
- o que pode ou não ser selecionado vem da API (`bloqueio`); a interface só obedece, a regra é do back-end (RNF10);
- **sem** a ação "Visualizar" nesta etapa (modal e conteúdo são da S3);
- erro ao carregar: mensagem pelo `errorCode` no corpo da seção e "Tentar novamente"; requerimento sem documentos: "Este requerimento não tem documentos".

**Barra de seleção** (fixa na parte de baixo da seção): contador e a ação **Gerar dossiê**, habilitada com pelo menos 1 documento selecionável.

### Modal de visualização

- Exibe o documento no visualizador de PDF do navegador.
- Documentos convertidos (planilhas, textos, imagens) são exibidos na versão em PDF, permitindo conferir a conversão antes de gerar o dossiê.
- Formatos sem conversão: mensagem com nome, tipo e opção de baixar o original.
- Indicação de carregamento enquanto o documento é obtido ou convertido.
- Ação de selecionar ou desmarcar o documento a partir do modal.

## 4. Geração do dossiê

- Ação **Gerar dossiê**, habilitada com ao menos um documento selecionado. Abre a **gaveta de geração** (sobre a barra do topo), que cria o pedido (`POST /api/dossies`) com a origem e o id dos selecionados.
- Acompanhamento por **polling** com intervalo crescente (1 s, 1,5 s, 2,25 s... até 5 s), que **para** ao chegar a um estado final ou ao fechar a gaveta (sem timer nem inscrição pendurados): barra de progresso `processados/total` e a situação de cada documento (aguardando, obtendo, incluído, falhou). Fechar a gaveta não interrompe a geração; **Cancelar geração** faz `DELETE`.
- Estados: enviando, pendente ("Na fila"), processando, **concluído**, **interrompido**, **cancelado** e **expirado**.
- Ao concluir:
  - **Pré-visualizar**: o PDF é buscado pelo `HttpClient` (o token vai no cabeçalho, **nunca na URL**), vira `Blob` e abre numa moldura sobre a gaveta por URL de objeto, revogada ao fechar;
  - **Baixar PDF**: também por `Blob`, com o nome do `Content-Disposition` (`dossie-processo-N-AAAA.pdf` ou `dossie-requerimento-N-AAAA.pdf`, RF18); o servidor apaga o arquivo ao fim do download, então a gaveta passa a "Arquivo baixado" e oferece **Gerar novamente**;
  - indicação do horário de expiração e do **tempo restante** (calculado pelo relógio de Cuiabá, qualquer que seja o fuso do navegador); ao zerar, a gaveta mostra "Dossiê expirado".
- Em caso de token da API de arquivos expirado ou recusado (`CREDENCIAL_ARQUIVOS_RECUSADA`): "Token da API de arquivos expirado ou recusado. Capture um novo token e tente novamente." (nunca mostra o valor do token) e **Tentar novamente**, sem "Remover documento".
- Em caso de falha em qualquer documento: a gaveta passa a "Geração interrompida", mostra o documento e o motivo, informa que nenhum arquivo foi gerado e oferece **Tentar novamente** (novo `POST` com a mesma seleção) ou **Remover documento e gerar** (novo `POST` sem o documento; ele também sai da seleção da tela). Em "Dossiês gerados" (S4), o pedido aparece como "Interrompido · nenhum arquivo gerado", sem download.
- Erro ao criar o pedido (por exemplo, `DOCUMENTO_SIGILOSO`, `NAO_INTERESSADO`, `SIGA_INDISPONIVEL`): mensagem na própria gaveta, sem toast duplicado.

## 5. Auditoria

Item "Auditoria" no menu, com dois subitens. Detalhes em [11 — Auditoria](11-auditoria.md).

- **Documentos** (todos os perfis): consultas, visualizações, dossiês, falhas e downloads; indicadores de dossiês gerados, eventos com sigilosos e usuários; destaque lateral nos eventos sigilosos. Gestor e Administrador veem tudo e têm o filtro "somente sigilosos". O Operador vê apenas eventos sobre processos e requerimentos vinculados a ele, com nomes de documentos sigilosos mascarados.
- **Acessos** (somente Administrador): entradas, trocas de perfil, alterações de usuários e de configuração; indicadores de acessos, alterações e usuários. **Regra:** para quem não é Administrador, o item não aparece no menu. No protótipo ele continua visível com cadeado apenas para demonstração.
- Em ambas: período, busca, filtros por evento e usuário, gaveta de detalhe e exportação em CSV.

## 6. Configurações

Somente Administrador. Detalhes em [12 — Configurações](12-configuracoes.md).

- Topo: usuários ativos, conexões funcionando e horas até expirar a credencial de arquivos.
- Abas: Usuários e perfis, Dossiê, Conversão, Conexões e Auditoria.
- Usuários: perfil e situação alterados direto na lista; inclusão por gaveta lateral.
- Parâmetros: alterações ficam pendentes numa barra fixa com "Descartar" e "Salvar alterações".

## Fora do escopo

- Nenhum item adicional.

## Termos na interface

A interface não menciona o motor de fluxo (Camunda). O bloco é chamado apenas de "Tarefa atual".
