package br.gov.mt.sema.vistas.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.gov.mt.sema.vistas.acesso.application.UsuarioAtual;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.consulta.application.DetalheDoRequerimento;
import br.gov.mt.sema.vistas.consulta.application.ObterRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Identificacao;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.documento.application.ReferenciaDeDocumento;
import br.gov.mt.sema.vistas.documento.application.SelecaoParaDossie;
import br.gov.mt.sema.vistas.documento.application.SelecionarDocumentosParaDossie;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.dossie.application.port.FilaDeGeracao;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.DocumentoNaoIncluido;
import br.gov.mt.sema.vistas.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.springframework.stereotype.Service;

/**
 * Cria o pedido (RF11): confere requerimento e acesso (404, RN07), valida a seleção contra a lista relida no
 * servidor, resolve as chaves uma única vez e guarda o instantâneo no pedido. Quando este método termina, a leitura
 * do SIGA acabou: a geração roda sem nenhuma conexão com o banco.
 */
@Service
public class CriarDossie {

    private final ObterRequerimento requerimentos;
    private final SelecionarDocumentosParaDossie selecao;
    private final UsuarioAtual usuarioAtual;
    private final RepositorioDePedidos pedidos;
    private final FilaDeGeracao fila;
    private final GeradorDeDossie gerador;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public CriarDossie(ObterRequerimento requerimentos, SelecionarDocumentosParaDossie selecao, UsuarioAtual usuarioAtual,
            RepositorioDePedidos pedidos, FilaDeGeracao fila, GeradorDeDossie gerador, ParametrosDoDossie parametros,
            Clock relogio) {
        this.requerimentos = requerimentos;
        this.selecao = selecao;
        this.usuarioAtual = usuarioAtual;
        this.pedidos = pedidos;
        this.fila = fila;
        this.gerador = gerador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** @return o id do pedido (UUID aleatório) */
    public String executar(long idRequerimento, List<ReferenciaDeDocumento> documentos) {
        Usuario usuario = usuarioAtual.obter();
        // 404 e RN07 primeiro: quem não pode acessar o requerimento não descobre nada sobre os documentos.
        DetalheDoRequerimento detalhe = requerimentos.executar(idRequerimento);
        SelecaoParaDossie selecionados = selecao.executarComInventario(idRequerimento, documentos);

        // Posição de cada documento na lista cronológica: a seção "Documentos não incluídos" segue a mesma ordem.
        List<Documento> listagem = new ArrayList<>(selecionados.escolhidos());
        selecionados.naoSelecionados().forEach(n -> listagem.add(n.documento()));
        listagem.sort(Documento.ORDEM_CRONOLOGICA);
        Map<Documento, Integer> posicoes = new IdentityHashMap<>();
        for (int i = 0; i < listagem.size(); i++) {
            posicoes.put(listagem.get(i), i);
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        List<ItemDoPedido> itens = new ArrayList<>();
        for (Documento d : selecionados.escolhidos()) {
            itens.add(new ItemDoPedido(d.origem().name(), d.origem().descricao(), d.id(), d.titulo(), d.data(),
                    d.chaveDoArquivo(), posicoes.get(d), d.extensao()));
        }
        List<DocumentoNaoIncluido> naoSelecionados = new ArrayList<>();
        for (SelecaoParaDossie.NaoSelecionado n : selecionados.naoSelecionados()) {
            Documento d = n.documento();
            naoSelecionados.add(new DocumentoNaoIncluido(posicoes.get(d), d.origem().descricao(), d.id(), n.titulo(),
                    d.data(), motivo(n.motivo())));
        }
        PedidoDeDossie pedido = new PedidoDeDossie(UUID.randomUUID().toString(), usuario.login(), idRequerimento,
                capa(detalhe, usuario, agora), itens, naoSelecionados, selecionados.listados(), agora,
                agora.plusMinutes(parametros.ttlMinutos()));
        pedidos.salvar(pedido);
        fila.submeter(() -> gerador.processar(pedido));
        return pedido.id();
    }

    private static String motivo(SelecaoParaDossie.Motivo motivo) {
        return switch (motivo) {
            case NAO_SELECIONADO -> DocumentoNaoIncluido.NAO_SELECIONADO;
            case INDISPONIVEL -> DocumentoNaoIncluido.INDISPONIVEL;
            case NAO_PDF -> DocumentoNaoIncluido.NAO_PDF;
            case SIGILOSO -> DocumentoNaoIncluido.SIGILOSO;
        };
    }

    private static DadosDaCapa capa(DetalheDoRequerimento detalhe, Usuario usuario, LocalDateTime agora) {
        Requerimento r = detalhe.requerimento();
        Identificacao identificacao = r.identificacao();
        ObjetoDoRequerimento objeto = detalhe.objeto();
        List<String> atividades = new ArrayList<>();
        objeto.finalidadesAtividades().forEach(p -> {
            String finalidade = p.finalidade() == null ? "" : p.finalidade();
            String atividade = p.atividade() == null ? "" : p.atividade();
            String linha = finalidade.isBlank() ? atividade : atividade.isBlank() ? finalidade : finalidade + " - " + atividade;
            if (!linha.isBlank()) {
                atividades.add(linha);
            }
        });
        if (objeto.atividadeLicenciamento() != null && objeto.atividadeLicenciamento().atividade() != null) {
            atividades.add(objeto.atividadeLicenciamento().atividade());
        }
        return new DadosDaCapa(identificacao.rotulo(), identificacao.numero(),
                r.interessado() == null ? null : r.interessado().nome(),
                formatarCpfCnpj(r.cpfCnpjDoInteressado()), r.tipoProcesso(), r.situacao().descricao(), r.abertura(),
                objeto.objetivos(), atividades, agora, usuario.nome());
    }

    static String formatarCpfCnpj(String valor) {
        if (valor == null) {
            return null;
        }
        String d = valor.replaceAll("\\D", "");
        if (d.length() == 11) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12)
                    + "-" + d.substring(12);
        }
        return valor;
    }
}
