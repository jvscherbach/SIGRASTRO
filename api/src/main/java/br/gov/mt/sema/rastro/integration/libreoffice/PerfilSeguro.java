package br.gov.mt.sema.rastro.integration.libreoffice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Perfil-modelo do LibreOffice copiado pelo JODConverter para o perfil próprio de cada processo. Defesa em
 * profundidade, somada às propriedades de carga do {@link ConversorLibreOffice} (macro nunca executada, vínculo nunca
 * atualizado):
 *
 * <ul>
 *   <li>segurança de macro no nível máximo, macros desligadas e conteúdo ativo (OLE, DDE) desligado;</li>
 *   <li>vínculos externos nunca atualizados no Calc e no Writer;</li>
 *   <li>vínculos (imagens) vindos de documento não confiável bloqueados;</li>
 *   <li>proxy manual apontando para uma porta fechada do próprio container: se algo escapar das regras acima, a
 *       requisição de rede falha ali, sem sair;</li>
 *   <li>local pt-BR, para que documentos sem tamanho de página (HTML) usem A4 em vez de Carta.</li>
 * </ul>
 */
final class PerfilSeguro {

    /** Porta "discard", fechada no container: o proxy que nunca responde. */
    static final int PORTA_DO_PROXY_MORTO = 9;

    private static final String XCU = """
            <?xml version="1.0" encoding="UTF-8"?>
            <oor:items xmlns:oor="http://openoffice.org/2001/registry" xmlns:xs="http://www.w3.org/2001/XMLSchema" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            %s</oor:items>
            """;

    private PerfilSeguro() {
    }

    /** Cria {@code <diretorio>/user/registrymodifications.xcu} e devolve o diretório (o modelo). */
    static Path criar(Path diretorio) throws IOException {
        Path usuario = Files.createDirectories(diretorio.resolve("user"));
        StringBuilder itens = new StringBuilder();
        String seguranca = "/org.openoffice.Office.Common/Security/Scripting";
        item(itens, seguranca, "MacroSecurityLevel", "3");
        item(itens, seguranca, "DisableMacrosExecution", "true");
        item(itens, seguranca, "DisableActiveContent", "true");
        item(itens, seguranca, "BlockUntrustedRefererLinks", "true");
        // Calc: 0 sempre, 1 nunca, 2 sob pedido. Writer: 0 nunca, 1 sob pedido, 2 sempre.
        item(itens, "/org.openoffice.Office.Calc/Content/Update", "Link", "1");
        item(itens, "/org.openoffice.Office.Writer/Content/Update", "Link", "0");
        String rede = "/org.openoffice.Inet/Settings";
        item(itens, rede, "ooInetProxyType", "2");
        for (String protocolo : new String[] {"HTTP", "HTTPS", "FTP"}) {
            item(itens, rede, "ooInet" + protocolo + "ProxyName", "127.0.0.1");
            item(itens, rede, "ooInet" + protocolo + "ProxyPort", String.valueOf(PORTA_DO_PROXY_MORTO));
        }
        item(itens, rede, "ooInetNoProxy", "");
        item(itens, "/org.openoffice.Setup/L10N", "ooSetupSystemLocale", "pt-BR");
        item(itens, "/org.openoffice.Setup/L10N", "ooLocale", "pt-BR");
        Files.writeString(usuario.resolve("registrymodifications.xcu"), XCU.formatted(itens), StandardCharsets.UTF_8);
        return diretorio;
    }

    private static void item(StringBuilder itens, String caminho, String nome, String valor) {
        itens.append("<item oor:path=\"").append(caminho).append("\"><prop oor:name=\"").append(nome)
                .append("\" oor:op=\"fuse\"><value>").append(valor).append("</value></prop></item>\n");
    }
}
