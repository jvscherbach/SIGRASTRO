import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NEVER, Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { Perfil, Usuario } from '../../core/usuario.model';
import { PageResponse } from '../../shared/api-tipos';
import { AcompanhamentoDeDossieService } from '../documentos/acompanhamento-dossie.service';
import { DossiesApiService } from '../documentos/documentos-api.service';
import { DossieGerado, SituacaoDossieGerado } from '../documentos/documentos.models';
import { DossiesGeradosComponent, INTERVALO_DA_LISTA_MS, rotuloDaSituacao } from './dossies-gerados.component';

function usuario(perfil: Perfil): Usuario {
  return {
    login: perfil.toLowerCase(),
    nome: 'Usuário Fictício',
    perfil,
    perfilDescricao: perfil,
    cpfCnpj: null,
    permissoes: {
      verSigilosos: perfil !== 'OPERADOR',
      verAuditoriaAcessos: perfil === 'ADMIN',
      acessarConfiguracoes: perfil === 'ADMIN',
      restritoAoInteressado: perfil === 'OPERADOR',
      verResponsavelTarefa: perfil !== 'OPERADOR',
    },
  };
}

function pedido(sobre: Partial<DossieGerado> = {}): DossieGerado {
  return {
    id: 'p1',
    idRequerimento: 101,
    identificacao: { rotulo: 'Processo', numero: '2825/2025' },
    situacao: 'CONCLUIDO',
    criadoEm: '2099-01-01T10:00:00',
    expiraEm: '2099-01-01T10:31:00',
    documentos: 12,
    falhas: 0,
    listados: 15,
    tamanhoBytes: 4 * 1024 * 1024,
    entregaPorTicket: false,
    temArquivo: true,
    arquivoGerado: true,
    ...sobre,
  };
}

function pagina(conteudo: DossieGerado[], sobre: Partial<PageResponse<DossieGerado>> = {}): PageResponse<DossieGerado> {
  return { conteudo, pagina: 0, tamanho: 20, totalElementos: conteudo.length, totalPaginas: 1, ...sobre };
}

const pdf = (disposicao: string) =>
  new HttpResponse({
    body: new Blob(['%PDF-'], { type: 'application/pdf' }),
    headers: new HttpHeaders({ 'Content-Disposition': `${disposicao}; filename="dossie-processo-2825-2025.pdf"` }),
    status: 200,
  });

describe('DossiesGeradosComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  const atual = signal<Usuario | null>(null);
  let fixture: ComponentFixture<DossiesGeradosComponent>;
  let raiz: HTMLElement;

  function abrir(conteudo: DossieGerado[] | PageResponse<DossieGerado>, perfil: Perfil = 'OPERADOR'): void {
    atual.set(usuario(perfil));
    api['listar'].mockReturnValue(of(Array.isArray(conteudo) ? pagina(conteudo) : conteudo));
    fixture = TestBed.createComponent(DossiesGeradosComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const linhas = () => Array.from(raiz.querySelectorAll<HTMLTableRowElement>('tbody tr[data-situacao]'));
  const botoes = (linha: HTMLElement) => Array.from(linha.querySelectorAll('button')).map((b) => b.textContent!.trim());
  const botao = (rotulo: string, onde: HTMLElement = raiz) =>
    Array.from(onde.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo))!;
  const clicar = (b: HTMLButtonElement) => {
    b.click();
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      listar: vi.fn(),
      arquivo: vi.fn(() => of(pdf('attachment'))),
      ticket: vi.fn(() => of({ ticket: 'tk-1', expiraEm: '2099-01-01T10:01:00' })),
      urlDeDownloadDireto: vi.fn((id: string, ticket: string) => `/api/dossies/${id}/arquivo?disposicao=attachment&ticket=${ticket}`),
      cancelar: vi.fn(() => of(undefined)),
      andamento: vi.fn(() => NEVER),
      continuar: vi.fn(),
      criar: vi.fn(),
    };
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/dossie-1');
    URL.revokeObjectURL = vi.fn();
    TestBed.configureTestingModule({
      imports: [DossiesGeradosComponent],
      providers: [
        provideRouter([]),
        { provide: DossiesApiService, useValue: api },
        { provide: AuthService, useValue: { usuario: atual, token: () => 'token' } },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.querySelectorAll('app-gaveta-dossie').forEach((g) => g.remove());
  });

  describe('carregamento e estados', () => {
    it('pede a primeira página, sem "só os meus"', () => {
      abrir([pedido()]);
      expect(api['listar']).toHaveBeenCalledWith(0, 20, false);
      expect(linhas()).toHaveLength(1);
      expect(texto()).toContain('Processo 2825/2025');
    });

    it('carregando', () => {
      atual.set(usuario('OPERADOR'));
      api['listar'].mockReturnValue(NEVER);
      fixture = TestBed.createComponent(DossiesGeradosComponent);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Carregando dossiês');
    });

    it('vazio: diz que o histórico é em memória', () => {
      abrir([]);
      expect(texto()).toContain('Você ainda não gerou dossiês nesta sessão do serviço');
      expect(texto()).toContain('some quando o serviço é reiniciado');
    });

    it('vazio para o Gestor sem filtro', () => {
      abrir([], 'OPERADOR_SIGILO');
      expect(texto()).toContain('Nenhum dossiê foi gerado nesta sessão do serviço');
    });

    it('erro com "Tentar novamente", sem o detail técnico', () => {
      atual.set(usuario('OPERADOR'));
      api['listar'].mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500, error: { errorCode: 'ERRO_INTERNO', detail: 'stack' } })));
      fixture = TestBed.createComponent(DossiesGeradosComponent);
      fixture.detectChanges();
      raiz = fixture.nativeElement;
      expect(texto()).toContain('Ocorreu um erro inesperado.');
      expect(texto()).not.toContain('stack');

      api['listar'].mockReturnValue(of(pagina([pedido()])));
      clicar(botao('Tentar novamente'));
      expect(linhas()).toHaveLength(1);
    });
  });

  describe('situações e ações', () => {
    const casos: { situacao: SituacaoDossieGerado; sobre?: Partial<DossieGerado>; rotulo: string; acoes: string[] }[] = [
      { situacao: 'AGUARDANDO', sobre: { temArquivo: false, tamanhoBytes: null }, rotulo: 'Na fila', acoes: ['Cancelar', 'Abrir requerimento'] },
      { situacao: 'OBTENDO', sobre: { temArquivo: false, tamanhoBytes: null }, rotulo: 'Obtendo documentos', acoes: ['Cancelar', 'Abrir requerimento'] },
      { situacao: 'MONTANDO', sobre: { temArquivo: false, tamanhoBytes: null }, rotulo: 'Montando o PDF', acoes: ['Cancelar', 'Abrir requerimento'] },
      { situacao: 'PENDENTE_DECISAO', sobre: { temArquivo: false, tamanhoBytes: null, falhas: 2 }, rotulo: 'Aguardando decisão', acoes: ['Decidir', 'Abrir requerimento'] },
      { situacao: 'CONCLUIDO', rotulo: 'Pronto', acoes: ['Pré-visualizar', 'Baixar PDF', 'Abrir requerimento'] },
      { situacao: 'CONCLUIDO', sobre: { entregaPorTicket: true }, rotulo: 'Pronto', acoes: ['Baixar PDF', 'Abrir requerimento'] },
      { situacao: 'CANCELADO', sobre: { temArquivo: false, tamanhoBytes: null, arquivoGerado: false }, rotulo: 'Cancelado', acoes: ['Abrir requerimento'] },
      { situacao: 'EXPIRADO', sobre: { temArquivo: false, tamanhoBytes: null }, rotulo: 'Expirado', acoes: ['Abrir requerimento'] },
      { situacao: 'EXPIRADO', sobre: { temArquivo: false, tamanhoBytes: null, arquivoGerado: false }, rotulo: 'Expirado · nenhum arquivo gerado', acoes: ['Abrir requerimento'] },
      { situacao: 'BAIXADO', sobre: { temArquivo: false, tamanhoBytes: null }, rotulo: 'Arquivo baixado', acoes: ['Abrir requerimento'] },
    ];

    for (const caso of casos) {
      it(`${caso.rotulo}${caso.sobre?.entregaPorTicket ? ' (grande)' : ''}: ações ${caso.acoes.join(', ')}`, () => {
        abrir([pedido({ situacao: caso.situacao, ...caso.sobre })]);
        const linha = linhas()[0];
        expect(linha.querySelector('.tag')!.textContent!.trim()).toBe(caso.rotulo);
        expect(botoes(linha)).toEqual(caso.acoes);
      });
    }

    it('rótulo da situação', () => {
      expect(rotuloDaSituacao({ situacao: 'EXPIRADO', arquivoGerado: true })).toBe('Expirado');
      expect(rotuloDaSituacao({ situacao: 'EXPIRADO', arquivoGerado: false })).toBe('Expirado · nenhum arquivo gerado');
    });

    it('mostra documentos de listados, falhas e tamanho', () => {
      abrir([pedido({ situacao: 'PENDENTE_DECISAO', falhas: 2, temArquivo: false, tamanhoBytes: null }), pedido({ id: 'p2' })]);
      expect(linhas()[0].textContent).toContain('12 de 15');
      expect(linhas()[0].textContent).toContain('2 com problema');
      expect(linhas()[0].textContent).toContain('—');
      expect(linhas()[1].textContent).toContain('4 MB');
    });

    it('expira em com tempo restante, só para pedido vivo', () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2099-01-01T13:00:00Z')); // 09:00 em Cuiabá (UTC-4)
      abrir([pedido({ expiraEm: '2099-01-01T09:30:00' }), pedido({ id: 'p2', situacao: 'BAIXADO', temArquivo: false, expiraEm: '2099-01-01T09:30:00' })]);

      expect(linhas()[0].querySelector('.expira')!.textContent).toContain('01/01/2099 09:30');
      expect(linhas()[0].querySelector('.expira')!.textContent).toContain('restam 30 min');
      expect(linhas()[1].querySelector('.expira')!.textContent).not.toContain('restam');
    });

    it('Baixar PDF por Blob com o nome do Content-Disposition e recarrega a lista', () => {
      abrir([pedido()]);
      const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

      clicar(botao('Baixar PDF'));

      expect(api['arquivo']).toHaveBeenCalledWith('p1', 'attachment');
      expect((URL.createObjectURL as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBeInstanceOf(Blob);
      expect(api['listar']).toHaveBeenCalledTimes(2);
      clique.mockRestore();
    });

    it('dossiê grande: ticket e download direto, sem Blob', () => {
      abrir([pedido({ entregaPorTicket: true })]);
      const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

      clicar(botao('Baixar PDF'));

      expect(api['ticket']).toHaveBeenCalledWith('p1');
      expect(api['arquivo']).not.toHaveBeenCalled();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
      expect(clique).toHaveBeenCalledOnce();
      clique.mockRestore();
    });

    it('Pré-visualizar abre o PDF em moldura e revoga a URL ao fechar', () => {
      api['arquivo'].mockReturnValue(of(pdf('inline')));
      abrir([pedido()]);

      clicar(botao('Pré-visualizar'));
      expect(api['arquivo']).toHaveBeenCalledWith('p1', 'inline');
      expect(raiz.querySelector('.previa iframe')).not.toBeNull();

      clicar(botao('', raiz.querySelector<HTMLElement>('.previa')!));
      expect(raiz.querySelector('.previa')).toBeNull();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/dossie-1');
    });

    it('erro na ação aparece na linha, pelo errorCode', () => {
      api['arquivo'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 410, error: { errorCode: 'DOSSIE_EXPIRADO' } })));
      abrir([pedido()]);
      clicar(botao('Baixar PDF'));
      expect(linhas()[0].querySelector('.erro-acao')!.textContent).toContain('O dossiê expirou. Gere-o novamente.');
    });

    it('Cancelar chama o DELETE e recarrega', () => {
      abrir([pedido({ situacao: 'OBTENDO', temArquivo: false })]);
      clicar(botao('Cancelar'));
      expect(api['cancelar']).toHaveBeenCalledWith('p1');
      expect(api['listar']).toHaveBeenCalledTimes(2);
    });

    it('Decidir abre a gaveta com o pedido existente, sem criar outro', () => {
      abrir([pedido({ situacao: 'PENDENTE_DECISAO', temArquivo: false })]);
      clicar(botao('Decidir'));

      expect(TestBed.inject(AcompanhamentoDeDossieService).gavetaAberta()).toBe(true);
      expect(api['andamento']).toHaveBeenCalledWith('p1');
      expect(api['criar']).not.toHaveBeenCalled();
    });

    it('Abrir requerimento vai ao detalhe', () => {
      abrir([pedido()]);
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      clicar(botao('Abrir requerimento'));
      expect(navegar).toHaveBeenCalledWith(['/consulta/requerimentos', 101]);
    });
  });

  describe('perfis e filtros', () => {
    it('Operador: sem "Gerado por" e sem "Só os meus"', () => {
      abrir([pedido()], 'OPERADOR');
      expect(texto()).not.toContain('Gerado por');
      expect(raiz.querySelector('.somente-meus')).toBeNull();
    });

    it('Gestor: coluna "Gerado por" quando a API manda e filtro "Só os meus"', () => {
      abrir([pedido({ geradoPor: 'Gestor Demo' })], 'OPERADOR_SIGILO');
      expect(raiz.querySelector('thead')!.textContent).toContain('Gerado por');
      expect(linhas()[0].querySelector('.gerado-por')!.textContent).toContain('Gestor Demo');

      const caixa = raiz.querySelector<HTMLInputElement>('.somente-meus input')!;
      caixa.checked = true;
      caixa.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(api['listar']).toHaveBeenLastCalledWith(0, 20, true);
    });

    it('filtro por situação na página carregada', () => {
      abrir([pedido(), pedido({ id: 'p2', situacao: 'BAIXADO', temArquivo: false })]);
      const filtro = raiz.querySelector<HTMLSelectElement>('select[aria-label="Situação"]')!;
      filtro.value = 'BAIXADO';
      filtro.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(linhas().map((l) => l.dataset['situacao'])).toEqual(['BAIXADO']);

      filtro.value = 'CANCELADO';
      filtro.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(texto()).toContain('Nenhum dossiê com essa situação nesta página');
    });

    it('paginação', () => {
      abrir(pagina([pedido()], { totalElementos: 25, totalPaginas: 2 }));
      expect(texto()).toContain('Página 1 de 2');
      clicar(botao('Próxima'));
      expect(api['listar']).toHaveBeenLastCalledWith(1, 20, false);
    });
  });

  describe('atualização automática', () => {
    it('atualiza a cada intervalo longo e para ao sair da tela', () => {
      vi.useFakeTimers();
      abrir([pedido()]);
      expect(api['listar']).toHaveBeenCalledTimes(1);

      vi.advanceTimersByTime(INTERVALO_DA_LISTA_MS);
      expect(api['listar']).toHaveBeenCalledTimes(2);

      fixture.destroy();
      vi.advanceTimersByTime(INTERVALO_DA_LISTA_MS * 3);
      expect(api['listar']).toHaveBeenCalledTimes(2);
    });

    it('com a aba oculta não atualiza; ao voltar, atualiza na hora', () => {
      vi.useFakeTimers();
      abrir([pedido()]);
      const visibilidade = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
      document.dispatchEvent(new Event('visibilitychange'));
      vi.advanceTimersByTime(INTERVALO_DA_LISTA_MS * 3);
      expect(api['listar']).toHaveBeenCalledTimes(1);

      visibilidade.mockReturnValue('visible');
      document.dispatchEvent(new Event('visibilitychange'));
      expect(api['listar']).toHaveBeenCalledTimes(2);
      visibilidade.mockRestore();
      fixture.destroy();
    });

    it('a atualização mantém a tabela na tela (sem "Carregando")', () => {
      abrir([pedido()]);
      const pendente = new Subject<PageResponse<DossieGerado>>();
      api['listar'].mockReturnValue(pendente);
      clicar(botao('Atualizar'));
      expect(linhas()).toHaveLength(1);
      expect(texto()).toContain('Atualizando');
      expect(texto()).not.toContain('Carregando dossiês');
    });
  });
});
