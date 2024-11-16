package com.nps.exception

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.nps.client.ClientException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalErrorAdvice {

    @ExceptionHandler(ClientException::class)
    fun handleClientException(ex: ClientException) = ErrorResponse(
        ex.type.name.lowercase(),
        ex.error.error,
        ex.timeStamp.toString()
    ).toResponseEntity(HttpStatus.BAD_REQUEST)
}

@JsonPropertyOrder("type", "error", "timeStamp")
data class ErrorResponse (
    val type: String,
    val error: String,
    val timeStamp: String ) {
    fun toResponseEntity(status: HttpStatus): ResponseEntity<ErrorResponse> {
        return ResponseEntity(this, status)
    }
}