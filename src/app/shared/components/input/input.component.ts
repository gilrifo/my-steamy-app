import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-input',
  templateUrl: './input.component.html',
  styleUrls: ['./input.component.scss'],
  standalone: false
})
export class InputComponent {
  @Input() placeholder: string = 'Buscar...';
  @Output() searchChange = new EventEmitter<string>();
  
  private searchSubject = new Subject<string>();

  constructor() {
    this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged()
    ).subscribe(value => {
      this.searchChange.emit(value);
    });
  }

  onInput(event: any): void {
    const value = event.target.value;
    this.searchSubject.next(value);
  }
}