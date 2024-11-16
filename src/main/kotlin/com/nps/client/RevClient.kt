package com.nps.client

import com.nps.dto.request.DepositRequest
import com.nps.dto.response.DepositResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "rev-client",
    url = "http://localhost:8082",
    configuration = [RevConfiguration::class]
)
interface RevClient {

    @PostMapping("/deposit")
    fun deposit(@RequestBody depositRequest: DepositRequest): DepositResponse
}