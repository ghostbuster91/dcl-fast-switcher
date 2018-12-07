package io.ghostbuster91.docker.compose.logger.docker

import de.gesellix.docker.compose.ComposeFileReader
import io.ghostbuster91.docker.compose.logger.StreamDefinition
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException

fun streamFromDockerCompose(streamDefinition: StreamDefinition): Flowable<String> {
    val command = DockerComposeCommandBuilder(
            services = streamDefinition.services.toList(),
            showTimestamps = streamDefinition.showTimeStamps)
            .build()
    return createProcess(command)
            .mergeWith(Flowable.never())
            .subscribeOn(Schedulers.io())
}

fun readServicesFromDockerConfig(args: Array<String>): List<String> {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, args.getOrElse(0) { "docker-compose.yml" }))
    val config = ComposeFileReader().loadYaml(inputStream)
    return config["services"]!!.keys.toList()
}

private fun createProcess(command: List<String>): Flowable<String> {
    return Flowable
            .using(
                    {
                        ProcessBuilder()
                                .directory(File(System.getProperty("user.dir")))
                                .redirectErrorStream(true)
                                .command(command)
                                .start()
                    },
                    { process -> process.inputStream.bufferedReader().toFlowable() },
                    { process -> process.destroy() }
            )
            .onErrorResumeNext { t: Throwable -> if (t is UncheckedIOException) Flowable.empty<String>() else throw t }

}

private fun BufferedReader.toFlowable(): Flowable<String> {
    return Flowable.fromIterable(object : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return lines().iterator()
        }
    })
}

