package io.ghostbuster91.docker.compose.logger.keyboard

import io.ghostbuster91.docker.compose.logger.Service

class ServiceMappingImpl(private val services: List<Service>) : ServiceMapping {
    private val characterToNumberMapping = listOf(
            "q",
            "a",
            "z",
            "w",
            "s",
            "x",
            "e",
            "d",
            "c",
            "r",
            "f",
            "v",
            "t",
            "g",
            "b")

    override fun isServiceKey(character: String?): Boolean {
        return characterToNumberMapping.indexOf(character)
                .let { it < services.size && it >= 0 }
    }

    override fun mapToService(character: String): Service {
        require(characterToNumberMapping.indexOf(character) != -1)
        return services[characterToNumberMapping.indexOf(character)]
    }

    override fun getMapping(): List<Pair<String, Service>> {
        return characterToNumberMapping.toList()
                .take(services.size)
                .mapIndexed { index, character ->
                    character to services[index]
                }
    }
}

interface ServiceMapping {
    fun isServiceKey(character: String?): Boolean
    fun mapToService(character: String) : Service
    fun getMapping() : List<Pair<String,Service>>
}