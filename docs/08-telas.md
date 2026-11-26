# 08 — Telas

A referência visual vigente é o **protótipo do SIGRASTRO** (`design/sigrastro/referencia/A3-trilha-funcional.html`) e o guia do padrão visual em [design/sigrastro/LEIA-ME.md](design/sigrastro/LEIA-ME.md): tema único **Cerrado**, conceito "Trilha", barra superior com navegação horizontal e conteúdo em largura total (R2, D90). O protótipo antigo (`prototipo/siga-vistas-prototipo.html`) é histórico. Este documento define **informações e comportamento**.

**Nome do produto e do módulo (R1, R2).** O produto se chama **SIGRASTRO** (nome visível definitivo, D90; "Rastro" fica nos identificadores técnicos). O nome aparece no `<title>` (`SIGRASTRO`, e `<Tela> · SIGRASTRO` em cada rota), na marca da barra superior e do login (escudo árvore-vida e nome), e a frase "Acompanhamento de processos de licenciamento ambiental" só no login. **"Vistas do Processo" é o módulo** do dossiê e continua como sobretítulo da faixa do topo; os próximos módulos virão como Tramitação, Taxas e Painel. Nenhum brasão do Estado, logo da SEMA, "consulta ao SIGA Licenciamento" ou "(protótipo)" aparece na marca (`front/e2e/nome-do-produto.e2e.ts`, `layout-e-tema.e2e.ts`). As chaves de armazenamento do navegador (`vistas.token`, `vistas.recentes.*`) mudam só na Fase 2.

**Shell novo (R2, bloco 6; nome visível SIGRASTRO, D90).** A barra superior substitui o menu lateral: faixa sticky de 56px, fundo `--ink`, à esquerda a marca (`<app-marca>`, link para `/consulta`, rótulo "SIGRASTRO, início"), no meio a navegação horizontal (ícone e rótulo; item ativo com sublinhado `--marker` e `aria-current="page"`) e à direita o indicador de geração do dossiê, o selo do perfil, o nome, o avatar com iniciais e "Sair" (direto, sem menu suspenso). O conteúdo ocupa a largura total. Saíram o menu lateral, o botão de recolher, o véu mobile, a marca d'água do brasão, o rodapé "SEMA · Mato Grosso" e a trilha textual do topo. **Auditoria é um item único** (`/auditoria/documentos`, ativo em todo `/auditoria/*`); as visões "Documentos" e "Acessos" viram abas dentro da tela no bloco 12 (a aba Acessos só com `verAuditoriaAcessos`). Em até 900px a navegação mostra só os ícones (o rótulo continua para leitor de tela, oculto só visualmente; R2, bloco 15); em até 520px somem o selo do perfil e o nome. As permissões (RN08) seguem as do `menu.ts`.

## 1. Login (simulado)

- Usuário e senha.
- Três usuários fixos de demonstração, um por perfil: `operador.demo` (`OPERADOR`), `gestor.demo` (`OPERADOR_SIGILO`, o Gestor) e `admin.demo` (`ADMIN`). Cada um entra com um clique, e também é possível digitar usuário e senha.
- Indicação visível de que o acesso é simulado.
- **Visual (R2, bloco 8):** fundo de uma cor só (`#12302B`, sem curvas de nível), tudo centralizado em coluna: `<app-marca>` grande (escudo `clamp(96px,16vh,140px)`, nome `clamp(2.2rem,6vw,3.4rem)`), a frase "Acompanhamento de processos de licenciamento ambiental" e a caixa branca de `min(440px,100%)`. Sem brasão, sem cena e sem "(protótipo)" na marca; o aviso de acesso simulado dentro da caixa permanece. Em 390 px as margens são de 20 px e não há rolagem horizontal.
- **Menu e telas por perfil (RN08, S4):** o menu é montado a partir das `permissoes` de `/auth/me` (não do nome do perfil): `verAuditoriaAcessos` libera "Auditoria › Acessos" e `acessarConfiguracoes` libera "Configurações". Resultado por perfil:

  | Item | Operador | Gestor | Administrador |
  |---|---|---|---|
  | Consulta | sim | sim | sim |
  | Dossiês gerados | sim | sim | sim |
  | Auditoria › Documentos | sim | sim | sim |
  | Auditoria › Acessos | não | não | sim |
  | Configurações | não | não | sim |

  - O item sem permissão **não é renderizado** (nada de cadeado: o cadeado de demonstração saiu do protótipo na S4). Auditoria (documentos e acessos) e Configurações estão implementadas desde a S5.
  - **Guarda de rota:** digitar ou abrir a URL de uma tela sem permissão mostra o aviso "Seu perfil não tem permissão para acessar esta área." e volta à Consulta. A decisão usa as permissões já carregadas, sem chamada ao servidor; o back-end valida de novo cada endpoint (matriz em [07 — API](07-api.md)).
  - Entrar com outro usuário (sair e entrar de novo) atualiza o menu sem recarregar a página; sem token, qualquer tela volta ao login.
  - O aviso do Operador na Consulta ("Seu perfil consulta apenas...") ocupa um espaço reservado também para os demais perfis, para que o quadro do topo tenha a mesma altura em todos.
  - Mensagens de acesso (`mensagens-erro.ts`, sem dado do recurso): `NAO_INTERESSADO` "Seu perfil consulta apenas processos e requerimentos em que você é o interessado." (na busca por número: "Processo N não tem você como interessado.", com o número digitado pelo próprio usuário); `DOCUMENTO_SIGILOSO` "Este documento é sigiloso e não está disponível para o seu perfil." (lista, modal e gaveta); `PERFIL_SEM_PERMISSAO` "Seu perfil não tem permissão para acessar esta área." (também na guarda de rota).

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
- **Visual (R2, bloco 9):** faixa do topo com o título "Consulta"; o formulário é um painel branco no pé da faixa (os três critérios continuam); o tipo (Requerimento | Processo) é um controle segmentado com `aria-pressed`; os recentes viram a trilha vertical "Consultados por último" (linha tracejada e um círculo por item, `ul.rec-list`). Nenhum atalho "Para testar" do protótipo.
- "Consultados por último" (antes "Consultados recentemente"): atalhos para o que o usuário abriu neste navegador (até 6, guardados no `localStorage` por login: id, rótulo, número do requerimento e situação; nenhum dado do interessado). Só aparecem se houver; não há dados de exemplo. **Desempate (S2):** quando dois atalhos têm o mesmo rótulo (um processo com dois requerimentos), o título ganha o número do requerimento ("Processo 445/2022 · Req. 3391/2022") e a situação aparece em texto pequeno; atalhos antigos, sem esses campos, continuam valendo.
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

> **Visual SIGRASTRO (R2, bloco 10):** os três cartões de dados do topo (`.glass`) usam o painel claro com anel `--line`; a **Tramitação** é uma trilha vertical (linha contínua `--trail`, um nó por etapa, a etapa atual com o marcador `--marker` e borda `--ink`, linha tracejada; correção em `--warn`); fluxograma, "Onde está agora", Taxas e pagamentos e os avisos usam só tokens (informação em `--trail-tint`, atenção em `--marker-tint`/`--warn`, sempre com texto). Estrutura, textos, ordem e lógica não mudaram.

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
  - Pendência P20: o SIGA mostra "LAC" como Objetivo em licenciamento por adesão sem objetivos cadastrados; o SIGRASTRO não mostra (regra de origem ainda não levantada).
- **Responsável na tarefa atual (RN12):** a linha "Responsável" só é renderizada quando a API manda o campo, isto é, para Gestor e Administrador: o nome, "Não atribuída" ou "Responsável não identificado". Para o Operador a linha não aparece (RN08). O front não esconde dado que a API mandou: a regra é do back-end, e a tela só reflete a resposta.

### Onde está agora (S7, bloco 8)

Bloco no topo da folha do detalhe, **acima** do objeto e dos documentos (componente `OndeEstaAgoraComponent`, `GET /api/requerimentos/{id}/posicao`, docs/07 e docs/15). Fica fora do quadro do topo: **não altera a altura do quadro**. Carrega e falha sozinho; o resto do detalhe não espera por ele.

- **Ativo:** rótulo da etapa em destaque, selo de tipo ("Tarefa", "Espera", "Automática", "Emissão"), **"+N em paralelo"** (botão que expande a lista das demais etapas ativas, com `aria-expanded`), **"Complementação em andamento"** quando houver (fora do "+N"), "Desde" em tempo relativo ("há 3 dias") com a data completa no `title` e no texto acessível, "desde a última entrada nesta etapa" quando a tarefa foi recriada, "Com quem" (`responsavel.texto`: nome, senão área, senão papel) e, na multi-instância, "x de y concluídos" com os participantes (concluiu/pendente; "e mais N"). Ciclo desconhecido: só "N pendentes", sem inventar o "y". Data desconhecida: "Data não informada".
- **Avisos:** um item por aviso, com ícone **e texto** (a gravidade também é dita ao leitor de tela: "Informação:" ou "Atenção:"); `INFO` em azul, `ATENCAO` em âmbar. Tom neutro, explica sem alarmar.
- **Verificação da revisão = "Concluído" (P38, D83):** a instância ativa na "Verificação da revisão do processo" é o **arquivamento do processo concluído**, não uma etapa em andamento. A API devolve `estado: CONCLUIDO` (sem `etapaAtual`, com `final.rotulo = "Concluído"` e `final.encerradoEm` = entrada na verificação, **sem** evento de fim e **sem** `idTecnico`), e a tela mostra "Concluído", o selo "Processo concluído" (não o "Final", que é só de evento de fim) e "Concluído desde <data>", mais o aviso `CONCLUIDO_ANTES_DA_VERIFICACAO`. O resumo (processo e coluna da busca) usa o ponto de estado "encerrado" com o texto "Concluído".
- **Estados:** carregando (esqueleto com a altura reservada e `role="status"`), `ENCERRADO` (o final, selo "Final" e "Encerrado em"), `CONCLUIDO` (acima), `SEM_INSTANCIA` ("Histórico detalhado indisponível para este requerimento"; sem diagrama, o plano B é da S8), `NAO_SUPORTADO` ("Fluxo ainda não suportado pelo SIGRASTRO"), `INDISPONIVEL` e erro HTTP com **"Tentar novamente"** (o resto da tela segue normal).
- **Nunca no texto:** id técnico (`task…`, `gtw…`, `Event_…`) nem CPF. Os campos só do Administrador são ignorados pela tela.
- **Nome do servidor:** a tela mostra o que a API manda; o Operador recebe só área e papel (RN12, RN14).
- **Datas** no relógio de Cuiabá pela utilidade única (`formatarDataHora`, `tempoDesde` em `shared/formato.ts`).
- **Testes:** `onde-esta-agora.component.spec.ts` (um por estado e por selo, teclado, texto sem id técnico nem CPF), `detalhe.component.spec.ts` (posição acima do objeto e dos documentos; falha não derruba o detalhe), `relogio-de-cuiaba.spec.ts` (`tempoDesde`) e `front/e2e/onde-esta-agora.e2e.ts` (estados, teclado, altura do quadro igual, objeto e documentos sem saltar mais de 24 px ao carregar, celular sem rolagem horizontal).

### Tramitação (S8, bloco 8)

Seção **recolhível** no detalhe, **abaixo** de "Onde está agora" e **acima** do objeto e dos documentos (componente `TramitacaoComponent`, `GET /api/requerimentos/{id}/tramitacao`, docs/07 e docs/15). Aberta por padrão. Carrega e falha **sozinha**: a falha da tramitação não afeta "Onde está agora" nem os documentos, e o inverso. Também é o **equivalente textual do fluxograma** do bloco 9.

- **Lista vertical** (`ol`, da mais antiga para a mais recente; só o que de fato aconteceu): rótulo da etapa, **entrada**, **saída** e **duração legível** ("2 dias e 3 h", `duracaoLegivel` em `shared/formato.ts`), `time` com `datetime`. A passagem atual mostra "Em andamento" e o selo "Etapa atual" (borda verde).
- **Com quem esteve:** uma linha "Com quem: nome · papel" (sem nome, só o papel, para o Operador, RN12); na multi-instância, a lista de **participantes** com "Concluiu", "Pendente" ou "Encerrada sem ação" (texto, não só cor).
- **Voltas:** "2ª passagem: devolvido na validação do parecer" (motivo do back-end; sem motivo determinável, só "2ª passagem", nunca um motivo inventado). As voltas aparecem na ordem real.
- **Ramo da correção:** item recuado, borda âmbar e selo em texto "Ramo da correção".
- **Emissão do ato:** lista dos atos com o estado ("Autorização: Emitido", "Outros atos: Não concluído").
- **Selos:** "Em paralelo", "Processo concluído" (verificação da revisão), "Etapa atual".
- **Complementação paralela:** linha à parte ("desde ... até ..." ou "em andamento").
- **Final:** "Concluído em 12/03/2026" (e o selo "Final" só quando é evento de fim); avisos neutros como em "Onde está agora" (inclui `HISTORICO_TRUNCADO`).
- **Filtro "Ocultar etapas automáticas"** (ligado por padrão): tira emissão da taxa, isenção, termo gerado etc., com a contagem "N etapas automáticas ocultas"; a lista completa fica a um clique. A **etapa atual nunca é escondida**.
- **Estados:** carregando (esqueleto, `aria-busy`), erro HTTP e `INDISPONIVEL` com "Tentar novamente", `NAO_SUPORTADO`, `SEM_INSTANCIA` (selo fixo do histórico aproximado e linha do tempo simples; o acabamento e os testes do plano B são do bloco 10).
- **Acessibilidade:** seção com `aria-labelledby`, lista semântica com nome, botão Recolher/Expandir com `aria-expanded` e `aria-controls`, foco visível, gravidade dita ao leitor de tela, celular sem rolagem horizontal. **Nunca no texto:** id técnico nem CPF.
- **Testes:** `tramitacao.component.spec.ts` (22), `formato.spec.ts` (`duracaoLegivel`), `detalhe.component.spec.ts` (posição, independência), `front/e2e/tramitacao.e2e.ts` (HC01, HC03, HC04, HC07, HC10, HC12, LC03, filtro, recolher, celular, independência, Operador sem nome e a regressão da P39).

### Fluxograma simplificado (S8, bloco 9)

Dentro da seção "Tramitação", **acima** da lista (que é o equivalente textual dele): componente `FluxogramaSimplificadoComponent` (HTML, CSS e SVG simples, **sem biblioteca**), alimentado só pelas `passagens` (`fluxograma.models.ts`, `montarFluxograma`, função pura).

- **Linha reta** de caixas ligadas por setas simples; em tela estreita a linha **quebra em várias linhas** (sem rolagem horizontal da página; 360 px testado). **Só o que já passou** mais a etapa atual destacada: **nunca** etapa futura nem o mapa completo do fluxo. Sem losango: o gateway (espera de pagamento) é caixa normal com o estado. Sem seta de retorno: uma volta é **nova caixa** com "2ª passagem".
- **Quais passagens viram caixa:** tarefas humanas, esperas, a emissão do ato, as automáticas que o usuário reconhece (**Emissão da taxa**, **Emissão da taxa (correção)**, **Isenção confirmada**, **Termo de adesão gerado**) e a etapa não mapeada (aconteceu). Ficam de fora as demais automáticas (conclusão, scripts) e o ruído. Taxável: "Emissão da taxa" e "Aguardando pagamento"; isento: "Isenção confirmada" (vem da rota seguida, nunca do valor final de `statusConferencia`).
- **Caixa:** rótulo de negócio, data de entrada embaixo, **estado em texto e ícone** além da cor e da forma (concluída: borda cheia e "✓ Concluída"; atual: borda grossa e fundo, "● Etapa atual"; aguardando: borda tracejada, "◷ Aguardando") e, quando couber, "Processo concluído" (verificação da revisão = caixa própria). Multi-instância: **uma caixa** ("3 analistas"; ativa: "1 de 3 concluídos"). **Emissão do ato:** uma caixa só, com os atos que ocorreram ("Autorização, Portaria").
- **Correção:** faixa tracejada com o rótulo "Correção do requerimento" e as caixas do ramo (a taxa e o pagamento dela, quando ocorreram).
- **Final:** **bola preta** (círculo preenchido em SVG, `role="img"`, `aria-label="Final: …"`) com o **rótulo escrito em cima** e a data embaixo; só com evento de fim (`final.tipo = FINAL`). Rótulos: Concluído, Cancelado pelo cadastrante, Expirado no cadastro, Expirado na correção, Cancelado por falta de pagamento, Encerrado por taxa não paga no prazo e **Arquivado** (forma curta: o parêntese do catálogo, "Arquivado (termo não enviado no prazo)", é cortado; o texto completo continua na lista). Sem evento de fim (em andamento e **Concluído na verificação da revisão**), **sem bola**.
- **Não renderiza** para plano B, `NAO_SUPORTADO` ou `INDISPONIVEL`: a seção mostra o texto correspondente.
- **Acessibilidade:** o conjunto tem `role="group"` e `aria-label` resumido ("Fluxograma simplificado: N etapas percorridas; etapa atual: …"); cada caixa é um item de lista **focável** e a **dica** (tooltip: entrada, saída, duração, com quem) aparece no foco e no mouse, ligada por `aria-describedby`; as setas são decorativas (`aria-hidden`); contraste AA com as cores do tema. A lista da seção é o texto alternativo completo. Decisão: o `role="img"` do prompt ficou só na **bola preta**; no conjunto, caixas focáveis com dica não combinam com um único `img`.
- **Impressão:** botão **"Imprimir tramitação"** (marca o `body` com `imprimindo-tramitacao` só durante a impressão; `styles/impressao.css`): sai apenas a seção Tramitação (fluxograma e lista), sem os botões e o filtro, **legível em preto e branco** (forma e texto, não só cor: borda cheia, grossa ou tracejada; bola preta em SVG).
- **Testes:** `fluxograma.models.spec.ts` (HC01, HC02, HC03, HC07, HC12, multi-instância, dica, não mapeada, rótulos dos finais, sem caminho, propriedade "nenhuma caixa de etapa futura"), `fluxograma-simplificado.component.spec.ts` (lista semântica, foco e dica, **nenhum losango**, setas decorativas, **rótulo do final acima da bola**, faixa da correção, sem renderização sem caminho, sem id técnico nem CPF), `tramitacao.component.spec.ts` (botão de imprimir) e `front/e2e/tramitacao.e2e.ts` (HC01, HC02, HC03, HC07, HC12, HC15 com o aviso, os seis finais do Hídrico e do LAC mais o "Arquivado", dica por teclado, impressão em preto e branco por `emulateMedia`, **sem rolagem horizontal em 360 px**).
- **Orçamento do bundle:** a tela de detalhe passou a ser carregada **sob demanda** (`app.routes.ts`), porque a tramitação e o fluxograma levaram o bundle inicial a 518 kB (aviso acima de 500 kB); agora 452 kB, sem aviso.

### Plano B e estados de erro da tramitação (S8, bloco 10)

- **Plano B** (`estado = SEM_INSTANCIA`, `planoB` na resposta; requerimento anterior ao registro detalhado do fluxo): a seção mostra o **selo fixo** (`role="note"`) "Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo", a **situação no Licenciamento**, a **linha do tempo aproximada** (cada etapa com o selo "Aproximado", entrada e saída **só com a data**, e "N eventos registrados") e os **marcos** (abertura, protocolo, conclusão, cancelamento, expirações). **Sem** fluxograma, ciclos, filtro, duração, "com quem" nem id técnico. O aviso `HISTORICO_APROXIMADO` não repete o texto do selo. Evento do Licenciamento que o Rastro não conhece só é **contado** ("1 evento do Licenciamento não reconhecido não aparece aqui."), nunca mostrado cru; `eventosTruncados` avisa que a linha pode estar incompleta. **PB03** (nenhum evento): "Sem detalhamento disponível." mais a situação e as datas. O botão "Imprimir tramitação" também vale aqui.
- **`INDISPONIVEL`** nunca vira plano B: aviso neutro ("Não foi possível consultar a tramitação agora. O restante da página não foi afetado.") e **"Tentar novamente"**; sem selo de histórico aproximado. **`NAO_SUPORTADO`**: "Fluxo ainda não suportado pelo Rastro.". **`ENCERRADO`**: fluxograma com a bola preta, lista e "<final> em <data>". **`HISTORICO_TRUNCADO`**: o que coube, com o aviso de atenção.
- **Independência:** falha da tramitação não afeta "Onde está agora" nem os documentos, e o inverso (testado nos dois sentidos).
- **Testes:** `tramitacao.component.spec.ts` (PB01, PB02, PB03, selo sem repetição, truncados, cada estado, "Tentar novamente") e `front/e2e/tramitacao.e2e.ts` (PB01 a PB03, `INDISPONIVEL` sem plano B, `NAO_SUPORTADO`, `ENCERRADO`, `HISTORICO_TRUNCADO`, independência, 360 px sem rolagem horizontal).

### Taxas e pagamentos (S9, bloco 8)

Seção **recolhível** (aberta por padrão) logo abaixo da "Tramitação" e acima dos outros requerimentos do processo, no componente `app-financeiro` (`features/detalhe/financeiro.component.ts`), que lê `GET /requerimentos/{id}/financeiro` (docs/07) sozinho, sem toast.

- **Resumo:** "Situação financeira:" com um **selo de texto e ícone** (nunca só a cor): *Sem taxa*, *Isento de taxa*, *Em aberto*, *Quitado*, *Vencido* (e *Situação não determinada* para o resumo `INDETERMINADO`).
- **Avisos** (textos da API, no mesmo estilo dos avisos da tramitação): o de **"Taxa vencida em dd/mm/aaaa."** vem em destaque neutro (âmbar), com a explicação curta "O SIGA ainda não marca a taxa como vencida; o SIGRASTRO compara a data de vencimento com hoje.". A seção **não troca** a situação do Licenciamento: a linha continua dizendo "Aguardando pagamento" e ganha, ao lado, a marca vermelha "Taxa vencida" (só quando vencida por data; quando o SIGA marcar `VENCIDA`, a situação passa a dizer "Vencida" e a marca some).
- **Isenções:** uma linha por isenção, **"Isento de Taxa em dd/mm/aaaa"**. Data que não é a do próprio lançamento ganha a nota **"data a confirmar"** (a cadeia de apoio é provisória, docs/15); sem nenhuma fonte, **"— data não registrada"**.
- **Lançamentos**, em ordem de emissão, numa **tabela semântica** (legenda para leitor de tela, `th scope="col"` e a taxa como `th scope="row"`): Taxa (*1ª taxa*, *2ª taxa*, *Taxa da correção*; a isenção aparece como "Isenção"), Situação (a do Licenciamento: *Aguardando emissão*, *Aguardando pagamento*, *Paga*, *Vencida*, *Isenta*, *Cancelada*, *Situação não reconhecida*), **Emitida em**, **Vence em**, **Paga em**, **Nº do DAR**, **Valor** e **Desconto**. Lançamento com dados incompletos no SIGA leva a nota "Dados incompletos" e é mostrado como está. Data ausente é "—"; valor ausente é **"Não informado"**, nunca R$ 0,00.
- **Valores** só existem na resposta com `RASTRO_MOSTRAR_VALORES` ligado (`mostraValores`); desligado, as colunas **Valor** e **Desconto** não existem e nenhum "R$" aparece; o DAR continua. Reais pela utilidade `formatarReais` (`shared/formato.ts`, "R$ 1.250,50"); datas pela `formatarData` (data pura, fuso de Cuiabá, nunca desloca o dia). A UPF vem na resposta mas **não é mostrada**.
- **Estados:** carregando (esqueleto e `aria-busy`), `SEM_TAXA` ("Este requerimento não tem taxa registrada.", sem tabela), `INDISPONIVEL` (aviso neutro "Não foi possível consultar as taxas agora. O restante da página não foi afetado." e **"Tentar novamente"**) e erro HTTP (a mensagem do erro e "Tentar novamente"). **Independência:** falha das taxas não afeta a tramitação, "Onde está agora" nem os documentos, e o inverso.
- **Celular (até 720 px):** a tabela vira **cartões** (cada célula com seu rótulo, `data-label`; o cabeçalho de coluna fica só para leitor de tela), sem rolagem horizontal da página. Contraste AA nos selos e foco visível nos botões.
- **Nunca** mostra id de lançamento, diagnóstico (ignorados, origem da data, status de processamento), CPF nem chave de arquivo: a API só os manda ao Administrador e a tela os ignora de qualquer modo.
- **Testes:** `financeiro.component.spec.ts` (22: FIN01 a FIN06, FIN11, FIN13, valor ausente, inconsistente, parâmetro de valores, várias isenções, selos, estados, recolher, tabela semântica, nenhum campo de Administrador no DOM), `formato.spec.ts` (`formatarReais`) e `front/e2e/financeiro.e2e.ts` (FIN01, FIN03, FIN05, FIN06, FIN11, FIN13, FIN15, valores desligados, Operador, `INDISPONIVEL`, independência da tramitação, teclado, 360 px). As marcas nas passagens, no fluxograma e o aviso no "Onde está agora" são do bloco 9 (abaixo).

### Marcas financeiras na linha do tempo, no fluxograma e em "Onde está agora" (S9, bloco 9)

Vêm de `passagens[].marcasFinanceiras` da tramitação (docs/07, bloco 5) e do aviso `TAXA_VENCIDA` da posição. **Sem Camunda** (`INDISPONIVEL`, plano B, sem lançamento casado) as marcas **não aparecem** e nada é inventado; a seção "Taxas e pagamentos" segue completa por conta própria.

- **Linha do tempo** (seção "Tramitação"): cada passagem de taxa mostra suas marcas como selos de texto logo abaixo dos atos: na **Emissão da taxa**, "Emitida em dd/mm/aaaa" e "DAR <número>"; em **Aguardando pagamento**, "Vence em", "Paga em" (se houve) e "Taxa vencida em dd/mm/aaaa" (ícone de alerta, texto vermelho); em **Isenção confirmada**, "Isento de Taxa em dd/mm/aaaa" com "(data a confirmar)" quando a data é provisória (sem data, "— data não registrada"); no **ramo da correção**, a etiqueta "da correção" (âmbar) na emissão e na espera da taxa emitida depois de entrar nele. **O filtro "ocultar etapas automáticas" mantém a passagem automática que traz marca financeira** (a "Emissão da taxa" e a "Isenção confirmada" são automáticas, mas é nelas que a data, o DAR e o isento aparecem); sem marca, a automática some como antes.
- **Fluxograma simplificado:** **nenhuma caixa nova, nenhuma etapa futura, sem losango; a regra visual da S8 não muda.** A caixa **"Aguardando pagamento"** (e a da correção) ganha uma linha com "Paga em dd/mm/aaaa" ou, sem pagamento, "Vence em dd/mm/aaaa", e o selo de texto **"Taxa vencida"** (borda vermelha; na impressão, borda dupla e texto preto, legível em preto e branco); a **"Isenção confirmada"** mostra "Isento de Taxa em dd/mm/aaaa". A dica por teclado de qualquer caixa lista todas as marcas da passagem.
- **"Onde está agora":** com o aviso **"Taxa vencida em dd/mm/aaaa."** (e a explicação "O SIGA ainda não marca a taxa como vencida…"), o bloco mostra também a linha **"Situação no Licenciamento: <situação>"**, **como está** (a situação nunca é trocada); sem o aviso, a linha não aparece. Junto com `FLUXO_ENCERRADO_SEM_STATUS` (H18/FIN15) os dois avisos aparecem e o final "Encerrado por taxa não paga no prazo" não muda.
- **Testes:** `tramitacao.models.spec.ts` (texto de cada marca, data pura, filtro), `fluxograma-marcas.models.spec.ts`, `tramitacao-marcas.component.spec.ts` (HC07 + FIN11, isenção, plano B, caixas, dica), `onde-esta-agora-taxa.component.spec.ts` e `front/e2e/financeiro-tramitacao.e2e.ts` (HC07 + FIN11, H18/FIN15, plano B + FIN16, impressão em preto e branco, 360 px).

### Outros requerimentos deste processo (S7, bloco 9)

O SIGRASTRO não tem uma tela própria de processo: a busca por número de processo lista os requerimentos (1:N), e cada um abre o próprio detalhe. No detalhe de um requerimento protocolado, logo abaixo de "Onde está agora", a seção **"Outros requerimentos deste processo"** (componente `RequerimentosDoProcessoComponent`) lista os demais requerimentos do mesmo processo, pela busca por número de processo (com o RN07 do back-end: o Operador só vê os dele). Sem outros, a seção não aparece; 403/404 na lista também não mostra nada; outro erro mostra um aviso curto, sem derrubar o detalhe.

- Cada requerimento é uma linha com o número (link para o detalhe dele) e a posição **resumida** (`OndeEstaAgoraComponent` no modo `resumido`): **ponto de estado** com texto alternativo ("Estado: Em andamento", "Em andamento, com aviso", "Encerrado", "Sem histórico detalhado", "Não suportado", "Indisponível"), etapa ou final, "· com quem", selo de tipo e "+N em paralelo". O botão **"Ver posição completa"** (`aria-expanded`) abre o bloco completo ali mesmo; "Ver menos" fecha.
- **Cada posição carrega e falha sozinha:** a falha de um requerimento mostra "Não foi possível consultar" e um botão de ícone para consultar de novo só naquele item; os demais e o detalhe seguem normais.

### Coluna "Etapa atual" na busca (S7, bloco 9)

Na lista de resultados da consulta (por número de processo, CPF/CNPJ ou nome), o botão **"Mostrar etapa atual"** (`aria-pressed`) liga uma coluna opcional com a posição de cada linha (modo `celula`: ponto de estado, etapa, com quem). Desligada por padrão. Ligada, só as linhas da página exibida pedem a posição, no máximo **4 ao mesmo tempo** (fila de posições; docs/12), para não disparar dezenas de consultas ao Camunda de uma vez.

Testes: `requerimentos-do-processo.component.spec.ts` (processo com 3 outros requerimentos: um com falha, um sem instância, um encerrado), `onde-esta-agora.component.spec.ts` (modos resumido e célula, pontos de estado), `fila-de-posicoes.service.spec.ts` (limite, erro e cancelamento), `consulta.component.spec.ts` (coluna desligada por padrão; ligada respeita o limite) e `front/e2e/posicoes-do-processo.e2e.ts` (o mesmo processo, a busca com 20 linhas sem passar de 4 simultâneas, teclado e celular sem rolagem horizontal).

O requerimento vira processo após o pagamento da taxa e a conclusão do protocolo. O topo da tela mostra sempre o número do processo; o número do requerimento só aparece, com a marca "Não protocolado", enquanto não houver processo. A mesma regra vale para a navegação, os atalhos, a lista de resultados e o dossiê.

### Geometrias

Seção própria logo abaixo de Documentos (S4, RN10), a partir de `geometrias` e `totais.geometriasOmitidas` da mesma chamada da lista (docs/07). Só aparece quando há geometria listada ou omitida.

- **Lista:** nome, arquivo (com o tamanho quando a API o informa), origem e data, em ordem cronológica. Sem caixa de seleção: as geometrias **não** entram na tabela de Documentos, nos totais da seção, na seleção nem no `POST /dossies`. Substituída e indisponível aparecem esmaecidas, com "versão substituída, fora do ZIP" e "arquivo indisponível, fora do ZIP". Para Gestor e Administrador, a sigilosa vem com a marca "Sigilosa".
- **Baixar geometrias:** só aparece quando há ao menos uma vigente. Baixa o ZIP por `HttpClient` e `Blob` (token no cabeçalho, nunca na URL), com o nome do `Content-Disposition` (`geometrias-processo-<numero>-<ano>.zip`). Enquanto o servidor monta o ZIP, o botão fica "Preparando o ZIP…" e desabilitado, com "Obtendo os arquivos e montando o ZIP. Pode levar alguns instantes."; ao terminar, "Download iniciado: <nome>".
- **Erros** (no corpo da seção, sem toast, nunca o `detail` técnico), com **Tentar novamente**:
  - `GEOMETRIAS_INCOMPLETAS` (502/504): "Nenhum arquivo foi baixado: algumas geometrias não puderam ser obtidas." e a lista dos arquivos com problema (`arquivos`: nome no ZIP e motivo);
  - `GEOMETRIAS_GRANDES_DEMAIS` (413): "As geometrias passam do limite de tamanho ou de quantidade para um único download. Nenhum arquivo foi baixado.";
  - demais códigos (credencial recusada, `NAO_ENCONTRADO`...) pela mensagem comum de `mensagens-erro.ts`.
- **Operador:** "N geometria(s) sigilosa(s) não são exibidas para o seu perfil." a partir de `totais.geometriasOmitidas`, sem nome, data ou id. Com só sigilosas, a seção mostra apenas esse aviso, sem tabela e sem botão. O aviso não conta como documento indisponível.

### Documentos

Como implementado (S2), no detalhe, abaixo do objeto. Colunas: seleção, documento (**nome do arquivo** em destaque e, na linha de baixo, ID e título/tipo; S6), origem (descrição), data e sigilo. A coluna "Sem efeito" da tela atual e a situação (vigente, substituído) não são exibidas como coluna.

Comportamento:

- ordenação padrão por data crescente (RN03); clicar em **Documento**, **Origem** ou **Data** ordena (e inverte); sem data vai sempre ao final; `aria-sort` no cabeçalho;
- **ordenação** pelo nome do arquivo (sem ele, pelo título); a mesma regra vale nas geometrias, no título do modal "Visualizar" (que também mostra o título) e no detalhe da auditoria (nome do arquivo em destaque);
- **busca** por texto (título, ID e nome do arquivo, sem acento e sem diferenciar maiúsculas) e **filtro por origem** (as opções vêm dos documentos recebidos);
- todos os **selecionáveis** vêm selecionados (S2.2, RN13): vigentes e sem efeito, inclusive tipo não informado; **Seleção padrão** volta a marcar todos eles e **Limpar seleção** zera; a caixa do cabeçalho seleciona (ou desmarca) todos os **listados e selecionáveis** (respeita o filtro);
- substituídos (sem efeito) aparecem selecionados e podem ser desmarcados;
- linha de totais no cabeçalho da seção: "N documento(s) · V vigente(s) · S sem efeito · I indisponível(is)". Os três últimos são excludentes e somam N. "N selecionados" (barra) conta os itens marcados; logo ao abrir, é N menos os indisponíveis, os não PDF e, para o Operador, os sigilosos. No 769/2025, "145 documentos · 71 vigentes" com "144 selecionados" parecia não fechar porque a linha antiga não mostrava os sem efeito (selecionáveis) nem os indisponíveis; "vigentes" nunca foi o total selecionável. Com a linha nova, confira: selecionados ao abrir = itens sem bloqueio (N − I − não PDF − sigilosos bloqueados, contando cada documento uma vez);
- **indisponíveis** esmaecidos, sem seleção, com "arquivo indisponível" junto ao nome;
- **avisos no topo da seção** (S3): com a conversão ligada, planilhas, textos e imagens convertíveis vêm selecionados e o aviso diz "N documento(s) serão convertidos para PDF" (`totais.convertiveis`); documentos sem tipo informado trazem "o tipo é conferido ao gerar o dossiê". Não PDF sem conversão (por exemplo `txt`, ou qualquer não PDF com a conversão desligada ou indisponível) ficam bloqueados, com "conversão indisponível";
- **documentos pessoais do SCORP** (S4, origem `SCORP`): a coluna Origem e o modal mostram o papel que vem da API ("Pessoal · Interessado", "Pessoal · Representante legal", "Pessoal · Responsável técnico"); no filtro de origem há uma única opção **Pessoal**, que reúne os três papéis. No empate de data, o pessoal vem depois das seis origens da Vista (mesma regra do servidor). Seleção total (RN13), mascaramento e bloqueio de sigiloso (se `SCORP_SIGILO_PADRAO=true`), conversão, visualização e dossiê funcionam como nas demais origens;
- **aviso do SCORP** (`avisos` da lista): faixa discreta no topo da seção, com texto da tela (a `mensagem` da API só é usada para um código desconhecido):
  - `SCORP_INDISPONIVEL`: "Os documentos pessoais (SCORP) não puderam ser consultados agora. Os demais documentos foram listados. Atualize a página para tentar de novo.";
  - `SCORP_PARCIAL`: "Parte dos documentos pessoais (SCORP) não pôde ser consultada agora: alguns podem estar faltando. Os demais documentos foram listados.";
  - botão **Consultar novamente**, que refaz só a lista sem esconder a tabela ("Consultando…" enquanto espera): a seleção feita pelo usuário é mantida e os documentos pessoais que voltarem entram pela seleção padrão; se a nova consulta falhar, a lista e o aviso continuam como estavam.
  - O aviso **não** impede selecionar nem gerar, não altera os totais e não vai para o dossiê (a capa continua "N incluídos de M listados"; o aviso é só da tela);
- **sigilosos sem perfil** visíveis, com o nome mascarado ("Documento sigiloso"), sem seleção, com "documento sigiloso" e "Requer perfil autorizado"; Gestor e Administrador os veem e podem selecioná-los;
- contador "N selecionados" e rodapé "Exibindo X de Y";
- o que pode ou não ser selecionado vem da API (`bloqueio`); a interface só obedece, a regra é do back-end (RNF10);
- ação **Visualizar** em cada linha (menos nos indisponíveis), que abre o modal abaixo; documentos convertíveis trazem a etiqueta "será convertido para PDF";
- erro ao carregar: mensagem pelo `errorCode` no corpo da seção e "Tentar novamente"; requerimento sem documentos: "Este requerimento não tem documentos".

**Barra de seleção** (fixa na parte de baixo da seção): contador e a ação **Gerar dossiê**, habilitada com pelo menos 1 documento selecionável.

### Modal de visualização (S3, bloco 7)

Aberto pela ação **Visualizar** de cada linha (todas, menos as indisponíveis; o sigiloso sem perfil também tem a ação, mas o modal só mostra o motivo, sem chamar o servidor).

- O PDF é buscado pelo `HttpClient` em `GET /documentos/{origem}/{id}/conteudo?requerimento=…&formato=pdf` (o token vai no cabeçalho, **nunca na URL**), vira `Blob` e é exibido na moldura por URL de objeto, **revogada ao fechar** (botão, `Esc`, clique no fundo ou sair da tela). Fechar com a resposta pendente cancela a requisição.
- Carregamento: "Convertendo para PDF…" para planilhas, textos e imagens; "Carregando o documento…" para os demais.
- Documento **convertido**: faixa "Versão convertida para PDF a partir de <formato>" (cabeçalho `X-Vistas-Convertido-De`), permitindo conferir a conversão antes de gerar o dossiê.
- Erro pelo `errorCode` (nunca o `detail`), com **Tentar novamente**. Quando a conversão não vai funcionar de novo (formato sem conversão, senha, arquivo corrompido, limite excedido) o modal também oferece **Baixar original**. Formato sem conversão (415): nome, tipo e **Baixar original**. 429 (`MUITAS_CONVERSOES`): pede para tentar em instantes.
- **Baixar original** pede `formato=original` e salva o Blob com o nome do `Content-Disposition`.
- **Documento grande**: o front lê o `Content-Length` do cabeçalho da resposta e, acima de 150 MB (`LIMITE_DO_BLOB_MB`, espelho do padrão de `DOSSIE_BLOB_MAX_MB`), cancela a requisição, não exibe por Blob e oferece só **Baixar original**, com o tamanho aproximado. O contrato do conteúdo não tem ticket (como o do dossiê, D49): o original grande também passa pelo Blob, o que pesa no navegador. **NÃO CONFIRMADO:** se vale um ticket para o original (decisão para depois da homologação, `docs/10`).
- **Incluir no dossiê / Tirar do dossiê** no cabeçalho do modal, refletindo na tabela e no contador (só aparece quando o documento pode ser selecionado).

## 4. Geração do dossiê

- Ação **Gerar dossiê**, habilitada com ao menos um documento selecionado. Abre a **gaveta de geração** (sobre a barra do topo), que cria o pedido (`POST /api/dossies`) com a origem e o id dos selecionados.
- **Geração em segundo plano (S5, bloco 1):** o pedido ativo, o envio do `POST` e o polling pertencem ao serviço `AcompanhamentoDeDossieService` (singleton); a gaveta (`GavetaDossieComponent`, hospedada pelo shell) é só a janela que mostra o estado. **Clicar no fundo, Esc e "Fechar" minimizam** a gaveta: não cancelam o `POST` (se ainda em envio, o pedido nasce) nem o polling. Só **Cancelar geração** chama `DELETE`. Sair da aplicação ou trocar de usuário encerra o acompanhamento (timer, inscrições e URL de objeto).
- **Indicador na barra do topo** (visível em todas as telas enquanto houver pedido ativo, dentro da barra fixa: o quadro do topo não muda de altura, 0 px): "Gerando dossiê… 12 de 40" com barra pequena; clicar **reabre a gaveta** no mesmo pedido. Estados: andamento, **precisa da sua decisão** (destaque âmbar), **Dossiê pronto**, erro e encerrado; os três últimos ficam até o usuário abrir ou dispensar (botão "x"). Pedido cancelado, expirado ou já baixado sai do indicador ao fechar a gaveta.
- **Aviso de término:** com a gaveta fechada, concluir ou parar à espera de decisão mostra um aviso (toast, ação **Abrir**); com a gaveta aberta não há aviso. O toast ganhou a ação opcional (`ToastService.info(texto, {rotulo, aoClicar})`, 10 s).
- **Um pedido ativo por vez** no acompanhamento: **Gerar dossiê** com pedido a acompanhar (em andamento ou à espera de decisão) mostra "Já existe uma geração em andamento." com a ação **Abrir** (o servidor aceita várias gerações; a tela acompanha uma). A tela "Dossiês gerados" reaproveita o mesmo serviço: **Decidir** chama `acompanhar(id)` e não duplica o polling.
- Acompanhamento por **polling** com intervalo crescente (1 s, 1,5 s, 2,25 s... até 5 s), que **para** quando o pedido depende do usuário (concluído, aguardando decisão, cancelado, expirado) ou quando o acompanhamento é encerrado (sem timer nem inscrição pendurados): barra de progresso `processados/total` e a situação de cada documento (aguardando, obtendo, obtido, falhou, removido; falhou e removido trazem o motivo).
- Estados (S2.2; textos no mapa de mensagens da gaveta, `TITULO_DO_STATUS` e `ROTULO_DO_STATUS`): enviando ("Enviando pedido…"), na fila, obtendo documentos, **aguardando sua decisão** (título "Documentos com problema"), montando o PDF, **concluído**, **cancelado** e **expirado**. O progresso `processados/total` **não volta a zero** depois da decisão: os documentos já obtidos (e os removidos) continuam contando.
- Ao concluir:
  - **Pré-visualizar**: o PDF é buscado pelo `HttpClient` (o token vai no cabeçalho, **nunca na URL**), vira `Blob` e abre numa moldura sobre a gaveta por URL de objeto, revogada ao fechar;
  - **Baixar PDF**: também por `Blob`, com o nome do `Content-Disposition` (`dossie-processo-N-AAAA.pdf` ou `dossie-requerimento-N-AAAA.pdf`, RF18). **S6, bloco 3:** antes de entregar ao navegador, o front confere o PDF recebido (tamanho igual ao `Content-Length` e início `%PDF-`); truncado ou não-PDF, nada é salvo e aparece "O arquivo recebido está incompleto ou não é um PDF, e nada foi salvo. Clique em Tentar novamente para baixar de novo.". Inteiro, a gaveta mostra uma nota **neutra** (não de sucesso): "Arquivo entregue ao navegador. Confira o arquivo na pasta de downloads. O arquivo foi descartado do servidor: se o navegador não conseguiu salvar (por exemplo, por falta de espaço em disco), libere espaço e gere o dossiê novamente.", com **Gerar novamente** (o servidor apaga o arquivo ao fim do download, D77);
  - **dossiê grande** (acima de `DOSSIE_BLOB_MAX_MB`, S2.2, D49): sem **Pré-visualizar**, com o aviso "Dossiê grande (N MB): a pré-visualização fica desligada e o download vai direto pelo navegador"; **Baixar PDF** pede um ticket de uso único e o navegador baixa em streaming; a gaveta passa a "Download enviado ao navegador", com a mesma nota e **Gerar novamente**;
  - **erro no download ou na pré-visualização (S6, bloco 3):** a gaveta mostra o **erro real** (o `detail` do `ProblemDetail` para `ARMAZENAMENTO_INSUFICIENTE`, `DOSSIE_ARQUIVO_AUSENTE`, `DOSSIE_ARQUIVO_INVALIDO`, `DOSSIE_SEM_ARQUIVO` e `DOSSIE_EXPIRADO`; para os demais, o texto do código), **nunca** o estado de sucesso, e as ações, cada uma com uma explicação curta: **Tentar novamente** ("Baixa o mesmo arquivo de novo, sem gerar outro.", só com o pedido ainda pronto: o download que falhou antes de chegar inteiro não apagou o arquivo), **Gerar novamente** ("Cria um dossiê novo com os mesmos documentos.") e **Descartar** ("Apaga este pedido do servidor e fecha a janela.", `DELETE`). Com arquivo perdido no servidor, o andamento é consultado de novo e a gaveta passa a `FALHA`;
  - **`FALHA` (S6):** título "Não foi possível gerar o dossiê", o motivo do servidor (ex.: "Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.") e "Nenhum arquivo foi gerado e os temporários foram apagados.", com **Gerar novamente** e **Descartar**; o indicador da barra do topo fica em erro ("Falha ao gerar o dossiê") e o aviso de término traz o motivo. `POST` recusado com 507 mostra a mesma mensagem e **Tentar novamente**;
  - indicação do horário de expiração e do **tempo restante** (calculado pelo relógio de Cuiabá, qualquer que seja o fuso do navegador); ao zerar, a gaveta mostra "Dossiê expirado".
- Pedido **aguardando decisão** (S2.2, `PENDENTE_DECISAO`): a gaveta lista **cada documento com problema e o motivo**, informa quantos já foram obtidos e que não serão baixados de novo, e que nada é entregue sem a decisão. Botões (N = itens não resolvidos: em falha e, com a credencial recusada, também os ainda não tentados):
  - **Tentar novamente os N com problema** (`POST /dossies/{id}/continuacao` com `TENTAR_NOVAMENTE`; não cria outro pedido);
  - **Remover os N com problema e gerar** (`REMOVER_E_CONTINUAR`; os removidos saem também da seleção da tela e aparecem como "Removido" na lista); só aparece se algum documento já foi obtido;
  - **Cancelar** (`DELETE`).
  - No singular: "o 1 com problema". Enquanto a decisão é enviada, os botões ficam desabilitados; uma recusa (`409 DOSSIE_SEM_DECISAO_PENDENTE`) aparece na própria gaveta.
  - Falha de conversão (S3) aparece como as demais, com o motivo do conversor (ex.: "documento protegido por senha", "tempo limite da conversão excedido", "planilha sem conteúdo imprimível"); "Tentar novamente" converte de novo sem baixar de novo, e o que já foi convertido não é refeito. Nenhuma mudança de tela: o contrato do andamento é o mesmo.
- Token da API de arquivos expirado ou recusado (`CREDENCIAL_ARQUIVOS_RECUSADA`): "Token da API de arquivos expirado ou recusado. Capture um novo token, atualize o arquivo do token e clique em Tentar novamente." (nunca mostra o valor do token; o token por arquivo está em docs/05). Os mesmos botões, sem reiniciar o que já foi obtido.
- Falha na montagem: o motivo geral, com os mesmos botões ("Tentar novamente" refaz só a montagem). Em "Dossiês gerados" (S4), um pedido que expirou à espera de decisão aparece como "Expirado · nenhum arquivo gerado", sem download.
- Erro ao criar o pedido (por exemplo, `DOCUMENTO_SIGILOSO`, `NAO_INTERESSADO`, `SIGA_INDISPONIVEL`): mensagem na própria gaveta, sem toast duplicado.

## 4.1 Dossiês gerados (S4)

Item **Dossiês gerados** no menu, para os três perfis (rota `/dossies`). Quadro do topo "Dossiês gerados" com a etiqueta "Histórico em memória" e, na folha, a lista dos pedidos do `GET /dossies` (docs/07), mais recentes primeiro.

- **Colunas:** dossiê (identificação como no RF18: "Processo 2825/2025" ou "Requerimento N"), situação (etiqueta), criado em, **expira em** com "restam N min" (relógio de Cuiabá, como na gaveta) enquanto o pedido está vivo (na fila, em andamento, aguardando decisão ou pronto), documentos ("12 de 15", os incluídos de M listados, e "N com problema" quando há falhas), tamanho do PDF (quando existe) e **Gerado por**, só quando a API manda o campo (Gestor e Administrador; o Operador nunca o recebe e a coluna não existe).
- **Etiquetas:** Na fila, Obtendo documentos, Montando o PDF, Aguardando decisão, Pronto, Cancelado, Expirado, "Expirado · nenhum arquivo gerado" (expirou à espera de decisão), Arquivo baixado e Falhou (S6).
- **Ações por linha:**
  - Pronto (`temArquivo`): **Pré-visualizar** (Blob numa moldura; não aparece para dossiê grande) e **Baixar PDF** (Blob com o token no cabeçalho e o nome do `Content-Disposition`; dossiê grande, `entregaPorTicket`, por ticket de uso único e download direto, como na gaveta). Depois do download (com o PDF conferido, como na gaveta) a lista é atualizada e a linha passa a "Arquivo baixado".
  - **Falhou** (`FALHA`, S6): a etiqueta vermelha "Falhou" e, abaixo, a mensagem do servidor (`falha.mensagem`); ações **Gerar novamente** ("Abre o requerimento para gerar um dossiê novo.") e **Descartar** ("Apaga este pedido do servidor.").
  - Erro no download (S6): o erro real na linha e as ações **Tentar novamente**, **Gerar novamente** e **Descartar**, cada uma com explicação; se o servidor diz que o arquivo sumiu ou expirou, a lista é atualizada. PDF truncado nunca vai ao navegador.
  - Na fila, obtendo ou montando: **Cancelar** (`DELETE /dossies/{id}`).
  - Aguardando decisão: **Decidir**, que abre a **gaveta de geração** no pedido existente (`AcompanhamentoDeDossieService.acompanhar`: não cria outro pedido; decidir, cancelar, pré-visualizar e baixar seguem a lógica da gaveta; com outra geração a acompanhar, aparece o aviso "Já existe uma geração em andamento."). Pedido encerrado aberto assim não oferece "Gerar novamente": gerar de novo é no detalhe.
  - Todas: **Abrir requerimento** (vai ao detalhe).
  - Erro de uma ação aparece na própria linha, pelo `errorCode` (ex.: "O dossiê expirou. Gere-o novamente.").
- **Filtros:** situação (sobre a página carregada) e, para Gestor e Administrador, a caixa **Só os meus** (`somenteMeus=true`, volta à primeira página). Paginação "Anterior/Próxima" quando há mais de uma página (20 por página).
- **Atualização:** botão **Atualizar** (atualiza sem tirar a tabela da tela) e atualização automática **a cada 30 s**, que para ao sair da tela e enquanto a aba está oculta; ao voltar para a aba, atualiza na hora. Nenhum timer fica pendurado.
- **Estados:** carregando ("Carregando dossiês…"); vazio "Você ainda não gerou dossiês nesta sessão do serviço" (Operador, ou Gestor e Administrador com "Só os meus"; sem o filtro, "Nenhum dossiê foi gerado nesta sessão do serviço"); erro pela mensagem do `errorCode`, com **Tentar novamente**. A tela sempre diz que o histórico fica só na memória do serviço: some ao reiniciar, e os pedidos encerrados saem da lista um tempo depois de vencer (24 h por padrão).
- O quadro do topo tem a mesma altura nos estados vazio, erro e com itens; no celular a tabela rola dentro da moldura, sem rolagem horizontal da página.

Resumo do contrato (bloco 7): o **Operador** só recebe os próprios pedidos e nunca recebe `geradoPor` (a coluna "Gerado por" só aparece quando o campo vier); **Gestor e Administrador** têm o filtro "Só os meus" (`somenteMeus=true`). `EXPIRADO` com `arquivoGerado` falso: "Expirado · nenhum arquivo gerado"; `BAIXADO`: "Arquivo baixado". Só `temArquivo` libera Pré-visualizar e Baixar (por ticket quando `entregaPorTicket`). O histórico é em memória: some ao reiniciar o serviço, e pedidos encerrados saem da lista `DOSSIE_HISTORICO_HORAS` depois de vencer.

## 5. Auditoria

Item único "Auditoria" no menu (`/auditoria/documentos`), com duas **abas** dentro da tela, "Documentos" e "Acessos" (a aba Acessos só aparece com `verAuditoriaAcessos`; R2, bloco 12). Detalhes em [11 — Auditoria](11-auditoria.md). Uma só tela (`AuditoriaComponent`, `features/auditoria`) atende os dois grupos, escolhido por `data.grupo` da rota; o back-end decide o que cada perfil vê.

- **Documentos** (todos os perfis; rota `/auditoria/documentos`, implementada no bloco 5 da S5): consultas, visualizações, dossiês, falhas, downloads e geometrias. Gestor e Administrador veem tudo e têm o filtro "somente sigilosos". O Operador vê os eventos dos **seus** processos e requerimentos, **feitos por qualquer usuário**, com o texto da tela dizendo isso, sem o filtro "somente sigilosos" (a API o recusaria com 400) e com documentos sigilosos já mascarados pelo servidor ("Documento sigiloso").
  - **Período:** botões Hoje, Últimos 7 dias (padrão), Últimos 30 dias e Todo o período (trocar volta à primeira página).
  - **Indicadores do período:** dossiês gerados, eventos com sigilosos e usuários (os da API, que seguem o período e o perfil).
  - **Filtros:** busca por texto (espera 350 ms de pausa na digitação), tipo de evento, login do usuário e, quando permitido, "somente sigilosos"; "Limpar filtros".
  - **Lista:** data e hora com o código do evento, usuário (nome, perfil e login), evento (etiqueta), processo ou requerimento, detalhe (arquivo, texto e etiqueta "Sigiloso") e botão **Detalhe**. Evento sigiloso tem **destaque lateral** vermelho. Paginação de 20, do mais recente para o mais antigo. Carregando, vazio ("Nenhum evento encontrado") e erro com **Tentar novamente**.
  - **Gaveta de detalhe** (`AuditoriaDetalheComponent`, sobre a barra do topo): todos os campos permitidos, origem da requisição, correlação, a lista de documentos com o sigilo de cada um e, quando houver, a tabela antes e depois. Fecha com Esc e clique fora; cancela a consulta ao fechar.
  - **Exportar CSV:** `GET /auditoria/documentos/exportacao` como `Blob`, com o token no cabeçalho (nunca na URL), mesmo filtro da lista e nome do `Content-Disposition`; com `X-Vistas-Truncado: true` mostra o aviso de que o arquivo foi cortado.
  - **Faixa fixa:** "Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço."
- **Acessos** (somente Administrador): entradas, trocas de perfil, alterações de usuários e de configuração; indicadores de acessos, alterações e usuários. **Regra:** para quem não é Administrador, o item não aparece no menu (RN08; sem cadeado de demonstração desde a S4). Rota `/auditoria/acessos` (bloco 6): o **mesmo componente** da tela de documentos com `data.grupo = 'acessos'`, sem coluna de processo nem filtro de sigilo, indicadores de entradas, alterações e usuários, filtro de tipo (Acesso, Troca de perfil, Usuário alterado, Configuração alterada), detalhe com a tabela antes e depois, CSV e a mesma faixa de memória. Menu e guarda de rota pela permissão `verAuditoriaAcessos`: Operador e Gestor não veem o item e, digitando a URL, voltam à Consulta sem chamar a auditoria.
- Em ambas: período, busca, filtros por evento e usuário, gaveta de detalhe e exportação em CSV.

- **Horários (S5.1):** toda data e hora da interface é mostrada **sempre no horário de Cuiabá** (`America/Cuiaba`), seja qual for o fuso do navegador ou do computador. Valor com fuso (`-04:00` ou `Z`, como os eventos da auditoria e o último acesso) é convertido para Cuiabá; valor sem fuso (e data pura) já é de Cuiabá e não muda de dia. O ponto único é `shared/formato.ts` (`formatarData`, `formatarDataHora`, `minutosAte`); o CI roda os testes em `UTC`, `America/Cuiaba` e `Asia/Tokyo`.

## 6. Configurações

Somente Administrador. Detalhes em [12 — Configurações](12-configuracoes.md).

- Topo: usuários ativos, conexões funcionando e horas até expirar a credencial de arquivos.
- Abas (`ConfiguracoesComponent`, `role="tablist"`): Usuários e perfis, Dossiê, Conversão, Visualização, Conexões e Auditoria. Navegáveis por teclado (setas, Home e End movem o foco e a seleção; só a aba ativa entra na ordem de tabulação) e com foco visível. Faixa fixa: "Neste protótipo, as alterações ficam na memória e somem ao reiniciar o serviço."
- **Usuários e perfis (S5, bloco 9):** lista com nome (e "você" no próprio), login, CPF/CNPJ **como a API envia** (mascarado, ou "—"), perfil, situação e último acesso; usuário inativo aparece esmaecido. Perfil e situação são alterados direto na lista, com uma **confirmação curta** na própria linha ("Mudar o perfil de X para Y?", "Desativar X? Ele deixa de entrar no sistema."); só ao confirmar sai o `PATCH` (um campo por vez, um evento `USUARIO` por alteração), e cancelar volta o controle ao valor anterior. As recusas de proteção (409 `ULTIMO_ADMINISTRADOR`, `PROPRIO_USUARIO`, `LOGIN_JA_EXISTE`) mostram a mensagem em português do servidor na linha, e o valor volta ao que estava. **Inclusão por gaveta lateral** (nome, login, perfil, CPF/CNPJ): valida antes de enviar (nome obrigatório; login de 3 a 60 caracteres, único na lista; CPF/CNPJ **obrigatório para o Operador**, com máscara e dígitos verificadores) e mostra por campo a recusa 400 do servidor. O CPF/CNPJ digitado vai só no corpo do `POST`, em dígitos, e nunca é exibido de novo por inteiro.
- **Parâmetros (S5, bloco 10):** as abas **Dossiê**, **Conversão**, **Visualização**, **Conexões** (tempo limite de download) e **Auditoria** (retenção por grupo: 1, 2, 5 ou 10 anos) montam os campos a partir de `GET /configuracoes/parametros`, por `grupo`: caixa de marcação (ligado/desligado), número com a faixa, lista (retenção) ou botões de opção (modo das planilhas: "Ajustar à largura" ou "Uma página por aba", `LARGURA`/`PAGINA_POR_ABA`). Cada campo mostra a **faixa**, o **padrão do ambiente** e a **etiqueta da classe de aplicação**: "Vale já", "Vale na próxima geração" e "Exige reinício" (esta com o aviso de que o valor só vale de fato pela variável de ambiente do servidor, porque as alterações da tela somem ao reiniciar, R56); `pendenteDeReinicio` e `sobreposto` viram as etiquetas "Pendente de reinício" e "Alterado nesta sessão". As 7 origens dos documentos ficam num bloco à parte. **Restaurar padrão** (só em parâmetro sobreposto) chama o `DELETE`. A **ordem cronológica** aparece como somente leitura ("fixa, RN03"), assim como as regras de geometrias e compactados (RN10, RN11).
- **Alterações pendentes (RF28):** editar **não grava**. As pendências ficam num estado compartilhado pelas abas (`ParametrosStore`, provido na tela) e uma **barra fixa** mostra "N alterações pendentes", **Descartar** e **Salvar alterações**, que faz **um único `PUT`** com todas (número e booleano tipados). Erro de validação do servidor (`PARAMETRO_INVALIDO` com o nome em `campos`) marca o campo e **mantém as pendências**; corrigir o campo limpa o erro dele. Sucesso mostra o **resumo** do que mudou (de → para, com a classe) e o que **exige reinício**. Sair da tela com pendência pede confirmação (`pendenciasGuard`, `canDeactivate`) e recarregar a janela aciona o aviso do navegador.
- **Conexões:** banco do SIGA, API de arquivos, SCORP e taxas, cada um com o estado (configurada, não configurada, desativada) e **Testar conexão** (carregando; resultado com a **classificação em texto**, não só cor: "OK", "Alcançável com ressalva" (tom âmbar, com o código HTTP e a explicação de que a chamada usa um valor fictício), "Falha de autenticação" e "Falha", mais o tempo medido e o limite, ex.: "· HTTP 400 · 180 ms (limite 15 s)", e a mensagem do servidor; acima da lista, o tempo limite de cada chamada e que o teste usa o mesmo endereço, cliente e credencial do consumo real (S6); o botão vira "Tentar novamente" depois de qualquer resultado que não seja OK; um teste por conexão de cada vez, 409 `TESTE_DE_CONEXAO_EM_ANDAMENTO`). A API de arquivos mostra as **horas até expirar o token** e, vencido, "Capture um novo token, atualize o arquivo do token e clique em Tentar novamente." **Nenhuma credencial, URL ou host** é exibido ou editável, e o texto da aba diz isso. Taxas está desativada e o botão fica desabilitado.
- **SIGRASTRO e o financeiro (S9, bloco 10):** a aba **Rastro** ganhou os parâmetros do financeiro (**Mostrar valores das taxas**, **Limite de lançamentos de taxa por requerimento** e **Tempo limite da consulta financeira**, todos "Vale já", com faixa e padrão do ambiente) e, abaixo deles, o painel **Diagnóstico do financeiro** (somente leitura): informe o identificador do requerimento e veja os lançamentos ignorados por motivo, os mantidos e a origem da data de cada "Isento de Taxa". Só contagens e a origem da data (nada de valor, DAR, id de lançamento nem CPF); identificador vazio ou inexistente e consulta indisponível mostram aviso neutro. Detalhes em [12](12-configuracoes.md#financeiro-na-aba-rastro-s9-bloco-10). Testes: `diagnostico-financeiro.component.spec.ts` e `front/e2e/configuracoes-financeiro.e2e.ts` (a aba, um PUT único, o diagnóstico, o Operador levado à consulta, 360 px).

**Visual de Configurações (R2, bloco 13).** Só aparência: abas em `cfg-aba` (item ativo com sublinhado `--trail` e `aria-selected`, foco `--marker`), tabelas e campos com os tokens do Cerrado, tags de estado (`ok`, `warn`, `bad`, `gray`; a classe de aplicação "Vale já" é `ok`, "Exige reinício" é `warn` e "Vale na próxima geração" usa a tag padrão), cartões de conexão com o resultado do teste em `ok`/`falha`/`ressalva` (cor e texto), e a barra de alterações pendentes em `--ink`. Nota de apoio (`.nota-historico`) e caixa com rótulo (`.check`) voltaram a ter estilo na base. Não há aba Aparência. Nada de lógica, validação, serviço ou classe de teste mudou. Testes: `front/src/app/features/configuracoes/*.spec.ts` (57) e `front/e2e/configuracoes-usuarios`, `configuracoes-parametros`, `configuracoes-financeiro` e `layout-tabelas` (38, em 1024, 768 e 360 px).

## 6.0 Minha caixa (S11, blocos 2 e 7)

Rota `/caixa` (título `Minha caixa · SIGRASTRO`), item **Minha caixa** como primeiro da seção "Área de trabalho", só com `permissoes.verCaixa` (perfil Analista). Depois do login, ao abrir a raiz e quando uma rota restrita o barra, o Analista cai aqui (os demais perfis, na Consulta; digitando `/caixa` eles voltam à Consulta com o aviso de perfil). Componentes `CaixaComponent` e `CaixaMudancasComponent` (sob demanda); lê `GET /api/caixa` (instantâneo em memória, contrato em [07](07-api.md)), `GET /api/caixa/avisos`, `POST /api/caixa/avisos/visto` e `POST /api/caixa/atualizar`. Nenhuma tela lê o SIGA nem o Camunda.

- **Cabeçalho:** "Atualizado às HH:MM" (Cuiabá) e o botão **Atualizar** (202 espera uma data nova, relendo a cada 5 s por até 2 minutos; 429 mostra "Tente de novo em N segundos").
- **Resumo:** total na caixa, uma contagem por etapa de posse (análise e revisão, inclusive zeradas) e "Com o interessado (correção)".
- **Visões:** botões **Geral**, um por etapa e **Com o interessado**; pedem a etapa ao servidor (`aria-pressed` na ativa). **Busca** por nome do interessado ou número (espera 300 ms depois da última tecla, sem acento) e **ordenação** (mais tempo parado primeiro, o padrão, ou mais recente).
- **Lista:** número (link para o requerimento), **interessado** (só o nome), tipo, etapa, dias na etapa ("sem informação" quando não há dado, nunca inventado) e a marca **Novo**; itens com o interessado levam a tag "Com o interessado". Em 360 px cada linha vira um cartão, sem rolagem horizontal.
- **Estados:** `PREPARANDO` (relê sozinha), `DESATUALIZADO` (mantém a lista e avisa), `INDISPONIVEL` (aviso, sem lista), leitura parcial (`incompleta`) e **sem CPF vinculado** (mensagem "Seu usuário não está vinculado a um servidor. Peça ao Administrador.", sem lista nem caixinha).
- **Caixinha de aviso:** região viva (`role="status"`, `aria-live="polite"`) com o botão destacado "N requerimentos chegaram para você" (vem de `novos` de `GET /api/caixa`). Ao clicar abre a **lista de mudanças** (diálogo com `appFocoPreso`, Esc e clique fora): processo, interessado, etapa e **de onde veio**. Receber a lista grava o visto só daquele conjunto e a caixa se relê, então a caixinha e as marcas "Novo" somem; ao fechar, o foco vai ao botão ou, se ele sumiu, ao título da lista. Clicar em um item abre o requerimento. Se gravar o visto falhar, a lista continua visível e o aviso volta na próxima abertura.
- **Ajuda discreta:** o visto fica só em memória e zera quando o sistema reinicia (reinício = primeiro acesso, sem aviso retroativo).

Testes: `caixa.component.spec.ts` (22) e `front/e2e/caixa.e2e.ts` e `caixa-destino.e2e.ts` (caixa vazia, itens, aviso e depois sem aviso, busca, ordenação, por etapa, sem CPF vinculado, Atualizar, 360, 390 e 1280 px, nenhum CPF/CNPJ no DOM).

## 6.1 Painel do Gestor (S10, bloco 7)

Rota `/painel` (título `Painel · SIGRASTRO`), item **Painel** na seção Gestão do menu, só com `permissoes.verPainel` (Gestor e Administrador; o Operador não vê o item e, digitando a rota, volta à Consulta com o aviso de perfil, sem chamar a API). Componente `PainelComponent` (carregado sob demanda); lê `GET /api/painel` (instantâneo em memória, contrato em [07](07-api.md)) e pede `POST /api/painel/atualizacao`.

- **Cabeçalho:** "Ano de 2026 (requerimentos protocolados no ano)", "Dados de dd/mm/aaaa hh:mm", próxima atualização automática e **Atualizar agora**. O `429 ATUALIZACAO_RECENTE` mostra "Tente de novo em N segundos" (`tentarEmSegundos`). Aceita a atualização (202), a tela relê a cada 5 s, no máximo 2 minutos, até chegar uma data de dados nova.
- **Estados:** `PREPARANDO` (selo, sem gráficos e releitura a cada 5 s por até 2 minutos; depois "A leitura está demorando"), `PRONTO`, `DESATUALIZADO` ("Última leitura falhou em ...; mostrando a de ..."; os gráficos seguem na tela), `INDISPONIVEL` (selo de erro, sem gráficos) e `truncado` (aviso do servidor).
- **Gráfico 1:** barras agrupadas por mês, em SVG: **protocolados** (barra cheia, à esquerda) e **concluídos (mês aproximado)** (barra listrada, à direita), com o valor sobre cada barra; nota sobre a data aproximada e "N sem data de protocolo".
- **Gráfico 2 (pizza, S11 bloco 8, D100):** pizza em SVG; legenda com todas as etapas na ordem do catálogo (inclusive zeradas, sem fatia), com o número e a "participação na soma das etapas" (nunca "% dos requerimentos"); "Fora do catálogo", "Sem instância" e "Etapa não mapeada" na legenda (fatia quando > 0); nota quando a soma das fatias passa de `totalNaFila`; tudo zerado mostra "Nenhum requerimento em andamento".
- **Gráfico 3:** barras horizontais (15 maiores e "Outros (N servidores)"); categorias sem nome (área, interessado, não atribuída, não identificado) com ícone próprio; com `nomesOcultos`, a nota "Nomes ocultos por configuração" e nenhum nome.
- **Acessibilidade:** cada gráfico tem `role="img"` com `aria-label` resumido e **Ver como tabela** (tabela equivalente, com totais); a informação não depende só de cor (valor numérico, rótulo, barra cheia × listrada e posição); foco visível; sem rolagem horizontal em 360 px; `prefers-reduced-motion` respeitado; impressão com barras em preto e valores legíveis. Cor só por token (`styles/painel.css`); sem biblioteca de gráficos.
- **Testes:** `painel.component.spec.ts` (15), `menu.spec.ts`, `shell.component.spec.ts` e `front/e2e/painel.e2e.ts` (menu por perfil, os estados, 429, tabela igual ao gráfico, nomes ocultos, 360 e 1280 px).
- **Configurações (S10, bloco 8):** a aba **Rastro** mostra os cinco campos "Painel: …" (só Administrador; a API responde 403 para Gestor e Operador) com faixa, padrão e "Vale já", e um texto de apoio explicando cada um (o `0` no ano é o ano corrente). Detalhes em [12](12-configuracoes.md#texto-de-apoio-na-aba-rastro-s10-bloco-8).

## Padrão visual comum (R2)

Vale para todas as telas; guia completo em [design/sigrastro/LEIA-ME.md](design/sigrastro/LEIA-ME.md).

- **Faixa do topo** (Consulta, detalhe, Dossiês gerados, Auditoria, Configurações): 240px, gradiente do céu do Cerrado, paisagem `cerrado.svg` como imagem decorativa e cartão de título claro à esquerda com título e subtítulo. Sem etiqueta de tema.
- **Painéis, tags, botões e tabelas:** só tokens do Cerrado; tag sempre com texto; tabela larga rola dentro do próprio contêiner.
- **Larguras:** sem rolagem horizontal em 390, 768, 1280 e 1920 px (`e2e/layout-e-tema.e2e.ts`). Em até 900px a navegação mostra só ícones; em até 520px somem o selo do perfil e o nome.
- **Teclado e foco:** anel de foco em duas cores, diálogos com foco preso e devolvido, Esc fecha; `prefers-reduced-motion` desliga as animações.
- **Tema único:** não há seletor de tema nem aba Aparência.

## Fora do escopo

- Nenhum item adicional.

## Termos na interface

A interface **de consulta** não menciona o motor de fluxo (Camunda), **para nenhum perfil** (P39, D83): nem a busca, nem o detalhe, nem "Onde está agora", "Tramitação", mensagens de erro ou avisos. O único lugar é o teste de conexão "Fluxo (Camunda)" na aba **Conexões** das Configurações, que só o **Administrador** acessa (RN08). Regressão: `front/e2e/tramitacao.e2e.ts` percorre a busca e o detalhe de Operador, Gestor e Administrador procurando o termo (texto e HTML), `detalhe.component.spec.ts` confere o DOM e `TramitacaoHttpTest` confere as respostas da API de consulta. O bloco é chamado apenas de "Tarefa atual". A posição na tramitação (S7) se chama "Onde está agora" e usa o vocabulário do catálogo de etapas ("Análise do processo"), nunca o id técnico.

**Tabelas de Usuários e perfis e de Auditoria (S6, bloco 2).** O detalhe da auditoria (Acessos e Documentos) fica numa célula de tabela comum (a flexibilidade de linha é interna, num `div`), com largura entre 200 e 420 px, quebra de texto longo e o texto completo no `title`. Em Configurações › Usuários e perfis, até 768 px cada usuário aparece como um cartão (rótulo e valor) em vez de linha de tabela, sem rolagem horizontal; acima disso, a tabela de seis colunas. Teste: `front/e2e/layout-tabelas.e2e.ts` (geometria em 1280, 1024, 768 e 360 px).
