package com.example.foodtracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.api.FoodApi
import com.example.foodtracker.data.FakeSettingsRepository
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.cache.CachedProduct
import com.example.foodtracker.data.cache.CachedSearch
import com.example.foodtracker.data.cache.FoodCache
import com.example.foodtracker.model.*
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.SearchResponseDto
import com.example.foodtracker.viewmodel.FoodViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// No-op stubs used only to satisfy FoodViewModel's FoodRepository dependency.
private object NoOpCache : FoodCache {
    override suspend fun getSearch(query: String): CachedSearch? = null
    override suspend fun putSearch(query: String, items: List<FoodItem>, now: Long) {}
    override suspend fun touchSearch(query: String, barcodes: List<String>, now: Long) {}
    override suspend fun getProduct(barcode: String): CachedProduct? = null
    override suspend fun putProduct(item: FoodItem, now: Long) {}
    override suspend fun touchProduct(barcode: String, now: Long) {}
}

private object NoOpFoodApi : FoodApi {
    override suspend fun searchFoods(query: String, pageSize: Int): SearchResponseDto =
        SearchResponseDto(emptyList(), 0)
    override suspend fun getFoodByBarcode(barcode: String): FoodItemDto =
        throw UnsupportedOperationException("NoOpFoodApi")
}

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FoodViewModel

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val trackerStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder("tracker"), "meal_log_tracker.preferences_pb") }
        )
        viewModel = FoodViewModel(
            settingsRepository = FakeSettingsRepository(),
            mealLogTracker = MealLogTracker(trackerStore),
            foodRepository = FoodRepository(NoOpFoodApi, NoOpCache),
        )
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
    fun updateAppSettings_propagatesThroughRepoToStateFlow() = runTest(testDispatcher) {
        val settings = AppSettings(notificationsEnabled = false, language = AppLanguage.ENGLISH)
        viewModel.updateAppSettings(settings)
        advanceUntilIdle()    // drains repo.update + StateFlow propagation
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
