package com.nps.dto.request

data class DepositRequest(
    val amount: Int,
//    @JsonProperty("store_requested_id")
    val storeRequestedId: String,
    val statusReason: String?
)
