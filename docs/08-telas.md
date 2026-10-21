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
- "Consultados recentemente": atalhos para o que o usuário abriu neste navegador (até 6, guardados no `localStorage` por login, só id e rótulo). Só aparecem se houver; não há dados de exemplo.
- Perfil restrito (Operador) vê o aviso "Seu perfil consulta apenas processos e requerimentos em que você é o interessado."

Como ajustado (S1.1):

- **Número de processo repetido (1:N, D31):** a busca só por número de processo com mais de um resultado não abre direto: mostra a lista (com situação e abertura, para o usuário escolher) e o aviso "Este número de processo está associado a mais de um requerimento." Com 1 resultado, abre o detalhe como antes. A busca por número de requerimento continua exata.
- **Quadro do topo com altura estável:** o quadro do topo (ilustração da cachoeira) tem a mesma altura e proporção em todos os estados da consulta: inicial, carregando, resultados, vazio, 400, 403, 404, 503 e erro de rede. O alerta de erro fica num espaço reservado dentro do cartão de busca (`.search-feedback`, com altura mínima própria, maior no celular para mensagens de duas linhas); por isso a mensagem não empurra o quadro nem amplia a ilustração. Resultados e o estado "Nenhum resultado encontrado" aparecem na folha sobreposta, fora do quadro. Causa do defeito corrigido: o alerta era inserido no fim do cartão sem espaço reservado; a altura do quadro (só `min-height`) crescia com o conteúdo e o SVG (`preserveAspectRatio="xMidYMid slice"`) se ampliava para cobrir a nova altura. Mensagem muito longa no celular (mais de duas linhas) ainda pode crescer o quadro; nenhuma mensagem atual passa disso.

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

Como implementado (S1): topo com a identificação (RN04), situação em tag, prioridade (quando mapeada) e "Não protocolado"; cartões de vidro de Interessado, Datas e Tarefa atual; bloco "Objeto do requerimento" com Objetivo, Finalidade, Atividade e Tipo (mostra "—" enquanto o mapeamento P01 estiver pendente; ver [04](04-modelo-de-dados.md)); a seção "Documentos" traz só o aviso de que chega na Sprint 2. Erros (403, 404, 503) aparecem no corpo da página, com "Voltar para a consulta". O topo da tela mostra a trilha `Vistas do Processo / Consulta / Processo N/AAAA`.

Como ajustado (S1.1), com o mapeamento P01:

- **Prioridade:** chip no topo, "Atendimento normal" (cinza) ou "Atendimento prioritário" (azul). No prioritário, os critérios aparecem como dica do chip e em lista ("Critérios de prioridade") no cartão de Datas e identificação.
- **Tipo do requerimento** no cartão de Datas e identificação ("—" sem dado).
- **Cartões do objeto** (`.obj-grid`, `.obj-card`): **Objetivos** (uma linha por objetivo); **Finalidade | Atividade** (uma linha por par, outorga); **Tipo | Objetivo** (só com objetivos de outorga, com "Subterrânea", "Superficial" ou "Barramento"); **Atividade** (licenciamento: grupo · atividade). Cartão sem dado mostra "—". Objetivos e pares finalidade/atividade são listas independentes, como na tela do SIGA.
- **Responsável na tarefa atual (RN12):** a linha "Responsável" só é renderizada quando a API manda o campo, isto é, para Gestor e Administrador: o nome, "Não atribuída" ou "Responsável não identificado". Para o Operador a linha não aparece (RN08). O front não esconde dado que a API mandou: a regra é do back-end, e a tela só reflete a resposta.

O requerimento vira processo após o pagamento da taxa e a conclusão do protocolo. O topo da tela mostra sempre o número do processo; o número do requerimento só aparece, com a marca "Não protocolado", enquanto não houver processo. A mesma regra vale para a navegação, os atalhos, a lista de resultados e o dossiê.

### Geometrias

Seção exibida quando houver SHP, KML ou KMZ vigentes: lista dos arquivos (nome, arquivo, origem, data) e ação **Baixar geometrias**, que baixa todos num ZIP. Não entram no dossiê.

### Documentos

Colunas: seleção, documento (nome, ID e arquivo), origem, data e sigilo. A coluna "Sem efeito" da tela atual não é exibida.

Comportamento:

- ordenação padrão por data crescente;
- busca por texto e filtro por origem;
- vigentes pré-selecionados (ação "Seleção padrão" restaura essa seleção);
- indisponíveis exibidos esmaecidos, sem seleção, com "arquivo indisponível" junto ao nome do arquivo;
- sigilosos sem perfil autorizado: visíveis na lista, mas sem seleção e sem visualização, com indicação do motivo;
- contador de documentos selecionados;
- ação por linha: **Visualizar**.

### Modal de visualização

- Exibe o documento no visualizador de PDF do navegador.
- Documentos convertidos (planilhas, textos, imagens) são exibidos na versão em PDF, permitindo conferir a conversão antes de gerar o dossiê.
- Formatos sem conversão: mensagem com nome, tipo e opção de baixar o original.
- Indicação de carregamento enquanto o documento é obtido ou convertido.
- Ação de selecionar ou desmarcar o documento a partir do modal.

## 4. Geração do dossiê

- Ação **Gerar dossiê**, habilitada com ao menos um documento selecionado.
- Acompanhamento: barra de progresso (processados/total) e situação de cada documento.
- Ao concluir:
  - **Pré-visualizar**: abre o PDF final em modal ou nova aba, sem gerar novamente;
  - **Baixar**;
  - indicação do tempo restante até a expiração do arquivo.
- Em caso de token da API expirado: mensagem orientando a capturar um novo token.
- Em caso de falha em qualquer documento: a gaveta passa a "Geração interrompida", mostra o documento e o motivo, informa que nenhum arquivo foi gerado e oferece **Tentar novamente** ou **Remover documento e gerar**. Em "Dossiês gerados", o pedido aparece como "Interrompido · nenhum arquivo gerado", sem download.

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
