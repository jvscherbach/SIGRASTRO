# Índice dos prompts

Cada sprint é implementada a partir de um prompt `.md` desta pasta. A ordem e o estado das sprints ficam em [../14-plano-de-sprints.md](../14-plano-de-sprints.md); o andamento por bloco, em `../progresso/`.

## Regras permanentes

- Nenhum dado real (CPF, CNPJ, nome de pessoa, número de processo) em teste, exemplo, documentação ou log; o `.env.local` nunca é aberto nem impresso.
- Nada pago e nenhuma dependência nova sem justificativa em `docs/10`.
- Só `git diff` entre os comandos git, e **só o responsável commita**.
  - **Exceção única:** o modo automático (A1, D89), em branch `auto/*`, só pelo script `commit-bloco`, **sem push**. Ver [../claude-code/orquestracao.md](../claude-code/orquestracao.md).
- Todo prompt tem a tabela **"Blocos e sessões"** (Bloco, Título, Sessão) e um `docs/progresso/<id>.md`, para o `/orquestrar` conseguir rodá-lo.

## Prompts

| Prompt | Tema |
|---|---|
| [R1](R1-renomeacao-rastro.md) | Renomeação para Rastro |
| [R2](R2-refatorar-front-sigrastro.md) | Refatoração do front para o SIGRASTRO (visual, marca e layout) |
| [S1](S1-consulta.md) a [S5](S5-auditoria-e-configuracoes.md) | Consulta, documentos, conversão, acesso, auditoria e configurações |
| [S6](S6-correcoes-ci-e-ferramentas-claude-code.md), [S6.1](S6.1-guardas-contra-vazamento-de-segredo.md) | Correções, CI e ferramentas do Claude Code |
| [S7](S7-tramitacao-onde-esta-e-com-quem.md), [S8](S8-tramitacao-historico-e-fluxograma.md), [S9](S9-financeiro-taxas-dar-e-pagamentos.md) | Tramitação e financeiro |
| [S10](S10-painel-do-gestor.md) | Painel do Gestor: três gráficos (protocolados × concluídos, fila por etapa, carga por responsável) sobre um instantâneo em memória; 9 blocos |
| [S11](S11-caixa-do-analista-e-lista-por-analista.md) | Caixa de entrada do analista (perfil Analista, posse, aviso de recebimento), nome do interessado, aba lateral "Processos por analista" e pizza da fila por etapa; 11 blocos (SONNET e HAIKU) |
| [A1](A1-commit-automatico-orquestracao.md) | Commit automático por bloco e orquestração com quatro agentes |
