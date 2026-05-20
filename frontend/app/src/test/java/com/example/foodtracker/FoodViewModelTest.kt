package com.example.foodtracker

import com.example.foodtracker.model.*
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.viewmodel.FoodViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: FoodViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FoodViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateUserProfile_updatesValue() {
        val profile = UserProfile(name = "Ana", age = 25, gender = Gender.FEMALE)
        viewModel.updateUserProfile(profile)
        assertEquals(profile, viewModel.userProfile.value)
    }

    @Test
    fun updateAppSettings_updatesValue() {
        val settings = AppSettings(notificationsEnabled = false, language = AppLanguage.ENGLISH)
        viewModel.updateAppSettings(settings)
        assertEquals(settings, viewModel.appSettings.value)
    }

    private fun sampleFood(name: String) = FoodItem(
        barcode = "1234567890123",
        name = name,
        brand = null,
        imageUrl = null,
        categories = emptyList(),
        per100g = 100,
        protein100g = 5f,
        carbs100g = 10f,
        fat100g = 2f,
    )

    @Test
    fun addFoodToMealReturningIndex_returnsZeroForFirstAdd() {
        val index = viewModel.addFoodToMealReturningIndex(
            "Breakfast",
            LoggedFood(sampleFood("Oats"), 100)
        )
        assertEquals(0, index)
    }

    @Test
    fun addFoodToMealReturningIndex_returnsIncrementingIndexes() {
        val firstIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Rice"), 200)
        )
        val secondIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Chicken"), 150)
        )
        val thirdIndex = viewModel.addFoodToMealReturningIndex(
            "Lunch",
            LoggedFood(sampleFood("Broccoli"), 80)
        )
        assertEquals(0, firstIndex)
        assertEquals(1, secondIndex)
        assertEquals(2, thirdIndex)
    }
}
