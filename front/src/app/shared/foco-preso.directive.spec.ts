import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { FocoPresoDirective } from './foco-preso.directive';

@Component({
  imports: [FocoPresoDirective],
  template: `
    <button type="button" id="abrir" (click)="aberto.set(true)">Abrir</button>
    @if (aberto()) {
      <section appFocoPreso role="dialog" aria-label="Janela de teste" id="janela">
        <button type="button" id="primeiro">Primeiro</button>
        <button type="button" id="desligado" disabled>Desligado</button>
        <button type="button" id="ultimo" (click)="aberto.set(false)">Fechar</button>
      </section>
    }
  `,
})
class AnfitriaoComponent {
  readonly aberto = signal(false);
}

function tab(alvo: Element, shiftKey = false): void {
  alvo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true }));
}

async function abrir() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  document.body.appendChild(fixture.nativeElement);
  await fixture.whenStable();
  const raiz: HTMLElement = fixture.nativeElement;
  const botao = raiz.querySelector<HTMLButtonElement>('#abrir')!;
  botao.focus();
  botao.click();
  await fixture.whenStable();
  return { fixture, raiz, botao };
}

describe('FocoPresoDirective', () => {
  it('ao abrir, o foco vai para o diálogo, que fica focável só por programa', async () => {
    const { raiz } = await abrir();
    const janela = raiz.querySelector<HTMLElement>('#janela')!;
    expect(janela.getAttribute('tabindex')).toBe('-1');
    expect(document.activeElement).toBe(janela);
  });

  it('Tab circula só dentro do diálogo, pulando o desabilitado', async () => {
    const { raiz } = await abrir();
    const janela = raiz.querySelector<HTMLElement>('#janela')!;
    tab(janela);
    expect(document.activeElement?.id).toBe('primeiro');
    tab(document.activeElement!);
    expect(document.activeElement?.id).toBe('ultimo');
    tab(document.activeElement!);
    expect(document.activeElement?.id).toBe('primeiro');
  });

  it('Shift+Tab no primeiro vai para o último', async () => {
    const { raiz } = await abrir();
    raiz.querySelector<HTMLElement>('#primeiro')!.focus();
    tab(document.activeElement!, true);
    expect(document.activeElement?.id).toBe('ultimo');
  });

  it('ao fechar, o foco volta a quem abriu', async () => {
    const { fixture, raiz, botao } = await abrir();
    raiz.querySelector<HTMLButtonElement>('#ultimo')!.click();
    await fixture.whenStable();
    await Promise.resolve();
    expect(raiz.querySelector('#janela')).toBeNull();
    expect(document.activeElement).toBe(botao);
  });
});
