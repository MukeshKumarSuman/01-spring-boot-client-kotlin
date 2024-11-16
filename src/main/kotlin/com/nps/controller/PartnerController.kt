package com.nps.controller

import com.nps.dto.request.DepositRequest
import com.nps.dto.response.DepositResponse
import com.nps.service.PartnerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PartnerController(
    val partnerService: PartnerService
) {

    @PostMapping("/deposit")
    fun deposit(@RequestBody depositRequest: DepositRequest): ResponseEntity<DepositResponse> {
        val depositResponse = partnerService.deposit(depositRequest)
        println(depositRequest)
        return ResponseEntity(depositResponse, HttpStatus.ACCEPTED)
    }
}