package io.github.ninbyo02.lami.debug

import android.app.Activity
import android.os.Bundle
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiteRtModelInspectorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread({
            try {
                val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)?.trim().orEmpty()
                val modelFile = File(modelPath)
                writeResult(append = false, line = "modelPath=$modelPath")
                writeResult(
                    append = true,
                    line = "file exists=${modelFile.exists()} canRead=${modelFile.canRead()} size=${modelFile.length()}",
                )
                require(modelPath.isNotBlank()) { "missing intent extra: $EXTRA_MODEL_PATH" }
                require(modelFile.isFile) { "model file missing: $modelPath" }
                require(modelFile.canRead()) { "model file unreadable: $modelPath" }

                val inspection = TfliteFlatBufferInspector.inspect(modelFile.readBytes())
                writeResult(append = true, line = "flatbufferOffset=${inspection.flatBufferOffset}")
                writeResult(append = true, line = "rootIdentifier=${inspection.rootIdentifier}")
                writeResult(append = true, line = "signatureCount=${inspection.signatures.size}")
                inspection.signatures.forEachIndexed { index, signature ->
                    writeResult(
                        append = true,
                        line = "signature[$index] key=${signature.key.ifBlank { "none" }} " +
                            "method=${signature.methodName.ifBlank { "none" }} " +
                            "subgraphIndex=${signature.subgraphIndex} " +
                            "inputs=${signature.inputs.joinToString("|")} " +
                            "outputs=${signature.outputs.joinToString("|")}",
                    )
                }
                writeResult(append = true, line = "subgraphCount=${inspection.subgraphs.size}")
                inspection.subgraphs.forEachIndexed { subgraphIndex, subgraph ->
                    writeResult(
                        append = true,
                        line = "subgraph[$subgraphIndex] name=${subgraph.name.ifBlank { "none" }} " +
                            "tensorCount=${subgraph.tensors.size} " +
                            "inputs=${subgraph.inputTensorNames.joinToString("|")} " +
                            "outputs=${subgraph.outputTensorNames.joinToString("|")}",
                    )
                    writeResult(
                        append = true,
                        line = "subgraph[$subgraphIndex] inputTensorNames=${subgraph.inputTensorNames.joinToString("|")}",
                    )
                    writeResult(
                        append = true,
                        line = "subgraph[$subgraphIndex] outputTensorNames=${subgraph.outputTensorNames.joinToString("|")}",
                    )
                    writeResult(
                        append = true,
                        line = "subgraph[$subgraphIndex] prefillDecodePresence=${subgraph.prefillDecodePresence()}",
                    )
                    subgraph.tensors.forEachIndexed { tensorIndex, tensor ->
                        writeResult(
                            append = true,
                            line = "tensor subgraph=$subgraphIndex index=$tensorIndex name=${tensor.name} " +
                                "isInput=${tensorIndex in subgraph.inputTensorIndexes} " +
                                "isOutput=${tensorIndex in subgraph.outputTensorIndexes}",
                        )
                    }
                }
                writeResult(append = true, line = "RESULT=SUCCESS")
            } catch (throwable: Throwable) {
                writeResult(
                    append = true,
                    line = "RESULT=FAILED class=${throwable.javaClass.name} message=${throwable.message.orEmpty()}",
                )
                writeResult(append = true, line = "STACK ${throwable.stackTraceToString()}")
            } finally {
                runOnUiThread { finish() }
            }
        }, "LiteRtModelInspector").start()
    }

    private fun writeResult(append: Boolean, line: String) {
        FileOutputStream(File(filesDir, RESULT_NAME), append).use { output ->
            output.write(line.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            output.flush()
            output.fd.sync()
        }
    }

    private companion object {
        private const val EXTRA_MODEL_PATH = "modelPath"
        private const val RESULT_NAME = "litert_model_inspector.txt"
    }
}

private object TfliteFlatBufferInspector {
    fun inspect(bytes: ByteArray): TfliteInspection {
        val offset = findTfliteFlatBufferOffset(bytes)
        val buffer = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.LITTLE_ENDIAN)
        val rootTable = buffer.getInt(0)
        val identifier = bytes.copyOfRange(offset + 4, offset + 8).toString(Charsets.US_ASCII)
        val subgraphTables = readTableVector(buffer = buffer, table = rootTable, field = MODEL_SUBGRAPHS)
        val subgraphs = subgraphTables.map { subgraphTable ->
            val tensors = readTableVector(buffer = buffer, table = subgraphTable, field = SUBGRAPH_TENSORS)
                .map { tensorTable -> TensorInfo(name = readStringField(buffer, tensorTable, TENSOR_NAME)) }
            val inputIndexes = readIntVector(buffer = buffer, table = subgraphTable, field = SUBGRAPH_INPUTS)
            val outputIndexes = readIntVector(buffer = buffer, table = subgraphTable, field = SUBGRAPH_OUTPUTS)
            SubgraphInfo(
                name = readStringField(buffer, subgraphTable, SUBGRAPH_NAME),
                tensors = tensors,
                inputTensorIndexes = inputIndexes,
                outputTensorIndexes = outputIndexes,
            )
        }
        val signatures = readTableVector(buffer = buffer, table = rootTable, field = MODEL_SIGNATURE_DEFS)
            .map { signatureTable ->
                SignatureInfo(
                    key = readStringField(buffer, signatureTable, SIGNATURE_KEY),
                    methodName = readStringField(buffer, signatureTable, SIGNATURE_METHOD_NAME),
                    subgraphIndex = readUIntField(buffer, signatureTable, SIGNATURE_SUBGRAPH_INDEX),
                    inputs = readTensorMapVector(buffer, signatureTable, SIGNATURE_INPUTS),
                    outputs = readTensorMapVector(buffer, signatureTable, SIGNATURE_OUTPUTS),
                )
            }
        return TfliteInspection(
            flatBufferOffset = offset,
            rootIdentifier = identifier,
            subgraphs = subgraphs,
            signatures = signatures,
        )
    }

    private fun findTfliteFlatBufferOffset(bytes: ByteArray): Int {
        val direct = if (bytes.size >= 8) bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) else ""
        if (direct == TFLITE_IDENTIFIER) return 0
        for (index in 4 until bytes.size - 4) {
            if (
                bytes[index] == 'T'.code.toByte() &&
                bytes[index + 1] == 'F'.code.toByte() &&
                bytes[index + 2] == 'L'.code.toByte() &&
                bytes[index + 3] == '3'.code.toByte()
            ) {
                return index - 4
            }
        }
        error("TFL3 root identifier not found")
    }

    private fun readTensorMapVector(buffer: ByteBuffer, table: Int, field: Int): List<String> {
        return readTableVector(buffer = buffer, table = table, field = field).map { tensorMapTable ->
            val name = readStringField(buffer, tensorMapTable, TENSOR_MAP_NAME)
            val tensorIndex = readUIntField(buffer, tensorMapTable, TENSOR_MAP_TENSOR_INDEX)
            "$name:$tensorIndex"
        }
    }

    private fun readTableVector(buffer: ByteBuffer, table: Int, field: Int): List<Int> {
        val vector = vectorStart(buffer = buffer, table = table, field = field) ?: return emptyList()
        val length = buffer.getInt(vector)
        return (0 until length).map { index ->
            val loc = vector + 4 + index * 4
            loc + buffer.getInt(loc)
        }
    }

    private fun readIntVector(buffer: ByteBuffer, table: Int, field: Int): List<Int> {
        val vector = vectorStart(buffer = buffer, table = table, field = field) ?: return emptyList()
        val length = buffer.getInt(vector)
        return (0 until length).map { index -> buffer.getInt(vector + 4 + index * 4) }
    }

    private fun readStringField(buffer: ByteBuffer, table: Int, field: Int): String {
        val loc = fieldLocation(buffer = buffer, table = table, field = field) ?: return ""
        val start = loc + buffer.getInt(loc)
        val length = buffer.getInt(start)
        val bytes = ByteArray(length)
        val oldPosition = buffer.position()
        buffer.position(start + 4)
        buffer.get(bytes)
        buffer.position(oldPosition)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readUIntField(buffer: ByteBuffer, table: Int, field: Int): Long {
        val loc = fieldLocation(buffer = buffer, table = table, field = field) ?: return 0L
        return buffer.getInt(loc).toLong() and 0xffffffffL
    }

    private fun vectorStart(buffer: ByteBuffer, table: Int, field: Int): Int? {
        val loc = fieldLocation(buffer = buffer, table = table, field = field) ?: return null
        return loc + buffer.getInt(loc)
    }

    private fun fieldLocation(buffer: ByteBuffer, table: Int, field: Int): Int? {
        val vtable = table - buffer.getInt(table)
        val vtableLength = buffer.getShort(vtable).toInt() and 0xffff
        val vtableFieldOffset = 4 + field * 2
        if (vtableFieldOffset >= vtableLength) return null
        val objectFieldOffset = buffer.getShort(vtable + vtableFieldOffset).toInt() and 0xffff
        if (objectFieldOffset == 0) return null
        return table + objectFieldOffset
    }

    private const val TFLITE_IDENTIFIER = "TFL3"
    private const val MODEL_SUBGRAPHS = 2
    private const val MODEL_SIGNATURE_DEFS = 7
    private const val SUBGRAPH_TENSORS = 0
    private const val SUBGRAPH_INPUTS = 1
    private const val SUBGRAPH_OUTPUTS = 2
    private const val SUBGRAPH_NAME = 4
    private const val TENSOR_NAME = 3
    private const val SIGNATURE_INPUTS = 0
    private const val SIGNATURE_OUTPUTS = 1
    private const val SIGNATURE_METHOD_NAME = 2
    private const val SIGNATURE_KEY = 3
    private const val SIGNATURE_SUBGRAPH_INDEX = 4
    private const val TENSOR_MAP_NAME = 0
    private const val TENSOR_MAP_TENSOR_INDEX = 1
}

private data class TfliteInspection(
    val flatBufferOffset: Int,
    val rootIdentifier: String,
    val subgraphs: List<SubgraphInfo>,
    val signatures: List<SignatureInfo>,
)

private data class SubgraphInfo(
    val name: String,
    val tensors: List<TensorInfo>,
    val inputTensorIndexes: List<Int>,
    val outputTensorIndexes: List<Int>,
) {
    val inputTensorNames: List<String>
        get() = inputTensorIndexes.mapNotNull { tensors.getOrNull(it)?.name }
    val outputTensorNames: List<String>
        get() = outputTensorIndexes.mapNotNull { tensors.getOrNull(it)?.name }

    fun prefillDecodePresence(): String {
        val names = tensors.map { it.name.lowercase() }
        val hasPrefill = names.any { it.contains("prefill") }
        val hasDecode = names.any { it.contains("decode") }
        val hasToken = names.any { it.contains("token") }
        val hasCache = names.any { it.contains("cache") || it.contains("kv") }
        return "prefill=$hasPrefill decode=$hasDecode token=$hasToken cacheOrKv=$hasCache"
    }
}

private data class TensorInfo(val name: String)

private data class SignatureInfo(
    val key: String,
    val methodName: String,
    val subgraphIndex: Long,
    val inputs: List<String>,
    val outputs: List<String>,
)
