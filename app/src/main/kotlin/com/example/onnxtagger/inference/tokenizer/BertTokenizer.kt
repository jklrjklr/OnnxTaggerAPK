package com.example.onnxtagger.inference.tokenizer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

// FIX-5: minimal WordPiece BERT tokenizer with zero native dependencies.
// Supports BLIP / BERT-family models. Loaded once and cached in CaptionInferenceEngine.
data class BertTokenizerConfig(
    val vocabMap: Map<String, Int>,
    val clsId: Int = 101,
    val sepId: Int = 102,
    val padId: Int = 0,
    val unkId: Int = 100,
)

class BertTokenizer(private val cfg: BertTokenizerConfig) {

    companion object {
        suspend fun fromUri(context: Context, uri: Uri): BertTokenizer =
            withContext(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)!!.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val root = Json.parseToJsonElement(text).jsonObject
                // HuggingFace tokenizer.json: vocab is under "model" > "vocab"
                val vocabObj = root["model"]?.jsonObject?.get("vocab")?.jsonObject
                    ?: root["vocab"]?.jsonObject
                    ?: error("tokenizer.json: cannot find vocab map")
                val vocabMap = vocabObj.entries.associate { (k, v) -> k to v.jsonPrimitive.int }
                BertTokenizer(BertTokenizerConfig(vocabMap = vocabMap))
            }
    }

    val bosId: Int get() = cfg.clsId
    val eosId: Int get() = cfg.sepId

    // Encode: lowercase → basic tokenize → wordpiece → prepend [CLS], append [SEP]
    fun encode(text: String): IntArray {
        val tokens = mutableListOf(cfg.clsId)
        basicTokenize(text.lowercase()).forEach { word ->
            tokens.addAll(wordpiece(word))
        }
        tokens.add(cfg.sepId)
        return tokens.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id == cfg.clsId || id == cfg.sepId || id == cfg.padId) continue
            val token = cfg.vocabMap.entries.find { it.value == id }?.key ?: continue
            if (token.startsWith("##")) {
                sb.append(token.substring(2))
            } else {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(token)
            }
        }
        return sb.toString().trim()
    }

    private fun basicTokenize(text: String): List<String> {
        return text.replace(Regex("[^\\w\\s]"), " $0 ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun wordpiece(word: String): List<Int> {
        if (word.length > 100) return listOf(cfg.unkId)
        val result = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found = false
            while (start < end) {
                val substr = if (start == 0) word.substring(start, end)
                             else "##${word.substring(start, end)}"
                val id = cfg.vocabMap[substr]
                if (id != null) {
                    result.add(id)
                    start = end
                    found = true
                    break
                }
                end--
            }
            if (!found) { result.add(cfg.unkId); break }
        }
        return result
    }
}
