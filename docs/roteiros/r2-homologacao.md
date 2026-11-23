# Roteiro de homologação da R2 (novo visual SIGRASTRO)

Roteiro para o responsável conferir a refatoração visual do front. **Não usa dado real**: onde aparecer um número de requerimento ou processo, use um número de teste da base de desenvolvimento; CPF e CNPJ são `<CPF_DE_TESTE>` e `<CNPJ_DE_TESTE>`. O guia do padrão visual é [design/sigrastro/LEIA-ME.md](../design/sigrastro/LEIA-ME.md); a referência é `docs/design/sigrastro/referencia/A3-trilha-funcional.html` (só o Cerrado; ignore o painel de testes e os outros biomas).

## 1. Subir com os três arquivos

```powershell
docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

Confirme, dentro do container, que as variáveis externas estão **vazias** (sem imprimir o valor), uma a uma, para `SIGA_DB_URL`, `LICENCIAMENTO_API_URL` e `SCORP_API_URL`:

```powershell
docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA'
```

Se alguma vier `PREENCHIDA`, pare, derrube os containers (sem `-v`) e relate. Para ver as capturas automáticas de cada tela: `npm --prefix front run capturas` (grava em `front/test-results/capturas/`, em 1920 e 390 px; as imagens não vão para o Git).

## 2. Como conferir (vale para todas as telas)

Abra cada tela nas **três larguras: 1920, 1280 e 390 px** (ferramentas do navegador, modo dispositivo) e compare com o protótipo no Cerrado. As fontes do protótipo vêm da internet; no front elas são locais.

Em toda tela, em toda largura:

- [ ] Sem rolagem horizontal da página (tabelas largas rolam dentro do próprio quadro).
- [ ] Cores do Cerrado (terra, ocre e marcador amarelo); nada verde-limão do visual antigo.
- [ ] Nenhum brasão do Estado, logo da SEMA, "SEMA · Mato Grosso" ou "consulta ao SIGA Licenciamento".

## 3. Telas

### 3.1 Login (sair e abrir a raiz)

- [ ] 1920 e 1280: caixa branca **centralizada** na janela, fundo de **uma cor** escura, sem curvas de nível nem imagem.
- [ ] Escudo árvore-vida e o nome **SIGRASTRO** acima da caixa, com a frase "Acompanhamento de processos de licenciamento ambiental".
- [ ] O aviso de acesso simulado continua dentro da caixa; os três atalhos de demonstração entram com um clique; senha errada mostra a mensagem de sempre.
- [ ] 390: margens de 20 px, a caixa ocupa a largura.
- [ ] A aba do navegador mostra o título "SIGRASTRO" e o escudo como ícone.

### 3.2 Barra e navegação

- [ ] Barra escura no topo, fixa ao rolar, com a marca à esquerda (clicar leva à Consulta).
- [ ] Item atual com sublinhado amarelo. Em 1920 e 1280: ícone e rótulo; em 390 (até 900 px): só ícones; em 390 (até 520 px) somem o selo do perfil e o nome.
- [ ] Perfis: Operador vê Consulta, Dossiês gerados e Auditoria; Gestor idem; Administrador vê também Configurações. **Auditoria é um item só.**
- [ ] "Sair" volta ao login.

### 3.3 Consulta

- [ ] Faixa do topo com a paisagem do Cerrado e o cartão claro com o título "Consulta".
- [ ] Alternância **Processo | Requerimento** (a opção ativa se destaca); validação do formato N/AAAA e as mensagens de "não encontrado" e de erro como antes.
- [ ] "Consultados por último" como trilha vertical tracejada com um círculo por item.
- [ ] A altura da faixa não muda entre os estados (inicial, erro, resultado).

### 3.4 Detalhe e trilha

- [ ] Cabeçalho com a tag de situação (cor e texto); painéis claros de dados.
- [ ] Tramitação como trilha vertical: marcador amarelo na etapa atual; marcador final quando concluído; caminho aproximado tracejado.
- [ ] "Taxas e pagamentos" e o selo "Taxa vencida" com texto, não só cor.
- [ ] Em 390 a trilha vem em coluna única, acima do conteúdo.

### 3.5 Documentos e gaveta do dossiê

- [ ] Tabela de documentos: ordenar, filtrar, contadores; documento sigiloso com **cadeado e texto**.
- [ ] Gaveta: progresso por etapa, mensagens de erro reais, "Tentar novamente"; Esc minimiza; o indicador na barra reabre a gaveta.
- [ ] Visualizador: Esc fecha e o foco volta ao botão que abriu. Geometrias em lista própria.

### 3.6 Dossiês gerados

- [ ] Tabela, filtros, tags de situação, Baixar, Descartar, estado vazio e paginação com os novos estilos.

### 3.7 Auditoria em abas

- [ ] Duas abas no topo da tela: **Documentos** e **Acessos**. A aba Acessos só aparece para o Administrador; o Operador digitando `/auditoria/acessos` volta à Consulta com o aviso de permissão.
- [ ] Filtros, detalhe, exportação CSV e a faixa "registros ficam na memória" como antes.

### 3.8 Configurações (Administrador)

- [ ] Abas Usuários, Parâmetros e Conexões (e as de parâmetros por grupo) com setas, Home e End; **não existe aba Aparência nem seletor de tema**.
- [ ] Tabelas, interruptores, campos com apoio e erro, cartões de conexão com o resultado do teste (OK, ressalva, falha, sempre com texto) e a barra de alterações não salvas.

## 4. Teclado e movimento

- [ ] Percorra com **Tab** o login, a barra, a Consulta, a tabela de documentos, a gaveta e as abas: o foco aparece sempre (anel amarelo com anel escuro por dentro) e todo controle tem nome.
- [ ] Gaveta e visualizador **prendem o foco** (Tab não sai) e o Esc fecha.
- [ ] Ative "reduzir movimento" no sistema operacional: a paisagem do Cerrado e as transições **param**.

## 5. Nada do comportamento mudou

Com dados fictícios ou de teste, de ponta a ponta:

- [ ] Entrar como Administrador, consultar um requerimento de teste, abrir o detalhe.
- [ ] Selecionar documentos, **Gerar dossiê**, acompanhar na gaveta, minimizar (indicador na barra), reabrir, **pré-visualizar** e **baixar o PDF**.
- [ ] O arquivo baixado tem o nome `dossie-...pdf`, abre e tem o sumário e os marcadores de sempre.
- [ ] Regras de perfil, ordem cronológica dos documentos e mensagens de erro iguais às da S6 a S9.

## 6. Registrar

Anote em `docs/progresso/r2.md` (seção do bloco 17) o que passou e o que não passou, por tela e largura. Itens que o visual deixou em aberto: R79 (borda de campo) e R80 (textos do back-end com "Rastro"), em [10](../10-pendencias-e-riscos.md).
