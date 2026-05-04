import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonicModule } from '@ionic/angular';
import { CardComponent } from './components/card/card.component';
import { InputComponent } from './components/input/input.component';

@NgModule({
  declarations: [
    CardComponent,
    InputComponent
  ],
  imports: [
    CommonModule,
    IonicModule
  ],
  exports: [
    CardComponent,
    InputComponent
  ]
})
export class SharedModule {}