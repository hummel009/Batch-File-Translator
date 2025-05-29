package com.github.hummel.bft

import com.google.gson.JsonParser
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun main() {
	val config = readConfigFile()
	val folderPath = config.getOrDefault("folderPath", "D:/Test")
	val targetLanguage = config.getOrDefault("targetLanguage", "ru")
	translateFiles(folderPath, targetLanguage)
}

private fun translateFiles(folderPath: String, targetLanguage: String) {
	val folder = File(folderPath)
	translateFilesRecursive(folder, targetLanguage)
}

private fun translateFilesRecursive(file: File, targetLanguage: String) {
	if (file.isDirectory) {
		for (childFile in file.listFiles() ?: return) {
			translateFilesRecursive(childFile, targetLanguage)
		}
	} else if (file.isFile) {
		val originalText = file.readText()
		val translatedText = translateText(originalText, targetLanguage)
		file.writeText(translatedText)
		println("Файл ${file.name} переведен и сохранен.")
	}
}

private fun translateText(text: String, targetLanguage: String): String {
	val apiUrl = "https://translate.googleapis.com/translate_a/single"
	val parameters = mapOf(
		"client" to "gtx",
		"sl" to "auto",
		"tl" to targetLanguage,
		"dt" to "t",
		"q" to URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
	)

	val url = "$apiUrl?${parameters.map { "${it.key}=${it.value}" }.joinToString("&")}"

	HttpClients.createDefault().use {
		val request = HttpGet(url)

		return@translateText it.execute(request) { response ->
			val entity = response.entity
			val jsonResponse = EntityUtils.toString(entity, StandardCharsets.UTF_8)
			parseTranslatedText(jsonResponse)
		}
	}
}

private fun parseTranslatedText(jsonResponse: String): String {
	val jsonElement = JsonParser.parseString(jsonResponse)
	val translationsArray = jsonElement.asJsonArray[0].asJsonArray
	val translatedText = StringBuilder()

	(0 until translationsArray.size()).asSequence().map {
		translationsArray[it].asJsonArray
	}.forEach {
		translatedText.append(it[0].asString)
	}

	return "$translatedText"
}

private fun readConfigFile(): Map<String, String> {
	val configFile = File("config.json")
	if (!configFile.exists()) {
		return emptyMap()
	}

	val jsonText = configFile.readText()
	val jsonElement = JsonParser.parseString(jsonText)
	val configMap = mutableMapOf<String, String>()

	jsonElement.asJsonObject.entrySet().forEach { (key, value) ->
		configMap[key] = value.asString
	}

	return configMap
}