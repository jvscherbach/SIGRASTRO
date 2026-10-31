# 08 — Telas

O protótipo navegável (`prototipo/siga-vistas-prototipo.html`) é a referência visual: tema claro, paleta verde, barra lateral e padrão de quadros sobrepostos do SIGATIC, com ilustração da Cachoeira Véu de Noiva no quadro principal e, no login, ilustração de chapadas com curvas de nível. Este documento define **informações e comportamento**.

## 1. Login (simulado)

- Usuário e senha.
- Três usuários fixos de demonstração, um por perfil: `operador.demo` (`OPERADOR`), `gestor.demo` (`OPERADOR_SIGILO`, o Gestor) e `admin.demo` (`ADMIN`). Cada um entra com um clique, e também é possível digitar usuário e senha.
- Indicação visível de que o acesso é simulado.
- **Menu e telas por perfil (RN08, S4):** o menu é montado a partir das `permissoes` de `/auth/me` (não do nome do perfil): `verAuditoriaAcessos` libera "Auditoria › Acessos" e `acessarConfiguracoes` libera "Configurações". Resultado por perfil:

  | Item | Operador | Gestor | Administrador |
  |---|---|---|---|
  | Consulta | sim | sim | sim |
  | Dossiês gerados | sim | sim | sim |
  | Auditoria › Documentos | sim | sim | sim |
  | Auditoria › Acessos | não | não | sim |
  | Configurações | não | não | sim |

  - O item sem permissão **não é renderizado** (nada de cadeado: o cadeado de demonstração saiu do protótipo na S4). Auditoria e Configurações continuam "Em construção" (S5) para quem tem permissão.
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

Seção própria logo abaixo de Documentos (S4, RN10), a partir de `geometrias` e `totais.geometriasOmitidas` da mesma chamada da lista (docs/07). Só aparece quando há geometria listada ou omitida.

- **Lista:** nome, arquivo (com o tamanho quando a API o informa), origem e data, em ordem cronológica. Sem caixa de seleção: as geometrias **não** entram na tabela de Documentos, nos totais da seção, na seleção nem no `POST /dossies`. Substituída e indisponível aparecem esmaecidas, com "versão substituída, fora do ZIP" e "arquivo indisponível, fora do ZIP". Para Gestor e Administrador, a sigilosa vem com a marca "Sigilosa".
- **Baixar geometrias:** só aparece quando há ao menos uma vigente. Baixa o ZIP por `HttpClient` e `Blob` (token no cabeçalho, nunca na URL), com o nome do `Content-Disposition` (`geometrias-processo-<numero>-<ano>.zip`). Enquanto o servidor monta o ZIP, o botão fica "Preparando o ZIP…" e desabilitado, com "Obtendo os arquivos e montando o ZIP. Pode levar alguns instantes."; ao terminar, "Download iniciado: <nome>".
- **Erros** (no corpo da seção, sem toast, nunca o `detail` técnico), com **Tentar novamente**:
  - `GEOMETRIAS_INCOMPLETAS` (502/504): "Nenhum arquivo foi baixado: algumas geometrias não puderam ser obtidas." e a lista dos arquivos com problema (`arquivos`: nome no ZIP e motivo);
  - `GEOMETRIAS_GRANDES_DEMAIS` (413): "As geometrias passam do limite de tamanho ou de quantidade para um único download. Nenhum arquivo foi baixado.";
  - demais códigos (credencial recusada, `NAO_ENCONTRADO`...) pela mensagem comum de `mensagens-erro.ts`.
- **Operador:** "N geometria(s) sigilosa(s) não são exibidas para o seu perfil." a partir de `totais.geometriasOmitidas`, sem nome, data ou id. Com só sigilosas, a seção mostra apenas esse aviso, sem tabela e sem botão. O aviso não conta como documento indisponível.

### Documentos

Como implementado (S2), no detalhe, abaixo do objeto. Colunas: seleção, documento (título, ID e nome do arquivo), origem (descrição), data e sigilo. A coluna "Sem efeito" da tela atual e a situação (vigente, substituído) não são exibidas como coluna.

Comportamento:

- ordenação padrão por data crescente (RN03); clicar em **Documento**, **Origem** ou **Data** ordena (e inverte); sem data vai sempre ao final; `aria-sort` no cabeçalho;
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
  - **Baixar PDF**: também por `Blob`, com o nome do `Content-Disposition` (`dossie-processo-N-AAAA.pdf` ou `dossie-requerimento-N-AAAA.pdf`, RF18); o servidor apaga o arquivo ao fim do download, então a gaveta passa a "Arquivo baixado" e oferece **Gerar novamente**;
  - **dossiê grande** (acima de `DOSSIE_BLOB_MAX_MB`, S2.2, D49): sem **Pré-visualizar**, com o aviso "Dossiê grande (N MB): a pré-visualização fica desligada e o download vai direto pelo navegador"; **Baixar PDF** pede um ticket de uso único e o navegador baixa em streaming; a gaveta passa a "Download entregue ao navegador";
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
- **Etiquetas:** Na fila, Obtendo documentos, Montando o PDF, Aguardando decisão, Pronto, Cancelado, Expirado, "Expirado · nenhum arquivo gerado" (expirou à espera de decisão) e Arquivo baixado.
- **Ações por linha:**
  - Pronto (`temArquivo`): **Pré-visualizar** (Blob numa moldura; não aparece para dossiê grande) e **Baixar PDF** (Blob com o token no cabeçalho e o nome do `Content-Disposition`; dossiê grande, `entregaPorTicket`, por ticket de uso único e download direto, como na gaveta). Depois do download a lista é atualizada e a linha passa a "Arquivo baixado".
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

Item "Auditoria" no menu, com dois subitens. Detalhes em [11 — Auditoria](11-auditoria.md). Uma só tela (`AuditoriaComponent`, `features/auditoria`) atende os dois grupos, escolhido por `data.grupo` da rota; o back-end decide o que cada perfil vê.

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

## 6. Configurações

Somente Administrador. Detalhes em [12 — Configurações](12-configuracoes.md).

- Topo: usuários ativos, conexões funcionando e horas até expirar a credencial de arquivos.
- Abas (`ConfiguracoesComponent`, `role="tablist"`): Usuários e perfis, Dossiê, Conversão, Visualização, Conexões e Auditoria. Navegáveis por teclado (setas, Home e End movem o foco e a seleção; só a aba ativa entra na ordem de tabulação) e com foco visível. Faixa fixa: "Neste protótipo, as alterações ficam na memória e somem ao reiniciar o serviço."
- **Usuários e perfis (S5, bloco 9):** lista com nome (e "você" no próprio), login, CPF/CNPJ **como a API envia** (mascarado, ou "—"), perfil, situação e último acesso; usuário inativo aparece esmaecido. Perfil e situação são alterados direto na lista, com uma **confirmação curta** na própria linha ("Mudar o perfil de X para Y?", "Desativar X? Ele deixa de entrar no sistema."); só ao confirmar sai o `PATCH` (um campo por vez, um evento `USUARIO` por alteração), e cancelar volta o controle ao valor anterior. As recusas de proteção (409 `ULTIMO_ADMINISTRADOR`, `PROPRIO_USUARIO`, `LOGIN_JA_EXISTE`) mostram a mensagem em português do servidor na linha, e o valor volta ao que estava. **Inclusão por gaveta lateral** (nome, login, perfil, CPF/CNPJ): valida antes de enviar (nome obrigatório; login de 3 a 60 caracteres, único na lista; CPF/CNPJ **obrigatório para o Operador**, com máscara e dígitos verificadores) e mostra por campo a recusa 400 do servidor. O CPF/CNPJ digitado vai só no corpo do `POST`, em dígitos, e nunca é exibido de novo por inteiro.
- **Parâmetros (S5, bloco 10):** as abas **Dossiê**, **Conversão**, **Visualização**, **Conexões** (tempo limite de download) e **Auditoria** (retenção por grupo: 1, 2, 5 ou 10 anos) montam os campos a partir de `GET /configuracoes/parametros`, por `grupo`: caixa de marcação (ligado/desligado), número com a faixa, lista (retenção) ou botões de opção (modo das planilhas: "Ajustar à largura" ou "Uma página por aba", `LARGURA`/`PAGINA_POR_ABA`). Cada campo mostra a **faixa**, o **padrão do ambiente** e a **etiqueta da classe de aplicação**: "Vale já", "Vale na próxima geração" e "Exige reinício" (esta com o aviso de que o valor só vale de fato pela variável de ambiente do servidor, porque as alterações da tela somem ao reiniciar, R56); `pendenteDeReinicio` e `sobreposto` viram as etiquetas "Pendente de reinício" e "Alterado nesta sessão". As 7 origens dos documentos ficam num bloco à parte. **Restaurar padrão** (só em parâmetro sobreposto) chama o `DELETE`. A **ordem cronológica** aparece como somente leitura ("fixa, RN03"), assim como as regras de geometrias e compactados (RN10, RN11).
- **Alterações pendentes (RF28):** editar **não grava**. As pendências ficam num estado compartilhado pelas abas (`ParametrosStore`, provido na tela) e uma **barra fixa** mostra "N alterações pendentes", **Descartar** e **Salvar alterações**, que faz **um único `PUT`** com todas (número e booleano tipados). Erro de validação do servidor (`PARAMETRO_INVALIDO` com o nome em `campos`) marca o campo e **mantém as pendências**; corrigir o campo limpa o erro dele. Sucesso mostra o **resumo** do que mudou (de → para, com a classe) e o que **exige reinício**. Sair da tela com pendência pede confirmação (`pendenciasGuard`, `canDeactivate`) e recarregar a janela aciona o aviso do navegador.
- **Conexões:** banco do SIGA, API de arquivos, SCORP e taxas, cada um com o estado (configurada, não configurada, desativada) e **Testar conexão** (carregando, sucesso com o tempo de resposta, erro com a mensagem do servidor; o botão vira "Tentar novamente" depois de uma falha; um teste por conexão de cada vez, 409 `TESTE_DE_CONEXAO_EM_ANDAMENTO`). A API de arquivos mostra as **horas até expirar o token** e, vencido, "Capture um novo token, atualize o arquivo do token e clique em Tentar novamente." **Nenhuma credencial, URL ou host** é exibido ou editável, e o texto da aba diz isso. Taxas está desativada e o botão fica desabilitado.

## Fora do escopo

- Nenhum item adicional.

## Termos na interface

A interface não menciona o motor de fluxo (Camunda). O bloco é chamado apenas de "Tarefa atual".
