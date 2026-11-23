import { TestBed } from '@angular/core/testing';

import { MarcaComponent } from './marca.component';

/** R2, bloco 5: componente da marca nos dois modos (com nome visível e só escudo). */
describe('MarcaComponent', () => {
  function criar(mostrarNome: boolean, tamanho?: number) {
    const fixture = TestBed.createComponent(MarcaComponent);
    fixture.componentRef.setInput('mostrarNome', mostrarNome);
    if (tamanho !== undefined) fixture.componentRef.setInput('tamanho', tamanho);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('mostra o nome SIGRASTRO com SIG em peso 500 e RASTRO em peso 700', () => {
    const el = criar(true);
    const wm = el.querySelector('.wm');
    expect(wm?.textContent).toBe('SIGRASTRO');
    expect(wm?.querySelector('.l')?.textContent).toBe('SIG');
    expect(el.querySelector('[role="img"]')).toBeNull();
  });

  it('mostra o escudo decorativo com o tamanho informado e sem recorte', () => {
    const el = criar(true, 48);
    const img = el.querySelector('img');
    expect(img?.getAttribute('src')).toBe('assets/img/sigrastro-escudo.svg');
    expect(img?.getAttribute('alt')).toBe('');
    expect(img?.style.width).toBe('48px');
    expect(img?.style.height).toBe('48px');
  });

  it('usa 36 px como tamanho padrão do escudo', () => {
    const img = criar(true).querySelector('img');
    expect(img?.style.width).toBe('36px');
  });

  it('sem nome visível, o conjunto tem o nome acessível SIGRASTRO e não mostra o texto', () => {
    const el = criar(false);
    const conjunto = el.querySelector('[role="img"]');
    expect(conjunto?.getAttribute('aria-label')).toBe('SIGRASTRO');
    expect(el.querySelector('.wm')).toBeNull();
    expect(el.querySelector('img')?.getAttribute('src')).toBe('assets/img/sigrastro-escudo.svg');
  });
});
