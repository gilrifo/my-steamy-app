import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonicModule } from '@ionic/angular';
import { FavoritePageRoutingModule } from './favorite-routing.module';
import { FavoritePage } from './favorite.page';
import { SharedModule } from '../../shared/shared-module';

@NgModule({
  imports: [
    CommonModule,
    IonicModule,
    FavoritePageRoutingModule,
    SharedModule
  ],
  declarations: [FavoritePage]
})
export class FavoritePageModule {}