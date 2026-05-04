import { TestBed } from '@angular/core/testing';

import { GameProvider } from './game-provider';

describe('GameProvider', () => {
  let service: GameProvider;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(GameProvider);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
