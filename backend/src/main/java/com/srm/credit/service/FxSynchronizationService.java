package com.srm.credit.service;

import org.springframework.stereotype.Service;

@Service
public class FxSynchronizationService {
  private final FxProviderClient provider;
  private final FxRateService rates;

  public FxSynchronizationService(FxProviderClient provider, FxRateService rates) {
    this.provider = provider;
    this.rates = rates;
  }

  public FxRateService.SyncResult synchronize() {
    // No database transaction is held while waiting for the external provider.
    return rates.recordSynchronized(provider.fetch());
  }
}
