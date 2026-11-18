import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { Usuario } from '../../core/usuario.model';
import { AuditoriaApiService } from './auditoria-api.service';
import { AuditoriaComponent, ESPERA_DA_BUSCA_MS } from './auditoria.component';
import { EventoDeAuditoria, EventoDetalhado, EventosResposta } from './auditoria.models';

function evento(sobre: Partial<EventoDeAuditoria> = {}): EventoDeAuditoria {
  return {
    id: 1,
    dataHora: '2026-10-21T10:30:00-04:00',
    tipo: 'VISUALIZACAO',
    rotulo: 'Visualização',
    usuario: { login: 'maria.teste', nome: 'Maria de Teste', perfil: 'OPERADOR_SIGILO' },
    idRequerimento: 101,
    numeroProcesso: '2825/2025',
    numeroRequerimento: '3331/2025',
    sigiloso: false,
    arquivo: 'oficio.pdf',
    idPedido: null,
    detalhe: 'Documento visualizado',
    ...sobre,
  };
}

function resposta(itens: EventoDeAuditoria[], extra: Partial<EventosResposta> = {}): EventosResposta {
  return {
    conteudo: itens,
    pagina: 0,
    tamanho: 20,
    totalElementos: itens.length,
    totalPaginas: 1,
    indicadores: { dossiesGerados: 4, eventosComSigilosos: 2, usuarios: 3 },
    ...extra,
  };
}

const SIGILOSO = evento({
  id: 2,
  sigiloso: true,
  arquivo: null,
  detalhe: 'Documento sigiloso',
  usuario: { login: 'joao.teste', nome: 'João de Teste', perfil: 'ADMIN' },
});

function usuario(perfil: 'OPERADOR' | 'OPERADOR_SIGILO' | 'ADMIN'): Usuario {
  const ve = perfil !== 'OPERADOR';
  return {
    login: 'x',
    nome: 'Fulano de Teste',
    perfil,
    perfilDescricao: perfil,
    cpfCnpj: null,
    permissoes: {
      verSigilosos: ve,
      verAuditoriaAcessos: perfil === 'ADMIN',
      acessarConfiguracoes: perfil === 'ADMIN',
      restritoAoInteressado: perfil === 'OPERADOR',
      verResponsavelTarefa: ve,
    },
  };
}

describe('AuditoriaComponent', () => {
  let api: Record<'listar' | 'detalhe' | 'exportar', ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<AuditoriaComponent>;
  let raiz: HTMLElement;

  function abrir(perfil: 'OPERADOR' | 'OPERADOR_SIGILO' | 'ADMIN' = 'OPERADOR_SIGILO', grupo = 'documentos'): void {
    TestBed.configureTestingModule({
      imports: [AuditoriaComponent],
      providers: [
        { provide: AuditoriaApiService, useValue: api },
        { provide: AuthService, useValue: { usuario: signal(usuario(perfil)).asReadonly() } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { grupo } } } },
      ],
    });
    fixture = TestBed.createComponent(AuditoriaComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const botao = (rotulo: string) => Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo));
  const ultimoFiltro = () => api.listar.mock.calls[api.listar.mock.calls.length - 1][1];

  beforeEach(() => {
    vi.useFakeTimers();
    api = {
      listar: vi.fn(() => of(resposta([evento(), SIGILOSO]))),
      detalhe: vi.fn(() => of({ ...evento(), origem: '10.0.0.1', correlationId: 'c-1', documentos: [], alteracoes: [] } as EventoDetalhado)),
      exportar: vi.fn(),
    };
    URL.createObjectURL = vi.fn(() => 'blob:csv');
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('abre em 7 dias, lista os eventos e mostra os indicadores e a faixa de memória', () => {
    abrir();

    expect(api.listar).toHaveBeenCalledWith('documentos', expect.objectContaining({ periodo: '7d', pagina: 0 }));
    expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(raiz.querySelector('[data-ind="dossiesGerados"] b')!.textContent).toBe('4');
    expect(raiz.querySelector('[data-ind="eventosComSigilosos"] b')!.textContent).toBe('2');
    expect(raiz.querySelector('[data-ind="usuarios"] b')!.textContent).toBe('3');
    expect(texto()).toContain('Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço.');
    expect(raiz.querySelector('button[aria-pressed="true"]')!.textContent).toContain('Últimos 7 dias');
  });

  it('data e hora com o código do evento, processo e destaque lateral só nos sigilosos', () => {
    abrir();
    const [comum, sigiloso] = Array.from(raiz.querySelectorAll('tbody tr'));

    expect(comum.textContent).toContain('21/10/2026 10:30');
    expect(comum.textContent).toContain('VISUALIZACAO');
    expect(comum.textContent).toContain('Processo 2825/2025');
    expect(comum.classList.contains('aud-sigiloso')).toBe(false);
    expect(sigiloso.classList.contains('aud-sigiloso')).toBe(true);
    expect(sigiloso.textContent).toContain('Sigiloso');
  });

  it('Gestor e Administrador têm "Somente sigilosos"; o filtro vai ao servidor', () => {
    abrir('ADMIN');
    const caixa = raiz.querySelector<HTMLInputElement>('.aud-somente-sigilosos input')!;

    caixa.click();
    fixture.detectChanges();

    expect(ultimoFiltro()).toMatchObject({ somenteSigilosos: true, pagina: 0 });
  });

  it('Operador: sem "Somente sigilosos", com o texto dos seus processos e o mascaramento vindo do servidor', () => {
    api.listar.mockReturnValue(of(resposta([evento({ id: 3, sigiloso: true, arquivo: null, detalhe: 'Documento sigiloso' })])));
    abrir('OPERADOR');

    expect(raiz.querySelector('.aud-somente-sigilosos')).toBeNull();
    expect(texto()).toContain('Você vê os eventos dos seus processos e requerimentos, feitos por qualquer usuário.');
    expect(texto()).toContain('Documento sigiloso');
  });

  it('trocar o período volta à primeira página e consulta de novo', () => {
    abrir();

    botao('Hoje')!.click();
    fixture.detectChanges();
    expect(ultimoFiltro()).toMatchObject({ periodo: 'hoje', pagina: 0 });
    botao('Todo o período')!.click();
    expect(ultimoFiltro()).toMatchObject({ periodo: 'todo' });
  });

  it('o texto espera a pausa na digitação e consulta uma vez; tipo e usuário entram no filtro', async () => {
    abrir();
    api.listar.mockClear();
    const busca = raiz.querySelector<HTMLInputElement>('.search input')!;

    busca.value = 'ofício';
    busca.dispatchEvent(new Event('input'));
    busca.value = 'ofício 2';
    busca.dispatchEvent(new Event('input'));
    expect(api.listar).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(ESPERA_DA_BUSCA_MS);
    expect(api.listar).toHaveBeenCalledTimes(1);
    expect(ultimoFiltro()).toMatchObject({ texto: 'ofício 2' });

    const tipo = raiz.querySelector<HTMLSelectElement>('select[aria-label="Tipo de evento"]')!;
    tipo.value = 'FALHA';
    tipo.dispatchEvent(new Event('change'));
    expect(ultimoFiltro()).toMatchObject({ tipo: 'FALHA' });

    const usuarioCampo = raiz.querySelector<HTMLInputElement>('.aud-usuario')!;
    usuarioCampo.value = 'maria.teste';
    usuarioCampo.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(ESPERA_DA_BUSCA_MS);
    expect(ultimoFiltro()).toMatchObject({ usuario: 'maria.teste', tipo: 'FALHA', texto: 'ofício 2' });
  });

  it('paginação pede a página pedida e mantém os filtros', () => {
    api.listar.mockReturnValue(of(resposta([evento()], { totalElementos: 45, totalPaginas: 3 })));
    abrir();

    botao('Próxima')!.click();

    expect(ultimoFiltro()).toMatchObject({ pagina: 1, periodo: '7d' });
  });

  it('vazio e erro com "Tentar novamente"', () => {
    api.listar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'ERRO_INTERNO' } })));
    abrir();
    expect(raiz.querySelector('.aud-erro')).not.toBeNull();

    api.listar.mockReturnValue(of(resposta([])));
    botao('Tentar novamente')!.click();
    fixture.detectChanges();

    expect(raiz.querySelector('.aud-vazio')).not.toBeNull();
    expect(texto()).toContain('Nenhum evento encontrado');
  });

  it('detalhe: abre a gaveta com documentos e sigilo de cada um e fecha com Esc, sem inscrição pendurada', () => {
    const detalhe = new Subject<EventoDetalhado>();
    api.detalhe.mockReturnValue(detalhe);
    abrir();

    botao('Detalhe')!.click();
    fixture.detectChanges();
    expect(api.detalhe).toHaveBeenCalledWith('documentos', 1);
    expect(detalhe.observed).toBe(true);
    detalhe.next({
      ...evento(),
      origem: '10.0.0.1',
      correlationId: 'c-1',
      alteracoes: [],
      documentos: [
        { origem: 'JUNTADA', id: '2', titulo: 'Ofício', nomeDoArquivo: 'oficio.pdf', sigiloso: false, ordem: 1 },
        { origem: 'IMOVEL', id: '3', titulo: 'Documento sigiloso', nomeDoArquivo: null, sigiloso: true, ordem: 2 },
      ],
    });
    fixture.detectChanges();
    const gaveta = document.body.querySelector('app-auditoria-detalhe')!;
    expect(gaveta.textContent).toContain('10.0.0.1');
    expect(gaveta.querySelectorAll('.aud-documentos li')).toHaveLength(2);
    expect(gaveta.querySelector('.aud-documentos li.sigiloso')!.textContent).toContain('Documento sigiloso');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(document.body.querySelector('app-auditoria-detalhe')).toBeNull();
    expect(detalhe.observed).toBe(false);
  });

  it('detalhe: clicar fora fecha e clicar dentro não', () => {
    abrir();
    botao('Detalhe')!.click();
    fixture.detectChanges();

    document.body.querySelector<HTMLElement>('app-auditoria-detalhe .drawer')!.click();
    fixture.detectChanges();
    expect(document.body.querySelector('app-auditoria-detalhe')).not.toBeNull();
    document.body.querySelector<HTMLElement>('app-auditoria-detalhe .scrim')!.click();
    fixture.detectChanges();
    expect(document.body.querySelector('app-auditoria-detalhe')).toBeNull();
  });

  it('exportar CSV baixa o Blob com o nome do servidor, usa o filtro atual e avisa quando truncado', () => {
    api.exportar.mockReturnValue(of({ blob: new Blob(['x']), nome: 'auditoria-documentos-20261021.csv', truncado: true }));
    abrir();
    botao('Hoje')!.click();
    const cliques: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      cliques.push(this.download);
    });

    botao('Exportar CSV')!.click();
    fixture.detectChanges();

    expect(api.exportar).toHaveBeenCalledWith('documentos', expect.objectContaining({ periodo: 'hoje' }));
    expect(cliques).toEqual(['auditoria-documentos-20261021.csv']);
    expect(raiz.querySelector('.aud-truncado')!.textContent).toContain('cortado');
  });

  it('exportar com erro mostra a mensagem e não baixa nada', () => {
    api.exportar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403, error: { errorCode: 'PERFIL_SEM_PERMISSAO' } })));
    abrir();

    botao('Exportar CSV')!.click();
    fixture.detectChanges();

    expect(raiz.querySelector('.aud-erro-exportacao')!.textContent).toContain('Seu perfil não tem permissão');
  });

  it('destruir a tela cancela a consulta e a espera da busca', async () => {
    const lista = new Subject<EventosResposta>();
    api.listar.mockReturnValue(lista);
    abrir();
    const busca = raiz.querySelector<HTMLInputElement>('.search input')!;
    busca.value = 'x';
    busca.dispatchEvent(new Event('input'));

    fixture.destroy();

    expect(lista.observed).toBe(false);
    api.listar.mockClear();
    await vi.advanceTimersByTimeAsync(5000);
    expect(api.listar).not.toHaveBeenCalled();
  });

  it('acessos: sem coluna de processo nem filtro de sigilo, com os indicadores de acessos', () => {
    api.listar.mockReturnValue(
      of(resposta([evento({ id: 9, tipo: 'LOGIN', rotulo: 'Acesso', numeroProcesso: null, arquivo: null })], {
        indicadores: { entradas: 5, alteracoes: 1, usuarios: 2 },
      })),
    );
    abrir('ADMIN', 'acessos');

    expect(api.listar).toHaveBeenCalledWith('acessos', expect.anything());
    expect(raiz.querySelector('.aud-somente-sigilosos')).toBeNull();
    expect(texto()).not.toContain('Processo / requerimento');
    expect(raiz.querySelector('[data-ind="entradas"] b')!.textContent).toBe('5');
    expect(raiz.querySelector('[data-ind="alteracoes"] b')!.textContent).toBe('1');
  });
});
