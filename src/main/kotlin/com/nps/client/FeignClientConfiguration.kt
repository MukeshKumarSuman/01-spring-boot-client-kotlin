package com.nps.client

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.util.EnumResolver
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import feign.RequestInterceptor
import feign.Response
import feign.codec.Decoder
import feign.codec.ErrorDecoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder
import org.springframework.cloud.openfeign.support.SpringDecoder
import org.springframework.cloud.openfeign.support.SpringEncoder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.reflect.KClass

class FeignClientConfiguration(
    val props: NpsFeignConfiguration,
    val errorHandler: ErrorHandler
) {
    private val bearerToken =  "YTVlZmQ0YWEtNjgwNC00MzA5LWExYTMtZGE3ODFiZTlmYjc0"// Base64 encode value of:- "a5efd4aa-6804-4309-a1a3-da781be9fb74"
    private val objectMapper = createObjectMapper()

    private fun createObjectMapper(): ObjectMapper {
        val jsonConfig = Jackson2ObjectMapperBuilder.json()
            .propertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .serializerByType(Enum::class.java, LowercaseEnumSerializer())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .deserializerByType(Enum::class.java, LowercaseEnumDeserializerFactory())

        return jsonConfig.build()
    }

    companion object {
        private val OBJECT_MAPPER = jacksonObjectMapper()
            .apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(SerializationFeature.INDENT_OUTPUT, true)
                registerModule(JavaTimeModule())
            }
    }

    @Bean
    fun authInterceptor() =
        RequestInterceptor {it.header(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")}

    @Bean
    fun feignDecoder() = ResponseEntityDecoder(SpringDecoder({messageConverters()}, customizers()))

    private fun messageConverters() = HttpMessageConverters(MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
    private fun customizers() = DoNothingCustomizersProvider()

    @Bean
    fun encoder() = SpringEncoder(objectFactory())

    private fun objectFactory() = ObjectFactory {
        HttpMessageConverters(messageConverter())
    }
    private fun messageConverter() = MappingJackson2HttpMessageConverter(objectMapper)

    @Bean
    fun errorDecoder() = ErrorHandlingDecoder(props.client, errorHandler, feignDecoder())

}

class DoNothingCustomizersProvider: ObjectProvider<HttpMessageConverterCustomizer> {
    override fun getObject(vararg args: Any?): HttpMessageConverterCustomizer = TODO()
    override fun getObject(): HttpMessageConverterCustomizer = TODO()
    override fun getIfAvailable(): HttpMessageConverterCustomizer? = TODO()
    override fun getIfUnique(): HttpMessageConverterCustomizer? = TODO()
    override fun forEach(action: Consumer<in HttpMessageConverterCustomizer>?) {// do nothing}
    }
}

class LowercaseEnumSerializer: StdSerializer<Enum<*>>(Enum::class.java) {
    override fun serialize(value: Enum<*>, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(value.name.lowercase(Locale.getDefault()))
    }

}

class LowercaseEnumDeserializerFactory: StdDeserializer<Enum<*>>(Enum::class.java), ContextualDeserializer {

    companion object {
        private val RESOLVER = ConcurrentHashMap<JavaType, EnumResolver>()
    }

    override fun deserialize(p0: JsonParser, p1: DeserializationContext): Enum<*>? {
        return null
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty): JsonDeserializer<*> {
        val type = property.type
        return when {
            type.isMapLikeType -> {
                // At some point handle map may be?
                this
            }
            type.isCollectionLikeType -> {
                // jackson will handle actual collection
                val enumType = type.containedType(0)
                LowercaseEnumDeserializer(enumType, createResolver(enumType, context))
            }
            type.isEnumImplType -> {
                LowercaseEnumDeserializer(type, createResolver(type, context))
            }
            else -> {
                this
            }
        }
    }

    private fun createResolver(type: JavaType, context: DeserializationContext): EnumResolver {
        return RESOLVER.computeIfAbsent(type) { EnumResolver.constructFor(context.config, it.rawClass) }
    }
}

class LowercaseEnumDeserializer (
    private val type: JavaType,
    private val resolver: EnumResolver ): StdDeserializer<Enum<*>>(Enum::class.java) {
    override fun deserialize(parser: JsonParser, ctx: DeserializationContext): Enum<*>? {
        val value = parser.valueAsString
        if (value.isNullOrBlank()) {
            return null
        }

        // Only accept lower value
        if (value.lowercase(Locale.getDefault()) != value) {
            if(resolver.defaultValue != null) {
                return resolver.defaultValue
            }
            ctx.handleWeirdStringValue(type.rawClass, value, "invalid")
        }

        var result = resolver.findEnum(value.uppercase(Locale.getDefault()))
        if (result == null) {
            result = resolver.findEnum(value)
        }
        if (result == null) {
            if(resolver.defaultValue != null) {
                return resolver.defaultValue
            }
            ctx.handleWeirdStringValue(type.rawClass, value, "invalid")
        }
        return result
    }
}

interface ErrorHandler {
    fun handlerError(
        logger: Logger,
        methodKey: String,
        status: HttpStatus,
        reader: ErrorHandlingDecoder.ResponseReader
    ): Exception?
}

class ErrorHandlingDecoder (
    client: KClass<*>,
    private val handler: ErrorHandler,
    private val decoder: Decoder ): ErrorDecoder {

    private val logger = LoggerFactory.getLogger(client.java)

    override fun decode(methodKey: String, response: Response): Exception {
        val status = HttpStatus.valueOf(response.status())
        logger.error("Rest call fail on method: $methodKey with status: $status")
        logger.info("Request: ${response.request().requestTemplate().toString()}")
        return handler.handlerError(logger, methodKey, status, ResponseReader(decoder, response)) ?: SystemException()
    }

    inner class ResponseReader(val decoder: Decoder, val response: Response) {
        inline fun <reified T> read(): T = decoder.decode(response, T::class.java) as T
    }

}

class SystemException: Exception()