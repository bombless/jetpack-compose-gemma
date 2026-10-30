package com.example.gemmarbt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemmarbt.ui.AgentExecutionState
import com.example.gemmarbt.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    secondary = Color(0xFF81C784),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()
                    MainScreen(
                        state = state,
                        onStartClicked = { viewModel.startRedBlackTreeExperiment() },
                        onRetryCheck = { viewModel.checkServer() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: AgentExecutionState,
    onStartClicked: () -> Unit,
    onRetryCheck: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gemma 4 E2B on 8 Gen 3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Red-Black Tree Tool-Use Agent (Python & JS)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = onRetryCheck) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Server")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.serverConnected) Color(0xFF1A3320) else Color(0xFF331A1A)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (state.serverConnected) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                    Column {
                        Text(
                            text = if (state.serverConnected) "Local Model Server Ready" else "Server Not Detected",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = state.currentStep,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCCCCCC)
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = onStartClicked,
                enabled = !state.isRunning && state.serverConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inference & Executing Tools...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run RBT Synthesis (Python + JS Tools)")
                }
            }

            // Python Result Section
            if (state.pythonCode.isNotEmpty() || state.pythonResult.isNotEmpty()) {
                CodeResultSection(
                    title = "Python Runtime Result",
                    tag = "run_python_code",
                    isSuccess = state.isPythonSuccess,
                    code = state.pythonCode,
                    result = state.pythonResult
                )
            }

            // JavaScript Result Section
            if (state.javascriptCode.isNotEmpty() || state.javascriptResult.isNotEmpty()) {
                CodeResultSection(
                    title = "JavaScript Runtime Result",
                    tag = "run_javascript_code",
                    isSuccess = state.isJsSuccess,
                    code = state.javascriptCode,
                    result = state.javascriptResult
                )
            }

            // Live Agent Logs
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Execution Logs", fontWeight = FontWeight.Bold, color = Color(0xFF90CAF9))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 240.dp)
                            .background(Color(0xFF0F0F0F), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (state.logs.isEmpty()) "No logs yet..." else state.logs.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeResultSection(
    title: String,
    tag: String,
    isSuccess: Boolean,
    code: String,
    result: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                ) {
                    Text(
                        text = if (isSuccess) "VERIFIED PASS" else "RUNNING / EVAL",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Result Preview
            Text("Tool Output ($tag):", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = result.ifEmpty { "Waiting for tool execution..." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (isSuccess) Color(0xFFA5D6A7) else Color(0xFFEF9A9A)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Code Preview
            Text("Generated Code Snippet:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .background(Color(0xFF0F0F0F), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = code.ifEmpty { "Generating..." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }
    }
}
