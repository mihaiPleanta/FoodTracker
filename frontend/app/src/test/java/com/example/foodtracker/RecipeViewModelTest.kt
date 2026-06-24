package com.example.foodtracker

import com.example.foodtracker.api.RecipeApi
import com.example.foodtracker.model.RecipeDto
import com.example.foodtracker.model.RecipeGenerateRequest
import com.example.foodtracker.model.SavedRecipeCreate
import com.example.foodtracker.model.SavedRecipeDto
import com.example.foodtracker.viewmodel.RecipeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private fun sampleRecipe(title: String) = RecipeDto(
    title = title, description = "d", ingredients = emptyList(), steps = emptyList(),
    servings = 1, kcalPerServing = 100, proteinG = 1, carbsG = 1, fatG = 1,
)

private fun savedDto(id: Long, title: String) = SavedRecipeDto(
    id = id, mealType = "BREAKFAST", title = title, recipe = sampleRecipe(title),
    createdAt = "2026-06-24T00:00:00",
)

/** A RecipeApi whose saved-recipe list can be swapped, to simulate switching accounts. */
private class FakeRecipeApi(var saved: List<SavedRecipeDto>) : RecipeApi {
    override suspend fun generateRecipe(body: RecipeGenerateRequest): RecipeDto =
        throw UnsupportedOperationException()
    override suspend fun saveRecipe(body: SavedRecipeCreate): SavedRecipeDto =
        throw UnsupportedOperationException()
    override suspend fun getRecipes(): List<SavedRecipeDto> = saved
    override suspend fun deleteRecipe(id: Long): Response<Unit> = Response.success(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun resetUserState_clearsSavedRecipesFromPreviousAccount() = runTest(testDispatcher) {
        val vm = RecipeViewModel(FakeRecipeApi(listOf(savedDto(1, "Account A pancakes"))))
        advanceUntilIdle()                       // init loadSaved() → account A's recipe
        assertEquals(1, vm.savedRecipes.value.size)

        vm.resetUserState()
        advanceUntilIdle()

        assertTrue(vm.savedRecipes.value.isEmpty())   // account A's recipe must not leak
    }

    @Test
    fun resetUserState_resetsSelectedMealType() = runTest(testDispatcher) {
        val vm = RecipeViewModel(FakeRecipeApi(emptyList()))
        advanceUntilIdle()
        vm.setMealType("DINNER")
        assertEquals("DINNER", vm.selectedMealType.value)

        vm.resetUserState()
        advanceUntilIdle()

        assertEquals("BREAKFAST", vm.selectedMealType.value)
    }

    @Test
    fun afterReset_loadSavedFetchesNewAccountList() = runTest(testDispatcher) {
        val api = FakeRecipeApi(listOf(savedDto(1, "Account A")))
        val vm = RecipeViewModel(api)
        advanceUntilIdle()
        assertEquals(1, vm.savedRecipes.value.size)

        // Logout clears state.
        vm.resetUserState()
        advanceUntilIdle()
        assertTrue(vm.savedRecipes.value.isEmpty())

        // Account B logs in and opens Meals → the screen reloads the saved list.
        api.saved = listOf(savedDto(2, "Account B one"), savedDto(3, "Account B two"))
        vm.loadSaved()
        advanceUntilIdle()

        assertEquals(2, vm.savedRecipes.value.size)
        assertEquals("Account B one", vm.savedRecipes.value[0].title)
    }
}
