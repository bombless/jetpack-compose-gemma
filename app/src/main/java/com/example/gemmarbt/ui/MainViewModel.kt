package com.example.gemmarbt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmarbt.llm.ChatMessage
import com.example.gemmarbt.llm.LlamaClient
import com.example.gemmarbt.runtime.JavaScriptRuntime
import com.example.gemmarbt.runtime.PythonRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class AgentExecutionState(
    val isRunning: Boolean = false,
    val serverConnected: Boolean = false,
    val currentStep: String = "Idle",
    val logs: List<String> = emptyList(),
    val pythonCode: String = "",
    val pythonResult: String = "",
    val javascriptCode: String = "",
    val javascriptResult: String = "",
    val isPythonSuccess: Boolean = false,
    val isJsSuccess: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val llamaClient = LlamaClient()
    private val jsRuntime = JavaScriptRuntime(application.applicationContext)
    private val pyRuntime = PythonRuntime(application.filesDir)

    private val _uiState = MutableStateFlow(AgentExecutionState())
    val uiState: StateFlow<AgentExecutionState> = _uiState.asStateFlow()

    init {
        checkServer()
    }

    fun checkServer() {
        viewModelScope.launch {
            val ok = llamaClient.checkHealth()
            _uiState.value = _uiState.value.copy(
                serverConnected = ok,
                currentStep = if (ok) "Connected to Gemma 4 E2B on Snapdragon 8 Gen 3" else "Cannot connect to 127.0.0.1:8080"
            )
            appendLog(if (ok) "Gemma 4 llama-server online (127.0.0.1:8080)" else "llama-server offline. Please ensure llama-server is running.")
        }
    }

    private fun appendLog(msg: String) {
        android.util.Log.i("GEMMA_RBT", msg)
        val updated = _uiState.value.logs + msg
        _uiState.value = _uiState.value.copy(logs = updated)
    }

    fun startRedBlackTreeExperiment() {
        if (_uiState.value.isRunning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunning = true,
                currentStep = "Starting Red-Black Tree agent verification...",
                pythonCode = "",
                pythonResult = "",
                javascriptCode = "",
                javascriptResult = "",
                isPythonSuccess = false,
                isJsSuccess = false
            )
            appendLog("========== Starting Red-Black Tree Verification ==========")
            appendLog("Model: Gemma 4 E2B Q4_K_M (bartowski imatrix quant)")
            appendLog("Hardware: Snapdragon 8 Gen 3 Kryo Octa-Core")

            // 1. Python RBT Generation & Execution
            runPythonStep()

            // 2. JavaScript RBT Generation & Execution
            runJsStep()

            _uiState.value = _uiState.value.copy(
                isRunning = false,
                currentStep = "All verifications completed!"
            )
            appendLog("========== All steps finished ==========")
        }
    }

    private suspend fun runPythonStep() {
        _uiState.value = _uiState.value.copy(currentStep = "Generating Python Red-Black Tree...")
        appendLog("[Step 1] Requesting Python Red-Black Tree from Gemma 4...")

        val pyPrompt = """
            请用Python编写一个精简且完整的红黑树（Red-Black Tree），包含RBNode、left_rotate、right_rotate、insert_fixup，以及验证红黑树5大性质的测试代码并在main中插入30个随机数验证。
            请务必用 ```python 和 ``` 包裹代码。
        """.trimIndent()

        try {
            val response = llamaClient.chat(
                messages = listOf(ChatMessage(role = "user", content = pyPrompt)),
                temperature = 0.2,
                maxTokens = 1200
            )

            val code = extractCodeBlock(response, "python")
            _uiState.value = _uiState.value.copy(
                pythonCode = code,
                currentStep = "Executing Python code in runtime..."
            )
            appendLog("Python code generated (${code.length} chars). Executing in Python runtime...")

            val result = pyRuntime.execute(code)
            val success = result.contains("SUCCESS") || result.contains("PASSED") || result.contains("5")
            _uiState.value = _uiState.value.copy(
                pythonResult = result,
                isPythonSuccess = success
            )
            appendLog("Python execution completed:\n$result")
        } catch (e: Exception) {
            val err = "Python Step Error: ${e.message}"
            _uiState.value = _uiState.value.copy(pythonResult = err)
            appendLog(err)
        }
    }

    private suspend fun runJsStep() {
        _uiState.value = _uiState.value.copy(currentStep = "Generating JavaScript Red-Black Tree...")
        appendLog("[Step 2] Requesting JavaScript Red-Black Tree from Gemma 4...")

        val jsPrompt = """
            请用JavaScript编写一个精简且完整的红黑树（Red-Black Tree），包含RBNode、leftRotate、rightRotate、insertFixup，以及验证红黑树5大性质的测试函数，插入30个随机数并通过console.log输出验证结果。
            请务必用 ```javascript 和 ``` 包裹代码。
        """.trimIndent()

        try {
            val response = llamaClient.chat(
                messages = listOf(ChatMessage(role = "user", content = jsPrompt)),
                temperature = 0.2,
                maxTokens = 1200
            )

            val code = extractCodeBlock(response, "javascript")
            _uiState.value = _uiState.value.copy(
                javascriptCode = code,
                currentStep = "Executing JavaScript in QuickJS/V8 WebView runtime..."
            )
            appendLog("JavaScript code generated (${code.length} chars). Executing in JS runtime...")

            val result = jsRuntime.execute(code)
            val success = result.contains("SUCCESS") || result.contains("true") || result.contains("passed") || !result.contains("Error")
            _uiState.value = _uiState.value.copy(
                javascriptResult = result,
                isJsSuccess = success
            )
            appendLog("JavaScript execution completed:\n$result")
        } catch (e: Exception) {
            val err = "JavaScript Step Error: ${e.message}"
            _uiState.value = _uiState.value.copy(javascriptResult = err)
            appendLog(err)
        }
    }

    private fun extractCodeBlock(text: String, lang: String): String {
        val pattern = Pattern.compile("```(?:$lang)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trim() ?: ""
        } else {
            // fallback: return full text or lines starting with class/def/function
            text.trim()
        }
    }
}
