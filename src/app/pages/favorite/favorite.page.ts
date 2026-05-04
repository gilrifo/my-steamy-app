import { Component, OnInit } from '@angular/core';
import { Preferences } from '@capacitor/preferences';
import { Browser } from '@capacitor/browser';
import { Deal, GameProvider, Store } from '../../shared/services/game-provider';
import { ToastController } from '@ionic/angular';

@Component({
  selector: 'app-favorite',
  templateUrl: './favorite.page.html',
  styleUrls: ['./favorite.page.scss'],
  standalone: false
})
export class FavoritePage implements OnInit {
  favorites: Deal[] = [];       // ← Array de favoritos (plural)
  stores: Store[] = [];
  isLoading: boolean = true;

  constructor(
    private gameProvider: GameProvider,
    private toastCtrl: ToastController
  ) {}

  async ngOnInit(): Promise<void> {
    this.loadStores();
  }

  // Se ejecuta cada vez que entras a la pestaña
  async ionViewWillEnter(): Promise<void> {
    await this.loadFavorites();
  }

  async loadFavorites(): Promise<void> {
    const { value } = await Preferences.get({ key: 'favoriteGames' });
    if (value) {
      this.favorites = JSON.parse(value);
    }
    this.isLoading = false;
  }

  loadStores(): void {
    this.gameProvider.getStores().subscribe(stores => {
      this.stores = stores.filter(s => s.isActive === 1);
    });
  }

  getStore(storeID: string): Store | undefined {
    return this.stores.find(s => s.storeID === storeID);
  }

  async removeFavorite(deal: Deal): Promise<void> {
    const index = this.favorites.findIndex(f => f.dealID === deal.dealID);
    if (index >= 0) {
      this.favorites.splice(index, 1);
      await Preferences.set({
        key: 'favoriteGames',
        value: JSON.stringify(this.favorites)
      });
      this.showToast(`"${deal.title}" eliminado`);
    }
  }

  async openDeal(deal: Deal): Promise<void> {
    const url = this.gameProvider.getRedirectUrl(deal.dealID);
    await Browser.open({ url });
  }

  private async showToast(message: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      duration: 2000,
      color: 'dark'
    });
    await toast.present();
  }
}