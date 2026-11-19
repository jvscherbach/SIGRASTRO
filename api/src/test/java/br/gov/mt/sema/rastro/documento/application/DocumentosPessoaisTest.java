package br.gov.mt.sema.rastro.documento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais.DocumentoPessoal;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento.Pessoa;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;
import br.gov.mt.sema.rastro.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.rastro.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.rastro.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.testsupport.ParametrosDeConversaoDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Documentos pessoais do SCORP na lista do requerimento (S4, bloco 5). SIGA e SCORP falsos, dados fictícios. */
class DocumentosPessoaisTest {

    private static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";
    private static final LocalDateTime D = LocalDateTime.of(2025, 4, 1, 10, 0);
    private static final String INTERESSADO = "CORP-INT";
    private static final String REPRESENTANTE = "CORP-RL";
    private static final String TECNICO = "CORP-RT";

    private final RequerimentosDoSiga requerimentos = mock(RequerimentosDoSiga.class);
    private final DocumentosDoRequerimento doSiga = mock(DocumentosDoRequerimento.class);
    private final List<Pessoa> pessoas = new ArrayList<>();
    private final List<Long> pessoasLidas = new CopyOnWriteArrayList<>();
    private final ScorpFalso scorp = new ScorpFalso();
    private boolean sigiloPadrao;
    private Usuario usuario;
    private ListarDocumentos listar;
    private SelecionarDocumentosParaDossie selecionar;

    /** SCORP falso: documentos por pessoa, pessoas que falham e as chamadas recebidas. */
    private static final class ScorpFalso implements DocumentosPessoais {
        boolean habilitado = true;
        final Map<String, List<DocumentoPessoal>> documentos = new HashMap<>();
        final Set<String> falham = new HashSet<>();
        final List<Set<String>> chamadas = new CopyOnWriteArrayList<>();

        @Override
        public boolean habilitado() {
            return habilitado;
        }

        @Override
        public Consulta consultar(Set<String> ids) {
            chamadas.add(Set.copyOf(ids));
            Map<String, List<DocumentoPessoal>> respondidas = new HashMap<>();
            Set<String> falharam = new HashSet<>();
            for (String id : ids) {
                if (falham.contains(id)) {
                    falharam.add(id);
                } else {
                    respondidas.put(id, documentos.getOrDefault(id, List.of()));
                }
            }
            return new Consulta(respondidas, falharam);
        }
    }

    @BeforeEach
    void preparar() {
        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                return usuario;
            }

            @Override
            public String token() {
                return "token-de-teste";
            }
        };
        PoliticaDeAcesso politica = new PoliticaDeAcesso(atual);
        VerificarAcessoAoRequerimento acesso = new VerificarAcessoAoRequerimento(requerimentos, politica);
        PessoasDoRequerimento leitorDePessoas = id -> {
            pessoasLidas.add(id);
            return List.copyOf(pessoas);
        };
        DocumentosPessoaisDoRequerimento pessoais = new DocumentosPessoaisDoRequerimento(leitorDePessoas, scorp,
                () -> sigiloPadrao);
        listar = new ListarDocumentos(acesso, doSiga, politica, ParametrosDeConversaoDeTeste.ligada(), pessoais);
        selecionar = new SelecionarDocumentosParaDossie(acesso, listar, politica, ParametrosDeConversaoDeTeste.ligada());

        when(requerimentos.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR)));
        when(requerimentos.buscarPorId(202)).thenReturn(Optional.of(requerimento(202, "98.765.432/0001-10")));
        when(doSiga.listar(anyLong())).thenReturn(List.of(
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 101, "K-SIGA-1", "Requerimento",
                        "requerimento.pdf", D, null, 0),
                Documento.deLinha(OrigemDocumento.REPRESENTANTE_LEGAL, "41", 101, "K-COMPARTILHADA", "procuracao.pdf",
                        "procuracao.pdf", D.plusDays(1), null, 0)));

        pessoas.add(new Pessoa(INTERESSADO, PapelDaPessoa.INTERESSADO));
        pessoas.add(new Pessoa(REPRESENTANTE, PapelDaPessoa.REPRESENTANTE_LEGAL));
        pessoas.add(new Pessoa(TECNICO, PapelDaPessoa.RESPONSAVEL_TECNICO));
        // mesma pessoa em dois papéis: vale o de maior precedência
        pessoas.add(new Pessoa(REPRESENTANTE, PapelDaPessoa.RESPONSAVEL_TECNICO));
        scorp.documentos.put(INTERESSADO, List.of(
                new DocumentoPessoal("K-SCORP-RG", "rg.pdf", "RG do interessado", D.minusDays(10), null),
                new DocumentoPessoal("K-SCORP-ANTIGO", "cnh.pdf", "CNH antiga", D.minusDays(20), D.minusDays(5)),
                new DocumentoPessoal("0", "perdido.pdf", "Perdido", D.minusDays(3), null)));
        scorp.documentos.put(REPRESENTANTE, List.of(
                new DocumentoPessoal("K-SCORP-RG", "rg.pdf", "RG repetido em outra pessoa", D.minusDays(10), null),
                new DocumentoPessoal("K-COMPARTILHADA", "procuracao.pdf", "Procuração", D.plusDays(1), null)));
        scorp.documentos.put(TECNICO, List.of(
                new DocumentoPessoal("K-SCORP-ART", "registro", null, D, null)));
    }

    private static Requerimento requerimento(long id, String cpfCnpj) {
        return new Requerimento(id, "3331/2025", "2825/2025", null, Situacao.de("EM_ANALISE"), D, null,
                new Interessado("Associação Residencial Vale Verde", cpfCnpj), "Uso de Recursos Hídricos");
    }

    private List<Documento> pessoaisDe(DocumentosListados lista) {
        return lista.itens().stream().map(DocumentoListado::documento).filter(d -> d.origem() == OrigemDocumento.SCORP)
                .toList();
    }

    @Test
    void umaChamadaPorPessoaComPapelDeMaiorPrecedenciaEDeduplicacao() {
        DocumentosListados lista = listar.executar(101);

        assertThat(scorp.chamadas).containsExactly(Set.of(INTERESSADO, REPRESENTANTE, TECNICO));
        List<Documento> pessoais = pessoaisDe(lista);
        // RG repetido entre pessoas vira um; procuração repetida com o SIGA fica com a origem do SIGA (RN05)
        assertThat(pessoais).extracting(Documento::titulo)
                .containsExactlyInAnyOrder("RG do interessado", "CNH antiga", "Perdido", "registro");
        assertThat(lista.itens()).filteredOn(i -> "K-COMPARTILHADA".equals(i.documento().chaveDoArquivo()))
                .singleElement().satisfies(i -> assertThat(i.documento().origem()).isEqualTo(OrigemDocumento.REPRESENTANTE_LEGAL));
        assertThat(pessoais).filteredOn(d -> d.titulo().equals("RG do interessado")).singleElement()
                .satisfies(d -> assertThat(d.descricaoDaOrigem()).isEqualTo("Pessoal · Interessado"));
        assertThat(pessoais).filteredOn(d -> d.titulo().equals("registro")).singleElement()
                .satisfies(d -> assertThat(d.descricaoDaOrigem()).isEqualTo("Pessoal · Responsável técnico"));
        assertThat(lista.avisos()).isEmpty();
    }

    @Test
    void vigenciaPelaDataFimEPelaChaveEDataPeloInicio() {
        List<Documento> pessoais = pessoaisDe(listar.executar(101));

        assertThat(pessoais).filteredOn(d -> d.titulo().equals("CNH antiga")).singleElement()
                .satisfies(d -> assertThat(d.situacao()).isEqualTo(SituacaoDocumento.SUBSTITUIDO));
        assertThat(pessoais).filteredOn(d -> d.titulo().equals("Perdido")).singleElement()
                .satisfies(d -> assertThat(d.situacao()).isEqualTo(SituacaoDocumento.INDISPONIVEL));
        assertThat(pessoais).filteredOn(d -> d.titulo().equals("RG do interessado")).singleElement()
                .satisfies(d -> {
                    assertThat(d.situacao()).isEqualTo(SituacaoDocumento.VIGENTE);
                    assertThat(d.data()).isEqualTo(D.minusDays(10));
                    assertThat(d.tipoArquivo().name()).isEqualTo("PDF");
                });
        assertThat(pessoais).filteredOn(d -> d.titulo().equals("registro")).singleElement()
                .satisfies(d -> assertThat(d.tipoArquivo().name()).isEqualTo("DESCONHECIDO"));
    }

    @Test
    void idEstavelEntreChamadasSemAChave() {
        List<String> primeira = pessoaisDe(listar.executar(101)).stream().map(Documento::id).sorted().toList();
        List<String> segunda = pessoaisDe(listar.executar(101)).stream().map(Documento::id).sorted().toList();

        assertThat(primeira).isEqualTo(segunda).hasSize(4);
        assertThat(primeira).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{16}").doesNotContain("K-SCORP"));
    }

    @Test
    void scorpDepoisDasSeisOrigensNoEmpateCronologico() {
        DocumentosListados lista = listar.executar(101);

        // "registro" (SCORP) e "Requerimento" (DOCUMENTO_PROCESSO) têm a mesma data: o do SIGA vem antes
        List<String> titulos = lista.itens().stream().map(i -> i.documento().titulo()).toList();
        assertThat(titulos.indexOf("Requerimento")).isLessThan(titulos.indexOf("registro"));
    }

    @Test
    void scorpForaDoArDaAvisoEMantemAsDemaisOrigens() {
        scorp.falham.addAll(Set.of(INTERESSADO, REPRESENTANTE, TECNICO));

        DocumentosListados lista = listar.executar(101);

        assertThat(lista.avisos()).extracting(AvisoDaLista::codigo).containsExactly(AvisoDaLista.SCORP_INDISPONIVEL);
        assertThat(lista.avisos().get(0).mensagem()).doesNotContain("CORP-", "HTTP", "http");
        assertThat(pessoaisDe(lista)).isEmpty();
        assertThat(lista.itens()).extracting(i -> i.documento().id()).containsExactly("1", "41");
    }

    @Test
    void falhaParcialListaOQueVeioEAvisa() {
        scorp.falham.add(TECNICO);

        DocumentosListados lista = listar.executar(101);

        assertThat(lista.avisos()).extracting(AvisoDaLista::codigo).containsExactly(AvisoDaLista.SCORP_PARCIAL);
        assertThat(pessoaisDe(lista)).extracting(Documento::titulo).doesNotContain("registro").contains("RG do interessado");
    }

    @Test
    void scorpDesligadoNaoLePessoasNemAvisa() {
        scorp.habilitado = false;

        DocumentosListados lista = listar.executar(101);

        assertThat(pessoasLidas).isEmpty();
        assertThat(scorp.chamadas).isEmpty();
        assertThat(lista.avisos()).isEmpty();
        assertThat(pessoaisDe(lista)).isEmpty();
    }

    @Test
    void semPessoaComIdCorporativoNaoChamaOScorp() {
        pessoas.clear();
        pessoas.add(new Pessoa(" ", PapelDaPessoa.INTERESSADO));

        assertThat(listar.executar(101).avisos()).isEmpty();
        assertThat(scorp.chamadas).isEmpty();
    }

    @Test
    void operadorDeOutroInteressadoNaoChegaAoScorp() {
        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, CNPJ_DO_OPERADOR, true);

        assertThatThrownBy(() -> listar.executar(202)).isInstanceOf(NaoInteressadoException.class);
        assertThat(pessoasLidas).isEmpty();
        assertThat(scorp.chamadas).isEmpty();
    }

    @Test
    void sigiloPadraoFalsoTodosVeem() {
        for (Usuario u : usuarios()) {
            usuario = u;
            assertThat(listar.executar(101).itens()).filteredOn(i -> i.documento().origem() == OrigemDocumento.SCORP)
                    .allSatisfy(i -> {
                        assertThat(i.acessivel()).isTrue();
                        assertThat(i.documento().sigiloso()).isFalse();
                    });
        }
    }

    @Test
    void sigiloPadraoVerdadeiroMascaraParaOperadorEBloqueiaNaSelecao() {
        sigiloPadrao = true;
        for (Usuario u : usuarios()) {
            usuario = u;
            boolean operador = u.perfil() == Perfil.OPERADOR;
            assertThat(listar.executar(101).itens()).filteredOn(i -> i.documento().origem() == OrigemDocumento.SCORP)
                    .allSatisfy(i -> {
                        assertThat(i.documento().sigiloso()).isTrue();
                        assertThat(i.acessivel()).isEqualTo(!operador);
                        assertThat(i.titulo()).isEqualTo(operador ? "Documento sigiloso" : i.documento().titulo());
                        assertThat(i.nomeArquivo()).isEqualTo(operador ? null : i.documento().nomeArquivo());
                    });
        }
        usuario = usuarios().get(0);
        String idDoRg = idDe("RG do interessado");
        assertThatThrownBy(() -> selecionar.executar(101, List.of(new ReferenciaDeDocumento("SCORP", idDoRg))))
                .isInstanceOf(DocumentoSigilosoException.class);
    }

    @Test
    void documentoPessoalEntraNaSelecaoDoDossieComAChaveResolvida() {
        String idDoRg = idDe("RG do interessado");

        List<Documento> escolhidos = selecionar.executar(101, List.of(new ReferenciaDeDocumento("SCORP", idDoRg)));

        assertThat(escolhidos).singleElement().satisfies(d -> {
            assertThat(d.origem()).isEqualTo(OrigemDocumento.SCORP);
            assertThat(d.chaveDoArquivo()).isEqualTo("K-SCORP-RG");
        });
    }

    @Test
    void comOScorpForaDoArAReferenciaPessoalExplicaOMotivo() {
        String idDoRg = idDe("RG do interessado");
        scorp.falham.add(INTERESSADO);

        assertThatThrownBy(() -> selecionar.executar(101, List.of(new ReferenciaDeDocumento("SCORP", idDoRg))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("o SCORP não respondeu agora");
    }

    private String idDe(String titulo) {
        return pessoaisDe(listar.executar(101)).stream().filter(d -> d.titulo().equals(titulo)).findFirst()
                .orElseThrow().id();
    }

    private static List<Usuario> usuarios() {
        return List.of(new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, CNPJ_DO_OPERADOR, true),
                new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true),
                new Usuario("admin.demo", "Admin Demo", Perfil.ADMIN, null, true));
    }
}
