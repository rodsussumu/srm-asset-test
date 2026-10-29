package com.srm.credit.dto;

import java.util.List;

public record SessionResponse(String username, List<String> roles) {}
