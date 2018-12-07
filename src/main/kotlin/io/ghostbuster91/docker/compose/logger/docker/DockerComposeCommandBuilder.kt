package io.ghostbuster91.docker.compose.logger.docker

data class DockerComposeCommandBuilder(val services: List<String>,
                                       val showTimestamps: Boolean = false,
                                       val showColors: Boolean = true,
                                       val follow: Boolean = true,
                                       val tailSize: Int = 200) {
    fun build(): List<String> {
        var command = listOf("docker-compose", "logs", "--tail=$tailSize")
        if (showTimestamps) {
            command += "--timestamps"
        }
        if (!showColors) {
            command += "--no-color"
        }
        if (follow) {
            command += "--follow"
        }
        return command + services
    }
}