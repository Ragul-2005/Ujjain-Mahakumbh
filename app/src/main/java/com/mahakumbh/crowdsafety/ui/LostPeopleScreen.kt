package com.mahakumbh.crowdsafety.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LostPeopleScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Report Missing") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Search Cases") })
        }
        when (selectedTab) {
            0 -> ReportLostPersonForm()
            1 -> LostPeopleCasesList()
        }
    }
}

@Composable
fun ReportLostPersonForm() {
    // TODO: Add form fields for photo, name, age, clothing, last seen location
    Text("Lost Person Report Form (to be implemented)", modifier = Modifier.padding(16.dp))
}

@Composable
fun LostPeopleCasesList() {
    // TODO: List of expandable cards for missing people
    Text("Lost People Cases List (to be implemented)", modifier = Modifier.padding(16.dp))
}
