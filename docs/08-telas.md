# 08 — Telas

O protótipo navegável (`prototipo/siga-vistas-prototipo.html`) é a referência visual: tema claro, paleta verde, barra lateral e padrão de quadros sobrepostos do SIGATIC, com ilustração da Cachoeira Véu de Noiva no quadro principal e, no login, ilustração de chapadas com curvas de nível. Este documento define **informações e comportamento**.

## 1. Login (simulado)

- Usuário e senha.
- Dois usuários fixos de demonstração: um com perfil `OPERADOR` e outro com `OPERADOR_SIGILO`.
- Indicação visível de que o acesso é simulado.

## 2. Consulta

Para o Operador, a consulta, os atalhos e os resultados se limitam aos processos e requerimentos em que ele é o interessado; uma busca por número de outro processo exibe "não tem você como interessado".

Critérios (mantidos da tela atual):

- tipo: Requerimento ou Processo;
- número do requerimento ou processo (máscara `N/AAAA`);
- CPF/CNPJ do interessado (máscara);
- nome do interessado.

Ações: Consultar, Limpar.

Quando a busca por interessado retorna mais de um resultado, a tela exibe uma lista com nº do requerimento, nº do processo, interessado, tipo de processo, situação e data de abertura.

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

- **tarefa atual**: nome da tarefa, desde quando e responsável. Se não houver tarefa ativa, indicar isso.

Processo e requerimento têm relação 1:1, e o requerimento vira processo após o pagamento da taxa e a conclusão do protocolo. O topo da tela mostra sempre o número do processo; o número do requerimento só aparece, com a marca "Não protocolado", enquanto não houver processo. A mesma regra vale para a navegação, os atalhos, a lista de resultados e o dossiê.

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
