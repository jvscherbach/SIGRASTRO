package br.gov.mt.sema.vistas.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.vistas.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.vistas.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.vistas.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Interessado;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.Situacao;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.FilaDeGeracao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes HTTP de documentos e dossiê: SIGA e API de arquivos falsos (nenhum sistema real), geração síncrona
 * e pasta temporária própria. As chaves e o token abaixo são marcadores que NUNCA podem aparecer numa resposta.
 */
@SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url=",
        "vistas.arquivos.url=", "vistas.arquivos.token=TOKEN-ARQUIVOS-DE-TESTE", "vistas.dossie.espaco-minimo-mb=0"})
@AutoConfigureMockMvc
public abstract class BaseHttpDeDocumentos {

    public static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";
    public static final String TOKEN_DE_ARQUIVOS = "TOKEN-ARQUIVOS-DE-TESTE";
    public static final String CHAVE_1 = "CHAVE-INTERNA-0001";
    public static final String CHAVE_2 = "CHAVE-INTERNA-0002";
    public static final String CHAVE_SIGILOSA = "CHAVE-INTERNA-SIGILOSA";
    public static final String[] SEGREDOS = {CHAVE_1, CHAVE_2, CHAVE_SIGILOSA, "CHAVE-INTERNA", TOKEN_DE_ARQUIVOS};

    private static final Path DIRETORIO;

    static {
        try {
            DIRETORIO = Files.createTempDirectory("vistas-dossies-teste");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Diretório temporário dos dossiês do contexto de teste: nenhuma resposta pode trazer este caminho. */
    protected static Path diretorioTemporario() {
        return DIRETORIO;
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("vistas.dossie.diretorio", DIRETORIO::toString);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @MockitoBean
    protected RequerimentosDoSiga siga;

    @MockitoBean
    protected TarefaAtualDoRequerimento tarefas;

    @MockitoBean
    protected ObjetoEPrioridadeDoRequerimento objetoEPrioridade;

    @MockitoBean
    protected DocumentosDoRequerimento documentos;

    @MockitoBean
    protected ArquivosDoRepositorio arquivos;

    @MockitoBean
    protected FilaDeGeracao fila;

    /** Conteúdo (por chave) que a API de arquivos falsa entrega; chave ausente = PDF de 1 página. */
    protected final Map<String, byte[]> conteudos = new ConcurrentHashMap<>();

    @BeforeEach
    protected void prepararBase() {
        reset(siga, tarefas, objetoEPrioridade, documentos, arquivos, fila);
        conteudos.clear();
        when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.semTarefa());
        when(objetoEPrioridade.objeto(anyLong())).thenReturn(ObjetoDoRequerimento.vazio());
        when(objetoEPrioridade.prioridade(anyLong())).thenReturn(PrioridadeAtendimento.de(List.of()));
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR)));
        when(siga.buscarPorId(202)).thenReturn(Optional.of(requerimento(202, "98.765.432/0001-10")));
        when(siga.buscarPorId(404)).thenReturn(Optional.empty());
        when(documentos.listar(anyLong())).thenReturn(listaPadrao());

        doAnswer(chamada -> {
            ((Runnable) chamada.getArgument(0)).run();
            return null;
        }).when(fila).submeter(any(Runnable.class));
        doAnswer(chamada -> {
            String chave = chamada.getArgument(0);
            Path destino = chamada.getArgument(1);
            Files.write(destino, conteudos.getOrDefault(chave, PdfsDeTeste.bytes(1, "documento " + chave.hashCode())));
            return 1;
        }).when(arquivos).baixar(anyString(), any(Path.class));
    }

    protected static Requerimento requerimento(long id, String cpfCnpj) {
        return new Requerimento(id, "3331/2025", "2825/2025", "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2025, 3, 31, 10, 12), LocalDateTime.of(2025, 4, 29, 9, 14),
                new Interessado("Associação Residencial Vale Verde", cpfCnpj), "Uso de Recursos Hídricos");
    }

    /** 1 e 2: PDFs vigentes; 3: sigiloso; 4: planilha; 5: indisponível; 6: substituído. */
    protected static List<Documento> listaPadrao() {
        LocalDateTime d = LocalDateTime.of(2025, 4, 1, 10, 0);
        return List.of(
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804328", 101, CHAVE_1, "Requerimento assinado", "requerimento.pdf", d, null, 0),
                Documento.deLinha(OrigemDocumento.COMPLEMENTACAO, "900118", 101, CHAVE_2, null, "oficio.pdf", d.plusDays(1), null, 0),
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804329", 101, CHAVE_SIGILOSA, "Laudo reservado", "laudo-reservado.pdf", d.plusDays(2), null, 1),
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804330", 101, "CHAVE-INTERNA-PLANILHA", "Planilha", "calculo.xlsx", d.plusDays(3), null, 0),
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804331", 101, "0", "Perdido", "perdido.pdf", d.plusDays(4), null, 0),
                Documento.deLinha(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "11", 101, "CHAVE-INTERNA-ANTIGO", "Antigo", "antigo.pdf", d.minusDays(1), d, 0));
    }

    protected String token(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", "senha-de-teste"));
        String resposta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }

    protected String bearer(String usuario) throws Exception {
        return "Bearer " + token(usuario);
    }
}
