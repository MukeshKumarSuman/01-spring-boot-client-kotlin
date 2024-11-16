package com.nps.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Import(FeignClientConfiguration::class)
class RevConfiguration {

    @Bean
    fun revFeignConfiguration() = NpsFeignConfiguration(RevClient::class)

    @Bean
    fun errorHandler() = RevErrorHandler()
}

data class NpsFeignConfiguration(
    val client: KClass<*>
)

class RevErrorHandler: ErrorHandler {
    override fun handlerError(
        logger: Logger,
        methodKey: String,
        status: HttpStatus,
        reader: ErrorHandlingDecoder.ResponseReader
    ): Exception {
        val error = reader.read<RevErrorResponse>()
        logger.error("Method $methodKey received ${error.errorConstant} -> ${error.message}")
        when(StatusReason.valueOf(error.errorConstant)) {
            StatusReason.ACCOUNT_NOT_FOUND -> throw ClientException(ErrorMapping.ACCOUNT_NOT_FOUND, ErrorType.BUSINESS)
            StatusReason.VALIDATION_INSUFFICIENT_BALANCE -> throw ClientException(ErrorMapping.VALIDATION_INSUFFICIENT_BALANCE, ErrorType.VALIDATION)
            else -> throw ClientException(ErrorMapping.API_FAILED, ErrorType.SYSTEM)
        }
    }

}

data class RevErrorResponse(
    val code: Int,
    val errorConstant: String,
    val message: String? = null
)

enum class StatusReason(val value: String) {
    ACCOUNT_NOT_FOUND("account_not_found"),
    VALIDATION_INSUFFICIENT_BALANCE("insufficient_balance"),
}
enum class ErrorMapping(val error: String, val description: String) {
    ACCOUNT_NOT_FOUND("account.not_found", "Account not found"),
    VALIDATION_INSUFFICIENT_BALANCE("insufficient.balance", "Insufficient balance"),
    API_FAILED("service.error", "Rev Service API failed"),
}

enum class ErrorType {
    BUSINESS, SYSTEM, VALIDATION
}
fun ErrorType.value(): String = this.name.lowercase()


class ClientException(errorMapping: ErrorMapping, var type: ErrorType): RuntimeException(errorMapping.description) {
    companion object {
        private const val serialVersionUID = 1L
        internal val LOG = LoggerFactory.getLogger(this::class.java)
    }

    var error = ErrorMapping.API_FAILED
    var timeStamp: LocalDateTime = LocalDateTime.now()

    init {
        this.error = errorMapping
        LOG.error("Error received from REV Server, ${error.error}: ${error.description}")
    }
}



