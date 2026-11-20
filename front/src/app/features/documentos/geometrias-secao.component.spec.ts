import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { DocumentosApiService } from './documentos-api.service';
import { GeometriaItem } from './documentos.models';
import { formatarTamanho } from '../../shared/formato';
import { GeometriasSecaoComponent } from './geometrias-secao.component';

function geometria(sobre: Partial<GeometriaItem> = {}): GeometriaItem {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id: '900',
    idRequerimento: 101,
    titulo: 'Polígono da área',
    nomeArquivo: 'area.kml',
    origemDescricao: 'Documento do processo',
    data: '2025-04-02T09:00:00',
    situacao: 'VIGENTE',
    sigiloso: false,
    extensao: 'kml',
    tipoArquivo: 'GEOMETRIA',
    tamanho: null,
    ...sobre,
  };
}

function zip(nome = 'geometrias-processo-1234-2025.zip'): HttpResponse<Blob> {
  return new HttpResponse({
    body: new Blob(['PK'], { type: 'application/zip' }),
    headers: new HttpHeaders({ 'Content-Disposition': `attachment; filename="${nome}"` }),
    status: 200,
  });
}

function falha(status: number, corpo: object): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: corpo });
}

describe('GeometriasSecaoComponent', () => {
  let baixarGeometrias: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<GeometriasSecaoComponent>;
  let raiz: HTMLElement;

  function abrir(geometrias: GeometriaItem[], omitidas = 0): void {
    TestBed.configureTestingModule({
      imports: [GeometriasSecaoComponent],
      providers: [{ provide: DocumentosApiService, useValue: { baixarGeometrias } }],
    });
    fixture = TestBed.createComponent(GeometriasSecaoComponent);
    fixture.componentRef.setInput('idRequerimento', 101);
    fixture.componentRef.setInput('geometrias', geometrias);
    fixture.componentRef.setInput('omitidas', omitidas);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const botao = () => raiz.querySelector<HTMLButtonElement>('.baixar-geometrias');
  const clicar = (seletor: string) => {
    raiz.querySelector<HTMLButtonElement>(seletor)!.click();
    fixture.detectChanges();
  };

  beforeEach(() => {
    baixarGeometrias = vi.fn(() => of(zip()));
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/zip-1');
    URL.revokeObjectURL = vi.fn();
  });

  it('lista nome, arquivo, origem e data de cada geometria', () => {
    abrir([geometria(), geometria({ id: '901', titulo: 'Reserva legal', nomeArquivo: 'reserva.shp', extensao: 'shp', data: '2025-04-03T09:00:00' })]);

    expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(texto()).toContain('Polígono da área');
    expect(texto()).toContain('area.kml');
    expect(texto()).toContain('Documento do processo');
    expect(texto()).toContain('02/04/2025');
    expect(texto()).toContain('Reserva legal');
    expect(texto()).toContain('Não entram no dossiê');
    expect(raiz.querySelector('input[type=checkbox]')).toBeNull();
  });

  it('baixa o ZIP por Blob com o nome do Content-Disposition', () => {
    abrir([geometria()]);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    clicar('.baixar-geometrias');

    expect(baixarGeometrias).toHaveBeenCalledExactlyOnceWith(101);
    expect((URL.createObjectURL as ReturnType<typeof vi.fn>).mock.calls[0][0]).toBeInstanceOf(Blob);
    expect(clique).toHaveBeenCalledOnce();
    expect(texto()).toContain('Download iniciado: geometrias-processo-1234-2025.zip');
    clique.mockRestore();
  });

  it('mostra carregando e não dispara dois downloads', () => {
    const pendente = new Subject<HttpResponse<Blob>>();
    baixarGeometrias.mockReturnValue(pendente);
    abrir([geometria()]);

    clicar('.baixar-geometrias');
    expect(botao()!.disabled).toBe(true);
    expect(botao()!.textContent).toContain('Preparando o ZIP');
    expect(texto()).toContain('Obtendo os arquivos e montando o ZIP');
    botao()!.click();
    expect(baixarGeometrias).toHaveBeenCalledOnce();
  });

  it('GEOMETRIAS_INCOMPLETAS: lista os arquivos com problema e oferece nova tentativa', () => {
    baixarGeometrias.mockReturnValueOnce(
      throwError(() =>
        falha(502, {
          status: 502,
          errorCode: 'GEOMETRIAS_INCOMPLETAS',
          detail: 'detalhe técnico que nunca deve aparecer',
          arquivos: [
            { nome: 'area-2.kml', motivo: 'arquivo não encontrado no repositório' },
            { nome: 'reserva.shp', motivo: 'tempo limite excedido' },
          ],
        }),
      ),
    );
    abrir([geometria()]);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    clicar('.baixar-geometrias');

    expect(texto()).toContain('Nenhum arquivo foi baixado: algumas geometrias não puderam ser obtidas.');
    expect(texto()).toContain('area-2.kml: arquivo não encontrado no repositório');
    expect(texto()).toContain('reserva.shp: tempo limite excedido');
    expect(texto()).not.toContain('detalhe técnico');
    expect(URL.createObjectURL).not.toHaveBeenCalled();

    clicar('.tentar-geometrias');

    expect(baixarGeometrias).toHaveBeenCalledTimes(2);
    expect(raiz.querySelector('.geometrias-erro')).toBeNull();
    expect(texto()).toContain('Download iniciado');
    clique.mockRestore();
  });

  it('504 também é GEOMETRIAS_INCOMPLETAS, com a lista', () => {
    baixarGeometrias.mockReturnValue(
      throwError(() => falha(504, { status: 504, errorCode: 'GEOMETRIAS_INCOMPLETAS', arquivos: [{ nome: 'a.kmz', motivo: 'tempo limite excedido' }] })),
    );
    abrir([geometria()]);
    clicar('.baixar-geometrias');
    expect(raiz.querySelectorAll('.geometrias-falhas li')).toHaveLength(1);
  });

  it('413 tem texto próprio, sem lista', () => {
    baixarGeometrias.mockReturnValue(throwError(() => falha(413, { status: 413, errorCode: 'GEOMETRIAS_GRANDES_DEMAIS' })));
    abrir([geometria()]);
    clicar('.baixar-geometrias');
    expect(texto()).toContain('passam do limite de tamanho ou de quantidade');
    expect(raiz.querySelector('.geometrias-falhas')).toBeNull();
    expect(raiz.querySelector('.tentar-geometrias')).not.toBeNull();
  });

  it('credencial recusada usa a mensagem comum, sem citar valor algum', () => {
    baixarGeometrias.mockReturnValue(throwError(() => falha(502, { status: 502, errorCode: 'CREDENCIAL_ARQUIVOS_RECUSADA' })));
    abrir([geometria()]);
    clicar('.baixar-geometrias');
    expect(texto()).toContain('A credencial de acesso aos arquivos foi recusada');
  });

  it('Operador: avisa quantas sigilosas foram omitidas, sem nomes', () => {
    abrir([geometria()], 2);
    const aviso = raiz.querySelector('.geometrias-omitidas')!;
    expect(aviso.textContent!.replace(/\s+/g, ' ')).toContain('2 geometria(s) sigilosa(s) não são exibidas para o seu perfil.');
    expect(raiz.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('só sigilosas omitidas: aviso sem tabela e sem botão de download', () => {
    abrir([], 1);
    expect(raiz.querySelector('.geometrias-omitidas')).not.toBeNull();
    expect(raiz.querySelector('table')).toBeNull();
    expect(botao()).toBeNull();
  });

  it('Gestor: a geometria sigilosa aparece marcada', () => {
    abrir([geometria({ sigiloso: true })]);
    expect(raiz.querySelector('tbody .lock')!.textContent).toContain('Sigilosa');
    expect(raiz.querySelector('.geometrias-omitidas')).toBeNull();
  });

  it('substituída e indisponível aparecem fora do ZIP; sem vigente, não há botão', () => {
    abrir([geometria({ situacao: 'SUBSTITUIDO' }), geometria({ id: '902', situacao: 'INDISPONIVEL' })]);
    expect(texto()).toContain('versão substituída, fora do ZIP');
    expect(texto()).toContain('arquivo indisponível, fora do ZIP');
    expect(botao()).toBeNull();
  });

  it('formata o tamanho quando conhecido', () => {
    expect(formatarTamanho(null)).toBe('');
    expect(formatarTamanho(2048)).toBe('2 KB');
    expect(formatarTamanho(1.5 * 1024 * 1024)).toBe('1,5 MB');
    abrir([geometria({ tamanho: 3 * 1024 * 1024 })]);
    expect(texto()).toContain('Polígono da área · 3 MB');
  });
});
