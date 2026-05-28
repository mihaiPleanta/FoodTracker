package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodtracker.data.SettingsRepository

class FoodViewModelFactory(
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FoodViewModel::class.java)) {
            "FoodViewModelFactory only creates FoodViewModel, got ${modelClass.name}"
        }
        return FoodViewModel(settingsRepository) as T
    }
}
