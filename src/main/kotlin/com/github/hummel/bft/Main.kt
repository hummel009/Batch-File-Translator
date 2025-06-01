package com.github.hummel.bft

import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTGitHubDarkIJTheme
import com.google.gson.JsonParser
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GridLayout
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

fun main() {
	FlatLightLaf.setup()
	EventQueue.invokeLater {
		try {
			UIManager.setLookAndFeel(FlatMTGitHubDarkIJTheme())
			val frame = FileTranslator()
			frame.isVisible = true
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
}

class FileTranslator : JFrame() {
	private var folderPathField: JTextField
	private var sourceLangCombo: JComboBox<String>
	private var targetLangCombo: JComboBox<String>
	private var processButton: JButton
	private var timeoutField: JTextField

	private val languages: List<Pair<String, String>> = listOf(
		"Auto" to "auto",
		"English" to "en",
		"Russian" to "ru",
		"Spanish" to "es",
		"French" to "fr",
		"German" to "de",
		"Chinese" to "zh",
		"Japanese" to "ja",
		"Korean" to "ko",
		"Arabic" to "ar"
	)

	init {
		title = "Hummel009's File Translator"
		defaultCloseOperation = EXIT_ON_CLOSE
		setBounds(100, 100, 600, 270)

		val contentPanel = JPanel().apply {
			border = EmptyBorder(10, 10, 10, 10)
			layout = GridLayout(0, 1, 5, 10)
		}

		val folderPanel = JPanel(BorderLayout(5, 0)).apply {
			add(JLabel("Folder path:").apply {
				preferredSize = Dimension(100, preferredSize.height)
			}, BorderLayout.WEST)

			folderPathField = JTextField()
			add(folderPathField, BorderLayout.CENTER)

			val browseButton = JButton("Browse").apply {
				preferredSize = Dimension(100, preferredSize.height)
				addActionListener {
					selectPath()
				}
			}
			add(browseButton, BorderLayout.EAST)
		}

		val sourcePanel = JPanel(BorderLayout(5, 0)).apply {
			add(JLabel("Source language:").apply {
				preferredSize = Dimension(100, preferredSize.height)
			}, BorderLayout.WEST)

			sourceLangCombo = JComboBox<String>().apply {
				languages.map {
					it.first
				}.forEach {
					addItem(it)
				}
			}
			add(sourceLangCombo, BorderLayout.CENTER)
		}

		val targetPanel = JPanel(BorderLayout(5, 0)).apply {
			add(JLabel("Target language:").apply {
				preferredSize = Dimension(100, preferredSize.height)
			}, BorderLayout.WEST)

			targetLangCombo = JComboBox<String>().apply {
				languages.filter {
					it.second != "auto"
				}.map {
					it.first
				}.forEach {
					addItem(it)
				}
			}
			add(targetLangCombo, BorderLayout.CENTER)
		}

		val timeoutPanel = JPanel(BorderLayout(5, 0)).apply {
			add(JLabel("Timeout (sec):").apply {
				preferredSize = Dimension(100, preferredSize.height)
			}, BorderLayout.WEST)

			timeoutField = JTextField("5")
			add(timeoutField, BorderLayout.CENTER)
		}

		processButton = JButton("Translate").apply {
			preferredSize = Dimension(100, preferredSize.height)
			alignmentX = CENTER_ALIGNMENT
			addActionListener {
				process()
			}
		}

		contentPanel.add(folderPanel)
		contentPanel.add(sourcePanel)
		contentPanel.add(targetPanel)
		contentPanel.add(timeoutPanel)
		contentPanel.add(processButton)

		contentPane = contentPanel

		setLocationRelativeTo(null)
	}

	private fun process() {
		val folderPath = folderPathField.text
		if (folderPath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Select folder path", "Error", JOptionPane.ERROR_MESSAGE)
			return
		}

		val timeoutSeconds = try {
			val t = timeoutField.text.toLong()
			if (t <= 0) {
				throw NumberFormatException()
			}
			t
		} catch (_: Exception) {
			JOptionPane.showMessageDialog(this, "Timeout must be a positive number", "Error", JOptionPane.ERROR_MESSAGE)
			return
		}

		listOf(folderPathField, sourceLangCombo, targetLangCombo, processButton).forEach {
			it.isEnabled = false
		}

		thread {
			try {
				val selectedSourceLangName = sourceLangCombo.selectedItem as String
				val selectedTargetLangName = targetLangCombo.selectedItem as String

				val sourceLang = languages.find { it.first == selectedSourceLangName }?.second ?: "auto"
				val targetLang = languages.find { it.first == selectedTargetLangName }?.second ?: "en"

				val folder = File(folderPath)
				translateFilesRecursive(folder, sourceLang, targetLang, timeoutSeconds)

				SwingUtilities.invokeLater {
					JOptionPane.showMessageDialog(
						this, "Translation completed!", "Success", JOptionPane.INFORMATION_MESSAGE
					)
				}
			} catch (e: Exception) {
				e.printStackTrace()
				SwingUtilities.invokeLater {
					JOptionPane.showMessageDialog(
						this,
						"Error during translation: ${e.message}",
						"Error",
						JOptionPane.ERROR_MESSAGE
					)
				}
			} finally {
				SwingUtilities.invokeLater {
					listOf(folderPathField, sourceLangCombo, targetLangCombo, processButton).forEach {
						it.isEnabled = true
					}
				}
			}
		}
	}


	private fun selectPath() {
		val chooser = JFileChooser().apply {
			fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			folderPathField.text = chooser.selectedFile.absolutePath
		}
	}

	private fun translateFilesRecursive(
		file: File, sourceLanguage: String, targetLanguage: String, timeoutSeconds: Long
	) {
		if (file.isDirectory) {
			file.listFiles()?.forEach {
				translateFilesRecursive(it, sourceLanguage, targetLanguage, timeoutSeconds)
			}
		} else if (file.isFile) {
			val originalText = file.readText()
			val translatedText = translateText(
				originalText, sourceLanguage, targetLanguage, timeoutSeconds
			) ?: throw Exception()
			file.writeText(translatedText)
		}
	}

	private fun translateText(
		text: String, sourceLanguage: String, targetLanguage: String, timeoutSeconds: Long
	): String? {
		val apiUrl = "https://translate.googleapis.com/translate_a/single"
		val parameters = mapOf(
			"client" to "gtx",
			"sl" to sourceLanguage,
			"tl" to targetLanguage,
			"dt" to "t",
			"q" to URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
		)

		val url = "$apiUrl?${parameters.map { "${it.key}=${it.value}" }.joinToString("&")}"

		val request = HttpGet(url)

		HttpClients.createDefault().use {
			val translatedText = it.execute(request) { response ->
				val entity = response.entity
				val jsonResponse = EntityUtils.toString(entity, StandardCharsets.UTF_8)
				parseTranslatedText(jsonResponse)
			}
			Thread.sleep(timeoutSeconds * 1000)
			return translatedText
		}
	}

	private fun parseTranslatedText(jsonResponse: String): String? {
		return try {
			val jsonElement = JsonParser.parseString(jsonResponse)
			val translationsArray = jsonElement.asJsonArray[0].asJsonArray
			val translatedText = StringBuilder()

			(0 until translationsArray.size()).asSequence().map {
				translationsArray[it].asJsonArray
			}.forEach {
				translatedText.append(it[0].asString)
			}

			"$translatedText"
		} catch (e: Exception) {
			e.printStackTrace()
			null
		}
	}
}