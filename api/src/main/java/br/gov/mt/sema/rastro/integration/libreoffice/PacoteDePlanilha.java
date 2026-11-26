package br.gov.mt.sema.rastro.integration.libreoffice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lê só a lista de entradas do pacote XLSX (e as relações {@code .rels}) para escolher o perfil do LibreOffice (V6,
 * bloco 3). Conteúdo ativo (macro VBA, objeto embutido OLE, vínculo externo, controle ActiveX, relação com destino
 * externo) mantém o perfil estrito; gráficos e desenhos não contam como conteúdo ativo. Na dúvida (arquivo ilegível,
 * relação grande demais), o perfil estrito.
 */
final class PacoteDePlanilha {

    /**
     * @param conteudoAtivo há ao menos um sinal de conteúdo ativo ou a leitura não foi conclusiva
     * @param graficos o pacote tem {@code xl/charts/*} ou {@code xl/drawings/*}
     */
    record Analise(boolean conteudoAtivo, boolean graficos) {
    }

    private PacoteDePlanilha() {
    }

    private static final int RELACAO_MAXIMA = 4 * 1024 * 1024;

    static Analise analisar(Path xlsx) {
        boolean ativo = false;
        boolean graficos = false;
        try (ZipFile zip = new ZipFile(xlsx.toFile())) {
            Enumeration<? extends ZipEntry> todas = zip.entries();
            while (todas.hasMoreElements()) {
                ZipEntry entrada = todas.nextElement();
                String nome = entrada.getName().toLowerCase(Locale.ROOT);
                if (nome.contains("vbaproject") || nome.contains("embeddings/") || nome.contains("oleobject")
                        || nome.contains("externallinks/") || nome.contains("activex/")) {
                    ativo = true;
                }
                if (nome.startsWith("xl/charts/") || nome.startsWith("xl/drawings/")) {
                    graficos = true;
                }
                if (nome.endsWith(".rels") && relacaoExterna(zip, entrada)) {
                    ativo = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Analise(true, graficos);
        }
        return new Analise(ativo, graficos);
    }

    private static boolean relacaoExterna(ZipFile zip, ZipEntry entrada) throws IOException {
        try (InputStream fluxo = zip.getInputStream(entrada)) {
            byte[] bytes = fluxo.readNBytes(RELACAO_MAXIMA + 1);
            if (bytes.length > RELACAO_MAXIMA) {
                return true;
            }
            String texto = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return texto.contains("targetmode=\"external\"") || texto.contains("targetmode='external'");
        }
    }
}
