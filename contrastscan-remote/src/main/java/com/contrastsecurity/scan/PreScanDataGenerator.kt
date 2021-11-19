package com.contrastsecurity.scan

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.Writer
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

sealed class PreScanDataGenerator {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)

        fun generate(sourceDir: Path, outPath: Path) {
            logger.debug("generating prescan data: source dir [$sourceDir],  output path [$outPath]")
            val mapper = ObjectMapper()
            val writer: Writer = FileWriter(outPath.toFile())
            mapper.writerWithDefaultPrettyPrinter().createGenerator(writer).use { generator ->
                // Start prescan json document
                val s = ScanInputMetadataSchema100()
                    .withVersion(ScanInputMetadataSchema100.Version._1_0_0)
                    .`with$schema`(URI.create("https://tbd/scan-input-metadata-schema-1.0.0.json"))
                generator.writeStartObject()
                generator.writeStringField("version", s.version.value())
                generator.writeStringField("\$schema", s.`$schema`.toString())
                generator.writeArrayFieldStart("paths")
                // walk dir listing and stream out path data
                Files.walkFileTree(
                    sourceDir,
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val abspath = file.toAbsolutePath().normalize()
                            if (Files.isRegularFile(file)) {
                                if (filter(abspath)) {
                                    generator.writeString(abspath.toString())
                                } else {
                                    logger.debug("ignoring $file")
                                }
                            } else if (Files.isDirectory(file)
                                && containsPath(abspath, Paths.get("node_modules"))
                            ) {
                                // don't descend into a node_modules cache.
                                logger.debug("skipping dir $file")
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            return FileVisitResult.CONTINUE
                        }
                    })
                generator.writeEndArray()
                // finish json document
                generator.writeEndObject()
            }
        }

        private fun filter(path: Path): Boolean {
            return (path.toString().endsWith(".java")
                    || path.fileName.endsWith("web.xml")
                    || path.toString().endsWith(".js")
                    || path.toString().endsWith(".ts")
                    || path.toString().endsWith(".html")
                    || path.toString().endsWith(".htm"))
        }

        private fun containsPath(path: Path, partial: Path) = path.normalize().any { it == partial }

    }
}
