package com.nps.service

import com.nps.client.RevClient
import com.nps.dto.request.DepositRequest
import com.nps.dto.response.DepositResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PartnerService(val revClient: RevClient) {
    val logger = LoggerFactory.getLogger(PartnerService::class.java)
    fun deposit(depositRequest: DepositRequest): DepositResponse {
//        return DepositResponse(100, "accepted")
        logger.info("Request: {}", depositRequest)
        val depositResponse = revClient.deposit(depositRequest)
        logger.info("Request: {}", depositResponse)
        return depositResponse
    }
}