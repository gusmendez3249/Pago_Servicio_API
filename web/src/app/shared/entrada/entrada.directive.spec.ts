import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Entrada } from './entrada.directive';

@Component({
  imports: [Entrada, ReactiveFormsModule],
  template: `
    <input id="curp" appEntrada="curp" [formControl]="curp" />
    <input id="monto" appEntrada="importe" [enteros]="5" [valorMaximo]="50000" [formControl]="monto" />
    <input id="tel" appEntrada="digitos" maxlength="10" [formControl]="tel" />
  `,
})
class Anfitrion {
  readonly curp = new FormControl('');
  readonly monto = new FormControl('');
  readonly tel = new FormControl('');
}

function pegar(campo: HTMLInputElement, texto: string): void {
  campo.focus();
  campo.setSelectionRange(campo.value.length, campo.value.length);
  // jsdom no tiene DataTransfer: el texto pegado viaja en `data`, que la directiva lee igual
  const e = new InputEvent('beforeinput', { inputType: 'insertFromPaste', data: texto, bubbles: true, cancelable: true });
  if (campo.dispatchEvent(e)) {
    // El navegador insertaría el texto tal cual: se simula y se avisa como lo haría él
    campo.value += texto;
    campo.dispatchEvent(new Event('input', { bubbles: true }));
  }
}

describe('Entrada (directiva)', () => {
  let anfitrion: Anfitrion;
  let raiz: HTMLElement;

  beforeEach(() => {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    anfitrion = fixture.componentInstance;
    raiz = fixture.nativeElement as HTMLElement;
  });

  const campo = (id: string) => raiz.querySelector<HTMLInputElement>(`#${id}`)!;

  it('pegar una CURP con guiones y minúsculas la deja limpia y completa en el formulario', () => {
    pegar(campo('curp'), 'garl-950312-mgtjpd01-sobra');
    expect(campo('curp').value).toBe('GARL950312MGTJPD01');
    expect(anfitrion.curp.value).toBe('GARL950312MGTJPD01');
    expect(campo('curp').maxLength).toBe(18);
  });

  it('el monto no admite más de 2 decimales ni pasar del tope', () => {
    pegar(campo('monto'), '500.999');
    expect(anfitrion.monto.value).toBe('500.99');
    pegar(campo('monto'), '9');
    expect(anfitrion.monto.value).toBe('500.99');
  });

  it('respeta el maxlength declarado y quita lo que no son dígitos', () => {
    pegar(campo('tel'), '(418) 123-4567 ext 9');
    expect(anfitrion.tel.value).toBe('4181234567');
  });

  it('un valor autocompletado con caracteres prohibidos se limpia antes de llegar al formulario', () => {
    const c = campo('tel');
    c.value = 'ab12cd';
    c.dispatchEvent(new Event('input', { bubbles: true }));
    expect(anfitrion.tel.value).toBe('12');
  });
});
