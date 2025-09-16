package com.nearnet.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.nearnet.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel: ViewModel() {

    //Rooms
    private val roomsMutable = MutableStateFlow(listOf<Room>())
    val rooms = roomsMutable.asStateFlow()


    //constructor to VievModel
    init {
        roomsMutable.value = listOf(
            Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
            Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
            Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
            Room(3, "Biohazard", "Be careful.", false),
            Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
            Room(5, "Fallout", null, true),
            Room(7, "My new world", "Don't join. It's private", true),
            Room(8, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
        )
    }


}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}