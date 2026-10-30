import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { Usuario } from '../../core/usuario.model';
import { ToastService } from '../../shared/toast.service';
import { AcompanhamentoDeDossieService, INTERVALO_INICIAL_MS } from './acompanhamento-dossie.service';
import { DossiesApiService } from './documentos-api.service';
import { AndamentoDossie, ReferenciaDocumento } from './documentos.models';
import { GavetaDossieComponent } from './gaveta-dossie.component';
import { IndicadorDossieComponent } from './indicador-dossie.component';

const REFS: ReferenciaDocumento[] = [
  { origem: 'DOCUMENTO_PROCESSO', id: '1' },
  { origem: 'JUNTADA', id: '2' },
];

function andamento(sobre: Partial<AndamentoDossie> = {}): AndamentoDossie {
  return {
    id: 'pedido-1',
    status: 'OBTENDO',
    total: 40,
    processados: 12,
    erro: null,
    falhas: [],
    paginas: null,
    expiraEm: '2026-10-21T11:30:00',
    itens: [{ origem: 'JUNTADA', id: '2', titulo: 'Ofício', situacao: 'OBTENDO' }],
    ...sobre,
  };
}

const USUARIO = { login: 'maria', nome: 'Maria de Teste', perfil: 'ADMIN' } as Usuario;

describe('AcompanhamentoDeDossieService (geração em segundo plano)', () => {
  let api: Record<'criar' | 'andamento' | 'cancelar' | 'continuar' | 'arquivo', ReturnType<typeof vi.fn>>;
  let servico: AcompanhamentoDeDossieService;
  let toast: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-21T15:00:00Z'));
    api = {
      criar: vi.fn(() => of({ id: 'pedido-1' })),
      andamento: vi.fn(() => of(andamento())),
      cancelar: vi.fn(() => of(undefined)),
      continuar: vi.fn(() => of(undefined)),
      arquivo: vi.fn(),
    };
    URL.revokeObjectURL = vi.fn();
    TestBed.configureTestingModule({
      imports: [GavetaDossieComponent, IndicadorDossieComponent],
      providers: [{ provide: DossiesApiService, useValue: api }, provideHttpClient(), provideHttpClientTesting()],
    });
    servico = TestBed.inject(AcompanhamentoDeDossieService);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const avancar = (ms: number) => vi.advanceTimersByTimeAsync(ms);
  const entrar = (login = 'maria') => {
    TestBed.inject(AuthService).login(login, 'x').subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ token: 't', tipo: 'Bearer', expiraEm: '', usuario: { ...USUARIO, login } });
    TestBed.tick();
  };

  it('fechar a gaveta durante o ENVIO do POST não cancela o pedido: ele nasce e o acompanhamento começa', () => {
    const envio = new Subject<{ id: string }>();
    api.criar.mockReturnValue(envio);

    servico.gerar(101, REFS);
    servico.fecharGaveta();

    expect(envio.observed).toBe(true);
    envio.next({ id: 'pedido-1' });
    expect(api.andamento).toHaveBeenCalledWith('pedido-1');
    expect(servico.pedidoId()).toBe('pedido-1');
    expect(servico.gavetaAberta()).toBe(false);
  });

  it('com a gaveta fechada durante OBTENDO o polling continua e o indicador avança', async () => {
    api.andamento.mockReturnValueOnce(of(andamento({ processados: 12 }))).mockReturnValue(of(andamento({ processados: 20 })));
    servico.gerar(101, REFS);
    servico.fecharGaveta();
    expect(servico.indicador()).toMatchObject({ tipo: 'andamento', texto: 'Gerando dossiê… 12 de 40', percentual: 30 });

    await avancar(INTERVALO_INICIAL_MS);

    expect(servico.indicador()).toMatchObject({ texto: 'Gerando dossiê… 20 de 40', percentual: 50 });
    expect(api.cancelar).not.toHaveBeenCalled();
  });

  it('conclusão com a gaveta fechada: indicador "Dossiê pronto" e aviso com a ação Abrir', async () => {
    api.andamento.mockReturnValueOnce(of(andamento())).mockReturnValue(of(andamento({ status: 'CONCLUIDO', processados: 40 })));
    servico.gerar(101, REFS);
    servico.fecharGaveta();

    await avancar(INTERVALO_INICIAL_MS);

    expect(servico.indicador()).toMatchObject({ tipo: 'pronto', texto: 'Dossiê pronto' });
    const aviso = toast.toasts().find((t) => t.texto.includes('pronto'));
    expect(aviso?.acao?.rotulo).toBe('Abrir');
    toast.executar(aviso!);
    expect(servico.gavetaAberta()).toBe(true);
  });

  it('parada à espera de decisão com a gaveta fechada: destaque no indicador e aviso', async () => {
    api.andamento.mockReturnValueOnce(of(andamento())).mockReturnValue(of(andamento({ status: 'PENDENTE_DECISAO' })));
    servico.gerar(101, REFS);
    servico.fecharGaveta();

    await avancar(INTERVALO_INICIAL_MS);

    expect(servico.indicador()?.tipo).toBe('decisao');
    expect(toast.toasts().some((t) => t.texto.includes('decisão') && t.acao?.rotulo === 'Abrir')).toBe(true);
  });

  it('gaveta aberta ao concluir: sem aviso (o usuário está vendo)', () => {
    api.andamento.mockReturnValue(of(andamento({ status: 'CONCLUIDO' })));
    servico.gerar(101, REFS);

    expect(toast.toasts()).toEqual([]);
  });

  it('reabrir pelo indicador mostra a gaveta no mesmo pedido, sem novo POST', () => {
    servico.gerar(101, REFS);
    servico.fecharGaveta();

    servico.abrirGaveta();

    expect(servico.gavetaAberta()).toBe(true);
    expect(api.criar).toHaveBeenCalledTimes(1);
  });

  it('só "Cancelar geração" chama o DELETE; fechar a gaveta nunca', () => {
    servico.gerar(101, REFS);
    servico.fecharGaveta();
    servico.abrirGaveta();
    servico.fecharGaveta();
    expect(api.cancelar).not.toHaveBeenCalled();

    servico.cancelarGeracao();

    expect(api.cancelar).toHaveBeenCalledWith('pedido-1');
  });

  it('segundo "Gerar dossiê" com pedido ativo: aviso, sem novo POST, e a ação abre a gaveta atual', () => {
    servico.gerar(101, REFS);
    servico.fecharGaveta();

    const iniciou = servico.gerar(202, REFS);

    expect(iniciou).toBe(false);
    expect(api.criar).toHaveBeenCalledTimes(1);
    const aviso = toast.toasts().find((t) => t.texto === 'Já existe uma geração em andamento.');
    expect(aviso?.acao?.rotulo).toBe('Abrir');
    toast.executar(aviso!);
    expect(servico.gavetaAberta()).toBe(true);
    expect(servico.idRequerimento()).toBe(101);
  });

  it('pedido encerrado e visto sai do indicador e não bloqueia o próximo', () => {
    api.andamento.mockReturnValue(of(andamento({ status: 'CANCELADO' })));
    servico.gerar(101, REFS);
    servico.fecharGaveta();

    expect(servico.indicador()).toBeNull();
    expect(servico.gerar(202, REFS)).toBe(true);
  });

  it('sair da aplicação (logout) encerra o acompanhamento e limpa timers e inscrições', async () => {
    entrar();
    const pendente = new Subject<AndamentoDossie>();
    api.andamento.mockReturnValueOnce(of(andamento())).mockReturnValue(pendente);
    servico.gerar(101, REFS);
    servico.fecharGaveta();
    await avancar(INTERVALO_INICIAL_MS);
    expect(pendente.observed).toBe(true);

    TestBed.inject(AuthService).limparSessao();
    TestBed.tick();

    expect(servico.indicador()).toBeNull();
    expect(pendente.observed).toBe(false);
    await avancar(120_000);
    expect(api.andamento).toHaveBeenCalledTimes(2);
  });

  it('trocar de usuário encerra o acompanhamento do anterior', () => {
    entrar('maria');
    servico.gerar(101, REFS);
    expect(servico.ativo()).toBe(true);

    entrar('joao');

    expect(servico.ativo()).toBe(false);
  });

  describe('com a gaveta e o indicador na tela', () => {
    let fixture: ComponentFixture<GavetaDossieComponent>;

    it('clicar no fundo e Esc minimizam a gaveta (não encerram) durante a geração', () => {
      servico.gerar(101, REFS);
      fixture = TestBed.createComponent(GavetaDossieComponent);
      fixture.detectChanges();
      const raiz = fixture.nativeElement as HTMLElement;

      raiz.querySelector<HTMLElement>('.scrim')!.click();
      expect(servico.gavetaAberta()).toBe(false);
      expect(servico.ativo()).toBe(true);

      servico.abrirGaveta();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(servico.gavetaAberta()).toBe(false);
      expect(servico.ativo()).toBe(true);
      expect(api.cancelar).not.toHaveBeenCalled();
    });

    it('o indicador mostra o texto e a barra, e o clique reabre a gaveta', () => {
      servico.gerar(101, REFS);
      servico.fecharGaveta();
      const indicador = TestBed.createComponent(IndicadorDossieComponent);
      indicador.detectChanges();
      const raiz = indicador.nativeElement as HTMLElement;

      expect(raiz.textContent).toContain('Gerando dossiê… 12 de 40');
      expect(raiz.querySelector('.ind-barra i')).not.toBeNull();
      raiz.querySelector<HTMLButtonElement>('.ind-abrir')!.click();
      expect(servico.gavetaAberta()).toBe(true);
    });

    it('sem pedido ativo o indicador não ocupa nada na barra', () => {
      const indicador = TestBed.createComponent(IndicadorDossieComponent);
      indicador.detectChanges();

      expect((indicador.nativeElement as HTMLElement).querySelector('.ind-dossie')).toBeNull();
    });

    it('dispensar tira o "Dossiê pronto" do indicador', () => {
      api.andamento.mockReturnValue(of(andamento({ status: 'CONCLUIDO' })));
      servico.gerar(101, REFS);
      servico.fecharGaveta();
      const indicador = TestBed.createComponent(IndicadorDossieComponent);
      indicador.detectChanges();
      const raiz = indicador.nativeElement as HTMLElement;
      expect(raiz.textContent).toContain('Dossiê pronto');

      raiz.querySelector<HTMLButtonElement>('.ind-dispensar')!.click();
      indicador.detectChanges();

      expect(raiz.querySelector('.ind-dossie')).toBeNull();
    });
  });
});
