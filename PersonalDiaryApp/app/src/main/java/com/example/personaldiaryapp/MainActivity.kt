package com.example.personaldiaryapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.personaldiaryapp.ui.theme.PersonalDiaryAppTheme
import com.maxkeppeker.sheets.core.models.base.UseCaseState
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.calendar.CalendarDialog
import com.maxkeppeler.sheets.calendar.CalendarView
import com.maxkeppeler.sheets.calendar.models.CalendarConfig
import com.maxkeppeler.sheets.calendar.models.CalendarSelection
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Create the DataStore instance as an extension property on Context
val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalDiaryAppTheme {
                // Read the dark mode preference as Compose state
                val context = this
                val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

                val darkModeFlow: Flow<Boolean> = context.dataStore.data
                    .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

                val isDarkMode by darkModeFlow.collectAsState(initial = false)

                // Apply the theme based on the preference
                FullScreen(isDarkMode = isDarkMode)
            }
        }
    }
}

//Main screen with settings and calendar
@Composable
fun FullScreen(isDarkMode: Boolean) {
    // Dynamically apply dark/light theme using Material3
    val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 35.dp, end = 30.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                SettingsScreen()
            }
            DiaryScreen()


        }
    }
}

//Settings part on top right corner
//From class example
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

    val isDarkMode by darkModeFlow.collectAsState(initial = false)

    //Dark/Light mode switch
    Column(
        modifier = Modifier.wrapContentSize(),
        horizontalAlignment = Alignment.End
    ) {
        Text("Dark Mode", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = isDarkMode,
            onCheckedChange = { newValue ->
                scope.launch {
                    context.dataStore.edit { preferences ->
                        preferences[DARK_MODE_KEY] = newValue
                    }
                }
            }
        )
    }
}


//Calendar and diary input section
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen() {
    val context = LocalContext.current
    val calendarState = remember { UseCaseState(visible = true, onCloseRequest = {}) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var entryText by remember { mutableStateOf("") }

    //reading today's entry when app first opened
    LaunchedEffect(Unit) {
        entryText = readFromFile(context, selectedDate.toString())
    }


    //confirmation message
    var message by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp).padding(top=70.dp)) {

        //showing calendar to choose a date
        //to choose a date, user must click on a date and then press "OK" button
        //if Ok button is not clicked, app is not going to choose the date
        CalendarView(
            useCaseState = calendarState,
            selection = CalendarSelection.Date { date ->
                selectedDate = date
                entryText = readFromFile(context, selectedDate.toString())
            },
            config = CalendarConfig()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Diary Entry TextField
        OutlinedTextField(
            value = entryText,
            onValueChange = { entryText = it },
            label = { Text("Diary Entry for $selectedDate") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        //Buttons
        Row(Modifier.padding(5.dp)){
            // Save Button
            Button(
                onClick = {
                    saveToFile(context, selectedDate.toString(), entryText)
                    message = "Entry saved successfully"
                },
                Modifier.padding(10.dp)
            ) {
                Text("Save Entry")
            }

            // Delete Button
            Button(
                onClick = {
                    if (deleteFile(context, selectedDate.toString())) {
                        message = "Entry deleted successfully"
                        entryText=""
                    } else "Error: Couldn't delete the entry. Try Again!"
                },
                Modifier.padding(10.dp)
            ) {
                Text("Delete Entry")
            }
        }

        //showing confirmation message for few seconds
        if (message.isNotBlank()) {
            Text(
                text = message,
                color = Color.Green,
                modifier = Modifier.padding(top = 5.dp)
            )

            //clear the message after few seconds
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2000)
                message = ""
            }
        }

    }
}






////////////////////////////////////////////////////////////////////////////////////////////////
//From class example
// Function to save text to internal storage
fun saveToFile(context: Context, filename: String, content: String) {
    // MODE_PRIVATE means the file is only accessible to this app
    //name of the file --> date like "2025-04-08"
    context.openFileOutput("$filename.txt", Context.MODE_PRIVATE).use { outputStream ->
        outputStream.write(content.toByteArray())
    }
}

// Function to read text from internal storage
fun readFromFile(context: Context, filename: String): String {
    return try {
        val diary_entry = context.openFileInput("$filename.txt").bufferedReader().useLines { lines ->
            lines.joinToString("\n")
        }
        if (diary_entry.isBlank())"No diary entry found." else diary_entry
    } catch (e: FileNotFoundException) {
        "No diary entry found."
    }
}

// Function to delete file from internal storage
fun deleteFile(context: Context, filename: String): Boolean {
    return context.deleteFile("$filename.txt")
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PersonalDiaryAppTheme {
    }
}