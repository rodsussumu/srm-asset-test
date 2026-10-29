package com.srm.credit.model;

import java.util.UUID;

public record StoredSettlementResponse(
    int statusCode, String body, String location, UUID settlementId, boolean replay) {}
