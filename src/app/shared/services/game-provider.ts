import { Injectable } from '@angular/core';
import { HttpService } from '../../core/services/http';
import { Observable, forkJoin, map } from 'rxjs';


export interface Store {
  storeID: string;
  storeName: string;
  images: {
    banner: string;
    logo: string;
    icon: string;
  };
  isActive: number;
}

export interface Deal {
  internalName: string;
  title: string;
  metacriticLink: string | null;
  dealID: string;
  storeID: string;
  gameID: string;
  salePrice: string;
  normalPrice: string;
  isOnSale: string;
  savings: string;
  metacriticScore: string | null;
  steamRatingText: string | null;
  steamRatingPercent: string;
  steamRatingCount: string;
  steamAppID: string | null;
  releaseDate: number;
  lastChange: number;
  dealRating: string;
  thumb: string;
}

// ✅ Estructura REAL de la API /games
export interface GameDetails {
  storeID: string;
  dealID: string;
  price: string;
  retailPrice: string;
  savings: string;
}

export interface GameInfo {
  info: {                          // ← "info" no "gameInfo"
    title: string;
    steamAppID: string | null;
    thumb: string;
  };
  cheapestPriceEver: {
    price: string;
    date: number;
  };
  deals: GameDetails[];            // ← "deals" con precios de todas las tiendas
}

@Injectable({
  providedIn: 'root'
})
export class GameProvider {
  private readonly BASE_URL = 'https://www.cheapshark.com/api/1.0';

  constructor(private http: HttpService) {}

  getStores(): Observable<Store[]> {
    return this.http.get<Store[]>('/stores');
  }

  getTopDeals(pageSize: number = 5): Observable<Deal[]> {
    return this.http.get<Deal[]>('/deals', { pageSize });
  }

  searchDeals(title: string): Observable<Deal[]> {
    return this.http.get<Deal[]>('/deals', { title, pageSize: 20 });
  }

  getGameDetails(gameID: string): Observable<GameInfo> {
    return this.http.get<GameInfo>('/games', { id: gameID });
  }

  getStoresWithDeals(): Observable<{ stores: Store[]; deals: Deal[] }> {
    return forkJoin({
      stores: this.getStores(),
      deals: this.getTopDeals(5)
    });
  }

  getRedirectUrl(dealID: string): string {
    return `https://www.cheapshark.com/redirect?dealID=${dealID}`;
  }
}