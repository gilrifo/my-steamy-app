import { Component, OnInit, ViewChild, OnDestroy } from '@angular/core';
import { IonModal, ToastController } from '@ionic/angular';
import { Browser } from '@capacitor/browser';
import { Preferences } from '@capacitor/preferences';
import { GameProvider, Deal, Store, GameInfo, GameDetails } from '../../shared/services/game-provider';
import { Subject, Subscription } from 'rxjs';
import { of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';

@Component({
  selector: 'app-home',
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
  standalone: false
})
export class HomePage implements OnInit, OnDestroy {
  @ViewChild('detailsModal') detailsModal!: IonModal;

  topDeals: Deal[] = [];
  searchResults: Deal[] = [];
  stores: Store[] = [];
  favorites: Deal[] = [];
  selectedGame: GameInfo | null = null;
  selectedDeal: Deal | null = null;
  
  isLoading: boolean = false;
  isSearching: boolean = false;
  searchQuery: string = '';
  
  private searchSubject = new Subject<string>();
  private searchSubscription!: Subscription;

    Math = Math;
  parseFloat = parseFloat;
  parseInt = parseInt;
  constructor(
    private gameProvider: GameProvider,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
    this.loadFavorites();
    this.setupSearch();
  }

  ngOnDestroy(): void {
    if (this.searchSubscription) {
      this.searchSubscription.unsubscribe();
    }
  }

  private setupSearch(): void {
    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(query => {
        const trimmedQuery = query.trim();
        
        if (!trimmedQuery) {
          this.isSearching = false;
          this.searchResults = [];
          this.searchQuery = '';
          return of([]);
        }
        
        this.isSearching = true;
        this.searchQuery = trimmedQuery;
        
        return this.gameProvider.searchDeals(trimmedQuery).pipe(
          catchError(error => {
            console.error('Error en búsqueda:', error);
            this.isSearching = false;
            return of([]);
          })
        );
      })
    ).subscribe({
      next: (results) => {
        this.searchResults = results;
        this.isSearching = false;
      },
      error: (err) => {
        console.error('Error en subscription:', err);
        this.isSearching = false;
      }
    });
  }

  onSearchChange(value: string): void {
    this.searchSubject.next(value);
  }

  loadInitialData(): void {
    this.isLoading = true;
    this.gameProvider.getStoresWithDeals().subscribe({
      next: (data) => {
        this.stores = data.stores.filter(s => s.isActive === 1);
        this.topDeals = data.deals;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error cargando datos:', err);
        this.isLoading = false;
      }
    });
  }

  async loadFavorites(): Promise<void> {
    const { value } = await Preferences.get({ key: 'favoriteGames' });
    if (value) {
      this.favorites = JSON.parse(value);
    }
  }

  getStore(storeID: string): Store | undefined {
    return this.stores.find(s => s.storeID === storeID);
  }

  getStoreById(storeID: string): Store | undefined {
    return this.stores.find(s => s.storeID === storeID);
  }

  async toggleFavorite(deal: Deal): Promise<void> {
    const index = this.favorites.findIndex(f => f.dealID === deal.dealID);
    
    if (index >= 0) {
      this.favorites.splice(index, 1);
      await this.saveFavorites();
      this.showToast(`"${deal.title}" eliminado de favoritos`);
    } else {
      this.favorites.push(deal);
      await this.saveFavorites();
      this.showToast(`"${deal.title}" agregado a favoritos`);
    }
  }

  private async saveFavorites(): Promise<void> {
    await Preferences.set({
      key: 'favoriteGames',
      value: JSON.stringify(this.favorites)
    });
  }

  isFavorite(deal: Deal): boolean {
    return this.favorites.some(f => f.dealID === deal.dealID);
  }

  async openDeal(deal: Deal): Promise<void> {
    const url = this.gameProvider.getRedirectUrl(deal.dealID);
    await Browser.open({ url });
  }

  async openDealById(dealID: string): Promise<void> {
    const url = this.gameProvider.getRedirectUrl(dealID);
    await Browser.open({ url });
  }

  async openGameDetails(deal: Deal): Promise<void> {
    this.selectedDeal = deal;
    this.gameProvider.getGameDetails(deal.gameID).subscribe({
      next: (info) => {
        console.log('GameInfo recibido:', info);  // Debug
        this.selectedGame = info;
        this.detailsModal.present();
      },
      error: (err) => {
        console.error('Error cargando detalles:', err);
      }
    });
  }

  closeModal(): void {
    this.detailsModal.dismiss();
    this.selectedGame = null;
    this.selectedDeal = null;
  }

  async viewDealFromModal(): Promise<void> {
    if (this.selectedDeal) {
      await this.openDeal(this.selectedDeal);
    }
  }

  formatPrice(price: string | number | null | undefined): string {
    if (!price) return '$0.00';
    const num = typeof price === 'string' ? parseFloat(price) : price;
    return '$' + num.toFixed(2);
  }

  getSavingsPercent(savings: string | undefined): number {
    if (!savings) return 0;
    return Math.round(parseFloat(savings));
  }

  formatDate(timestamp: number | string | null | undefined): string {
    if (!timestamp) return 'N/A';
    const ts = typeof timestamp === 'string' ? parseInt(timestamp) : timestamp;
    const date = new Date(ts * 1000);
    return date.toLocaleDateString('es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  // ✅ Encontrar el deal actual dentro de selectedGame.deals
  getCurrentGameDeal(): GameDetails | undefined {
    if (!this.selectedGame || !this.selectedDeal) return undefined;
    return this.selectedGame.deals.find(d => d.dealID === this.selectedDeal?.dealID);
  }

  // ✅ Encontrar deals más baratos (excluyendo el actual)
  getCheaperDeals(): GameDetails[] {
    if (!this.selectedGame || !this.selectedDeal) return [];
    const currentPrice = parseFloat(this.selectedDeal.salePrice);
    return this.selectedGame.deals.filter(d => parseFloat(d.price) < currentPrice);
  }

  private async showToast(message: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      duration: 2000,
      position: 'bottom',
      color: 'dark'
    });
    await toast.present();
  }
}