package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.SettingsRepository
import com.example.foodtracker.data.journal.JournalRepository

class FoodViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val mealLogTracker: MealLogTracker,
    private val foodRepository: FoodRepository,
    private val journal: JournalRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            "FoodViewModelFactory only creates FoodViewModel, got ${modelClass.name}"
        }
        return FoodViewModel(settingsRepository, mealLogTracker, foodRepository, journal) as T
    }
}
