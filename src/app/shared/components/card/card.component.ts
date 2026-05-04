import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Deal, Store } from '../../services/game-provider';

@Component({
  selector: 'app-card',
  templateUrl: './card.component.html',
  styleUrls: ['./card.component.scss'],
  standalone: false
})
export class CardComponent {
  @Input() deal!: Deal;
  @Input() store!: Store | undefined;
  @Input() isFavorite: boolean = false;
  @Output() favoriteToggle = new EventEmitter<Deal>();
  @Output() cardClick = new EventEmitter<Deal>();

  get savingsPercent(): number {
    return Math.round(parseFloat(this.deal.savings || '0'));
  }

  onFavorite(event: Event): void {
    event.stopPropagation();
    this.favoriteToggle.emit(this.deal);
  }

  onCardClick(): void {
    this.cardClick.emit(this.deal);
  }
}