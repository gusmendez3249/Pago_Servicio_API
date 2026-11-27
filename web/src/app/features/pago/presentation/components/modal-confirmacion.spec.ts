import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ModalConfirmacion } from './modal-confirmacion';

@Component({
  imports: [ModalConfirmacion],
  template: `<app-modal-confirmacion [total]="250" [procesando]="procesando()" [aviso]="aviso()" (confirmar)="pines.push($event)" (cancelar)="cancelados = cancelados + 1" />`,
})
class Anfitrion {
  procesando = signal(false);
  aviso = signal<string | null>(null);
  pines: string[] = [];
  cancelados = 0;
}

describe('ModalConfirmacion', () => {
  function crear() {
    const f = TestBed.createComponent(Anfitrion);
    f.detectChanges();
    const el: HTMLElement = f.nativeElement;
    return { f, el, campo: el.querySelector('input') as HTMLInputElement, boton: () => el.querySelector('button[type=submit]') as HTMLButtonElement };
  }

  it('es un diálogo modal accesible con campo PIN numérico y oculto', () => {
    const { el, campo } = crear();
    const dialogo = el.querySelector('[role=dialog]');
    expect(dialogo?.getAttribute('aria-modal')).toBe('true');
    expect(campo.type).toBe('password');
    expect(campo.getAttribute('inputmode')).toBe('numeric');
    expect(campo.getAttribute('maxlength')).toBe('4');
    expect(campo.getAttribute('autocomplete')).toBe('off');
    expect(el.textContent).toContain('$250.00');
  });

  it('habilita Confirmar solo con 4 dígitos y borra el PIN al enviar', () => {
    const { f, campo, boton } = crear();
    campo.value = '12a';
    campo.dispatchEvent(new Event('input'));
    f.detectChanges();
    expect(campo.value).toBe('12');
    expect(boton().disabled).toBe(true);
    campo.value = '1234';
    campo.dispatchEvent(new Event('input'));
    f.detectChanges();
    expect(boton().disabled).toBe(false);
    boton().click();
    f.detectChanges();
    expect(f.componentInstance.pines).toEqual(['1234']);
    expect(campo.value).toBe('');
  });

  it('Escape cancela salvo mientras procesa', () => {
    const { f, el } = crear();
    el.querySelector('app-modal-confirmacion')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(f.componentInstance.cancelados).toBe(1);
    f.componentInstance.procesando.set(true);
    f.detectChanges();
    el.querySelector('app-modal-confirmacion')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(f.componentInstance.cancelados).toBe(1);
  });

  it('muestra el aviso de PIN incorrecto', () => {
    const { f, el } = crear();
    f.componentInstance.aviso.set('PIN incorrecto. Te quedan 2 intentos.');
    f.detectChanges();
    expect(el.textContent).toContain('PIN incorrecto. Te quedan 2 intentos.');
  });
});
