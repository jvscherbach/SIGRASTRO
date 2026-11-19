# Roteiro de homologação da R1 (renomeação para Rastro)

Roteiro curto para o responsável conferir a renomeação depois da Fase 2. **Não usa dado real**: onde aparecer um número de requerimento ou processo, use um número de teste da base de desenvolvimento. CPF e CNPJ são marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`); nada de dado pessoal em anotação, print ou log.

## 1. Subir com os três arquivos

```powershell
docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

Confirme, dentro do container, que as variáveis externas estão **vazias** (sem imprimir o valor). Ex.: `docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA'`. Repita para `LICENCIAMENTO_API_*`, `SCORP_API_*` e `VISTAS_DB_*`. Se algum vier `PREENCHIDA`: pare e derrube sem `-v`.

- [ ] `rastro-api-1` e `rastro-front-1` ficam `healthy`.
- [ ] O volume `siga-vistas_dossies-tmp` está montado (o nome é mantido de propósito).

## 2. Nome na interface

- [ ] A aba do navegador mostra **Rastro** (`<title>`).
- [ ] A tela de login mostra **RASTRO** com o rótulo "consulta ao SIGA Licenciamento (protótipo)".
- [ ] A trilha do topo e o quadro da consulta mostram **Vistas do Processo** (rótulo do módulo).
- [ ] Nenhuma tela mostra "SIGA Vistas".

## 3. Login e perfis

- [ ] Entre com o usuário de demonstração e a senha de demonstração do seu `.env.local` (não a escreva aqui).
- [ ] Senha errada responde 401.
- [ ] O menu de cada perfil continua o mesmo de antes.

## 4. Consulta e dossiê

- [ ] Busque um `<NUMERO_DE_TESTE>`. Com `compose.verificacao.yaml` o SIGA está desligado e a consulta responde "SIGA indisponível": é o esperado.
- [ ] A geração de dossiê falha sem o SIGA; para a conferência real, use a base de desenvolvimento, fora deste roteiro.

## 5. PDF e metadados

- [ ] Em um dossiê gerado com sucesso, em "Propriedades do documento" do leitor de PDF: **Produtor** e **Criador** são `Rastro`; o título é `Dossiê do Processo` ou `Dossiê do Requerimento` seguido do número.
- [ ] O nome do arquivo baixado continua `dossie-...pdf`.
- [ ] Nenhum CPF, CNPJ ou nome do interessado aparece nos metadados.

## 6. Reinício e preservação

- [ ] Entre, guarde um token e faça `docker compose ... restart`. O token deixa de valer, como já acontecia antes (sessão em memória, R50/R57); isso não é regressão da renomeação.
- [ ] Logue de novo e confira que os **recentes** continuam listados (migração da chave antiga para `rastro.*`, D75).
- [ ] Antes de sair, confirme que o logout apaga a sessão.

## 7. Aliases

- [ ] Um `.env.local` com `vistas.dossie.diretorio` (ou outra propriedade `vistas.*`) sobe sem erro e registra aviso de depreciação **sem valor**.

## 8. CI

- [ ] O workflow `ci.yml` está verde no PR; as tags locais são `rastro-api:ci` e `rastro-front:ci`.

## 9. Lista manual (bloco 6)

Siga, à mão, a seção "Renomeação do Rastro" de [docs/13](../13-padroes-tecnicos.md): renomear o repositório, a pasta local, o `git remote`, pacotes do GHCR e, se quiser, o volume. Nada disso é feito por este roteiro.

## 10. Derrubar

```powershell
docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml down
```

Nunca com `-v`.
