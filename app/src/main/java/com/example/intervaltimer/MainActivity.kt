package com.example.intervaltimer // Ensure this matches your project package!

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IntervalTimerApp()
                }
            }
        }
    }
}

enum class TimerState { SETUP, RUNNING, PAUSED, DONE }
enum class Phase { WORK, REST, GET_READY }

// --- TIMER STATE HOLDER ---
class TimerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // Config (editable on the setup screen)
    var workTime by savedStateHandle.saveable(key = "workTime") { mutableStateOf(120) }
    var restTime by savedStateHandle.saveable(key = "restTime") { mutableStateOf(5) }
    var totalRounds by savedStateHandle.saveable(key = "totalRounds") { mutableStateOf(8) }

    // Live progress (driven by the timer, read-only to the UI)
    var state by savedStateHandle.saveable(key = "state") { mutableStateOf(TimerState.SETUP) }
        private set
    var currentRound by savedStateHandle.saveable(key = "currentRound") { mutableStateOf(1) }
        private set
    var currentPhase by savedStateHandle.saveable(key = "currentPhase") { mutableStateOf(Phase.GET_READY) }
        private set
    var timeLeft by savedStateHandle.saveable(key = "timeLeft") { mutableStateOf(5) }
        private set

    private val vibrator =
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var timerJob: Job? = null

    init {
        if (state == TimerState.RUNNING) launchTimer()
    }

    fun startWorkout() {
        currentRound = 1
        currentPhase = Phase.GET_READY
        timeLeft = restTime
        state = TimerState.RUNNING
        launchTimer()
    }

    fun togglePause() {
        when (state) {
            TimerState.RUNNING -> {
                state = TimerState.PAUSED
                timerJob?.cancel()
            }
            TimerState.PAUSED -> {
                state = TimerState.RUNNING
                launchTimer()
            }
            else -> {}
        }
    }

    fun reset() {
        timerJob?.cancel()
        state = TimerState.SETUP
        currentRound = 1
        currentPhase = Phase.GET_READY
        timeLeft = restTime
    }

    private fun launchTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (state == TimerState.RUNNING && timeLeft > 0) {
                delay(1000L)
                timeLeft--

                if (currentPhase == Phase.WORK && timeLeft in 1..5) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }

                if (timeLeft == 0) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 400)
                    triggerBuzz()

                    if (currentPhase == Phase.REST || currentPhase == Phase.GET_READY) {
                        currentPhase = Phase.WORK
                        timeLeft = workTime
                    } else {
                        if (currentRound < totalRounds) {
                            currentRound++
                            currentPhase = Phase.REST
                            timeLeft = restTime
                        } else {
                            state = TimerState.DONE
                        }
                    }
                }
            }
        }
    }

    private fun triggerBuzz() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        toneGen.release()
    }
}

// --- PERSISTENCE LAYER ---
data class Preset(val workTime: Int, val restTime: Int, val rounds: Int)

class PresetManager(context: Context) {
    private val prefs = context.getSharedPreferences("timer_presets", Context.MODE_PRIVATE)

    fun save(slot: Int, preset: Preset) {
        prefs.edit()
            .putInt("w_$slot", preset.workTime)
            .putInt("r_$slot", preset.restTime)
            .putInt("s_$slot", preset.rounds)
            .putBoolean("e_$slot", true)
            .apply()
    }

    fun load(slot: Int): Preset? {
        if (!prefs.getBoolean("e_$slot", false)) return null
        return Preset(
            workTime = prefs.getInt("w_$slot", 120),
            restTime = prefs.getInt("r_$slot", 5),
            rounds = prefs.getInt("s_$slot", 8)
        )
    }
}

fun formatTime(timeInSeconds: Int): String {
    val m = timeInSeconds / 60
    val s = timeInSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

@Composable
fun IntervalTimerApp(viewModel: TimerViewModel = viewModel()) {
    when (viewModel.state) {
        TimerState.SETUP -> {
            SetupScreen(
                workTime = viewModel.workTime,
                restTime = viewModel.restTime,
                rounds = viewModel.totalRounds,
                onWorkChange = { viewModel.workTime = it },
                onRestChange = { viewModel.restTime = it },
                onRoundsChange = { viewModel.totalRounds = it },
                onStart = { viewModel.startWorkout() }
            )
        }
        else -> {
            ActiveTimerScreen(
                currentState = viewModel.state,
                timeLeft = viewModel.timeLeft,
                currentPhase = viewModel.currentPhase,
                currentRound = viewModel.currentRound,
                totalRounds = viewModel.totalRounds,
                onTogglePause = { viewModel.togglePause() },
                onBack = { viewModel.reset() }
            )
        }
    }
}

@Composable
fun SetupScreen(
    workTime: Int,
    restTime: Int,
    rounds: Int,
    onWorkChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val presetManager = remember { PresetManager(context) }

    var showPresetsDialog by remember { mutableStateOf(false) }
    var presetsUpdated by remember { mutableIntStateOf(0) }

    if (showPresetsDialog) {
        AlertDialog(
            onDismissRequest = { showPresetsDialog = false },
            title = { Text("Saved Presets", fontWeight = FontWeight.Bold) },
            text = {
                val dummy = presetsUpdated
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (i in 1..4) {
                        val preset = presetManager.load(i)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (preset != null) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Slot $i", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("${preset.rounds}x ${formatTime(preset.workTime)} / ${formatTime(preset.restTime)}", color = Color.LightGray, fontSize = 14.sp)
                                }
                                Row {
                                    Button(
                                        onClick = {
                                            onWorkChange(preset.workTime)
                                            onRestChange(preset.restTime)
                                            onRoundsChange(preset.rounds)
                                            showPresetsDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp).padding(end = 8.dp)
                                    ) { Text("LOAD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }

                                    Button(
                                        onClick = {
                                            presetManager.save(i, Preset(workTime, restTime, rounds))
                                            presetsUpdated++
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("SAVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                                }
                            } else {
                                Text("Slot $i: Empty", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                Button(
                                    onClick = {
                                        presetManager.save(i, Preset(workTime, restTime, rounds))
                                        presetsUpdated++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(36.dp)
                                ) { Text("SAVE CURRENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetsDialog = false }) {
                    Text("Close", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF141F23),
            titleContentColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141F23))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TIMER", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
            Button(
                onClick = { showPresetsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6572)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text("PRESETS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }

        NumberInputRow("SETS", rounds, onRoundsChange)
        Spacer(modifier = Modifier.height(24.dp))

        TimeInputRow("WORK", workTime, onWorkChange)
        Spacer(modifier = Modifier.height(24.dp))

        TimeInputRow("REST", restTime, onRestChange)

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6572))
        ) {
            Text("START", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun SquareButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun AtmTextField(value: Int, onValueChange: (Int) -> Unit) {
    val textVal = value.toString().padStart(2, '0')
    var textFieldValue by remember(textVal) {
        mutableStateOf(TextFieldValue(text = textVal, selection = TextRange(textVal.length)))
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val digits = newValue.text.filter { it.isDigit() }
            if (digits.length <= 2) {
                val newInt = digits.toIntOrNull() ?: 0
                textFieldValue = newValue.copy(text = digits)
                onValueChange(newInt)
            } else {
                val lastTwo = digits.takeLast(2)
                val newInt = lastTwo.toIntOrNull() ?: 0
                textFieldValue = TextFieldValue(text = lastTwo, selection = TextRange(2))
                onValueChange(newInt)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        modifier = Modifier.width(60.dp),
        cursorBrush = SolidColor(Color(0xFFFFC107)),
        singleLine = true
    )
}

@Composable
fun SetsTextField(value: Int, onValueChange: (Int) -> Unit) {
    val textVal = value.toString()
    var textFieldValue by remember(textVal) {
        mutableStateOf(TextFieldValue(text = textVal, selection = TextRange(textVal.length)))
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val digits = newValue.text.filter { it.isDigit() }
            if (digits.length <= 2) {
                val newInt = digits.toIntOrNull() ?: 0
                textFieldValue = newValue.copy(text = digits)
                if (newInt > 0) onValueChange(newInt)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        modifier = Modifier.width(60.dp),
        cursorBrush = SolidColor(Color(0xFFFFC107)),
        singleLine = true
    )
}

@Composable
fun TimeInputRow(label: String, totalSeconds: Int, onValueChange: (Int) -> Unit) {
    val m = totalSeconds / 60
    val s = totalSeconds % 60

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton("-") { if (totalSeconds >= 5) onValueChange(totalSeconds - 5) }
            Spacer(modifier = Modifier.width(32.dp))
            AtmTextField(value = m, onValueChange = { newM -> onValueChange(newM * 60 + s) })
            Text(" : ", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            AtmTextField(value = s, onValueChange = { newS -> onValueChange(m * 60 + newS) })
            Spacer(modifier = Modifier.width(32.dp))
            SquareButton("+") { onValueChange(totalSeconds + 5) }
        }
    }
}

@Composable
fun NumberInputRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton("-") { if (value > 1) onValueChange(value - 1) }
            Spacer(modifier = Modifier.width(32.dp))
            SetsTextField(value = value, onValueChange = onValueChange)
            Spacer(modifier = Modifier.width(32.dp))
            SquareButton("+") { onValueChange(value + 1) }
        }
    }
}

@Composable
fun ActiveTimerScreen(
    currentState: TimerState,
    timeLeft: Int,
    currentPhase: Phase,
    currentRound: Int,
    totalRounds: Int,
    onTogglePause: () -> Unit,
    onBack: () -> Unit
) {
    var showQuitDialog by rememberSaveable { mutableStateOf(false) }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("Quit Workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to end your current workout early?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQuitDialog = false
                        onBack()
                    }
                ) {
                    Text("Yes, Quit", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showQuitDialog = false }
                ) {
                    Text("Cancel", color = Color.Black)
                }
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.DarkGray
        )
    }

    val backgroundColor = when (currentState) {
        TimerState.DONE -> Color(0xFF2196F3)
        TimerState.PAUSED -> Color.DarkGray
        else -> when (currentPhase) {
            Phase.WORK -> Color(0xFF4CAF50)
            Phase.GET_READY -> Color(0xFFFF9800)
            Phase.REST -> Color(0xFFF44336)
        }
    }

    val phaseText = when {
        currentState == TimerState.PAUSED -> "PAUSED"
        currentPhase == Phase.GET_READY -> "GET READY"
        else -> currentPhase.name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (currentState == TimerState.DONE) {
            Text("WORKOUT COMPLETE", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.height(60.dp).width(200.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Finish", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                text = phaseText,
                fontSize = 56.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = formatTime(timeLeft),
                fontSize = 120.sp,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Round $currentRound / $totalRounds",
                fontSize = 32.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onTogglePause() },
                    modifier = Modifier.height(60.dp).width(150.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(
                        text = if (currentState == TimerState.PAUSED) "RESUME" else "PAUSE",
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (currentState == TimerState.RUNNING) onTogglePause()
                        showQuitDialog = true
                    },
                    modifier = Modifier.height(60.dp).width(150.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Text("QUIT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}