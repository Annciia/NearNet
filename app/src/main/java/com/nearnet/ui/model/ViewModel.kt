package com.nearnet.ui.model

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearnet.Recent
import com.nearnet.sessionlayer.logic.MessageUtils
import com.nearnet.sessionlayer.logic.RoomRepository
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.ui.component.PasswordValidationResult
import com.nearnet.ui.component.validatePassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import java.util.LinkedList
import com.nearnet.sessionlayer.logic.PublicKeyManager
import com.nearnet.sessionlayer.logic.CryptoUtils
import com.nearnet.sessionlayer.logic.ServerConfig
import com.nearnet.sessionlayer.logic.UserStatus
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlin.system.exitProcess


//Popup's type, popup's structure
enum class PopupType {
    DELETE_USER_AUTHORIZATION,
    LOGOUT_CONFIRMATION,
    DELETE_ROOM_CONFIRMATION,
    JOIN_ROOM_CONFIRMATION,
    JOIN_ROOM_APPROVAL,
    LEAVE_ROOM_CONFIRMATION,
    DROP_ADMIN_CONFIRMATION,
    EDIT_AVATAR,
    USER_LIST_IN_ROOM,
    SERVER_SETTINGS
}
class PopupContext(
    val type: PopupType,
    val data: Any?
)
class PopupContextApprovalData(
    val user: UserData,
    val room: RoomData
)

//Message structure
enum class MessageType(val type: String) {
    TEXT("TEXT"),
    IMAGE("IMAGE"),
    FILE("FILE")
}

//event dotyczący wyniku przetwarzania jakiejś operacji asynchronicznej
sealed class ProcessEvent<out T> {
    data class Success<T>(val data: T): ProcessEvent<T>()
    data class Error(val err: String): ProcessEvent<Nothing>()
}

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel(): ViewModel() {
    lateinit var repository: UserRepository
    lateinit var roomRepository: RoomRepository
    //lateinit var messageUtils: MessageUtils
    private var contextProvider: (() -> Context)? = null
    private val keysBeingSaved = mutableSetOf<String>()

    /**
     * Inicjalizacja MessageUtils z providerami tokenu i kontekstu
     *
     * Wywoływana przy starcie aplikacji
     * Umożliwia MessageUtils dostęp do tokenu i kontekstu bez użycia LocalContext
     *
     * @param context Kontekst aplikacji
     */
    fun initMessageUtils(context: Context) {
        MessageUtils.init(
            tokenProv = { UserRepository.getTokenFromPreferences(context) },
            contextProv = { context } // ← DODAJ
        )

        contextProvider = { context }
    }

    //Selected user
    private val selectedUserMutable = MutableStateFlow<UserData?>(null)
    val selectedUser = selectedUserMutable.asStateFlow()
    private val selectedUserEventMutable = MutableSharedFlow<ProcessEvent<UserData?>>()
    val selectedUserEvent = selectedUserEventMutable.asSharedFlow()

    //Register user
    private val registerUserEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val registerUserEvent = registerUserEventMutable.asSharedFlow()

    //Update user
    private val updateUserEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val updateUserEvent = updateUserEventMutable.asSharedFlow()

    //Delete user
    private val deleteUserEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val deleteUserEvent = deleteUserEventMutable.asSharedFlow()

    //Welcome state
    private val welcomeStateMutable = MutableStateFlow<Boolean>(false)
    val welcomeState = welcomeStateMutable.asStateFlow()

    //Rooms
    private val myRoomsMutable = MutableStateFlow(listOf<RoomData>())
    val myRooms = myRoomsMutable.asStateFlow()

    //Discover rooms
    private val discoverRoomsMutable = MutableStateFlow(listOf<RoomData>())
    val discoverRooms = discoverRoomsMutable.asStateFlow()

    //Filtered my rooms
    private val searchMyRoomsTextMutable = MutableStateFlow("")
    val searchMyRoomsText = searchMyRoomsTextMutable.asStateFlow()
    val filteredMyRoomsList : StateFlow<List<RoomData>> = combine(myRooms, searchMyRoomsText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Filtered discover rooms
    private val searchDiscoverTextMutable = MutableStateFlow("")
    val searchDiscoverText = searchDiscoverTextMutable.asStateFlow()
    var filteredDiscoverList : StateFlow<List<RoomData>> = combine(discoverRooms, searchDiscoverText) { rooms, searchText ->
        Log.d("FilteredDiscover", "discoverRooms=${rooms.map { it.name }} searchText=$searchText")
        if (searchText.isEmpty()) {
            rooms.filter { it.isVisible }
        } else {
            rooms.filter { room ->
                if (room.isVisible) {
                    room.name.contains(searchText, ignoreCase = true)
                } else {
                    room.name.equals(searchText, ignoreCase = true)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<RoomData?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()
    private val selectedRoomEventMutable = MutableSharedFlow<ProcessEvent<RoomData>>()
    val selectedRoomEvent = selectedRoomEventMutable.asSharedFlow()
    private val verifyKeyExistEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val verifyKeyExistEvent = verifyKeyExistEventMutable.asSharedFlow()

    //Register room
    private val registerRoomEventMutable = MutableSharedFlow<ProcessEvent<RoomData>>()
    val registerRoomEvent = registerRoomEventMutable.asSharedFlow()

    //Update room
    private val updateRoomEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val updateRoomEvent = updateRoomEventMutable.asSharedFlow()

    //Delete room
    private val deleteRoomEventMutable = MutableSharedFlow<ProcessEvent<RoomData?>>()
    val deleteRoomEvent = deleteRoomEventMutable.asSharedFlow()

    //Join the room
    private val joinRoomEventMutable = MutableSharedFlow<ProcessEvent<String>>()
    val joinRoomEvent = joinRoomEventMutable.asSharedFlow()

    //Join room admin approve
    private val joinRoomAdminApproveEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val joinRoomAdminApproveEvent = joinRoomAdminApproveEventMutable.asSharedFlow()

    //Leave room
    private val leaveRoomEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val leaveRoomEvent = leaveRoomEventMutable.asSharedFlow()

    //Leave room admin
    private val dropAdminEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val dropAdminEvent = dropAdminEventMutable.asSharedFlow()

    //Room users
    private val roomUsersMutable = MutableStateFlow(listOf<UserData>())
    val roomUsers = roomUsersMutable.asStateFlow()
    private val knownUserIds = mutableSetOf<String>() //dodane zeby unknown nie bylo

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()

    //Send Message
    private val sendMessageEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val sendMessageEvent = sendMessageEventMutable.asSharedFlow()

    //Recent
    private val recentsMutable = MutableStateFlow(listOf<Recent>())
    val recents = recentsMutable.asStateFlow()

    //Reconnect chat
    private var reconnectJob: Job? = null
    private var stopRealtimeFlag = false

    //Popup
    private val queuedPopupList = LinkedList<PopupContext>()
    private val selectedPopupMutable = MutableStateFlow<PopupContext?>(null)
    val selectedPopup = selectedPopupMutable.asStateFlow()



    //TODO dziala ok - ujednolicielm UserData
    fun logInUser(login: String, password: String) {
        viewModelScope.launch {
            try {
                val userData = repository.loginUser(login, password)

                selectedUserMutable.value = userData
                selectedUserEventMutable.emit(ProcessEvent.Success(userData))
                //rozpoczęcie globalnego pollingu sprawdzania haseł(dołączenie do pokoju przez hasło)
                startGlobalPasswordCheckPolling()
            } catch (e: Exception) {
                Log.e("LoginError", "Failed to log in", e)
                selectedUserEventMutable.emit(ProcessEvent.Error("Login failed. Please try again."))
            }
        }
    }
    //TODO tez ok dziala
    fun registerUser(login: String, password: String){
        viewModelScope.launch {
            // TODO Call asynchronous function to register user. //DONE
            val status : Boolean = repository.registerUser(login, password)
            if (status){
                welcomeStateMutable.value = true
                registerUserEventMutable.emit(ProcessEvent.Success(Unit))
            }
            else {
                registerUserEventMutable.emit(ProcessEvent.Error("Failed to create account. Please try again."))
            }
        }
    }

    //tutaj czysci po prostu token = wylogowuje
    fun logOutUser(){ //wylogowuje nawet jak coś poszło nie tak z internetem/serwerem
        viewModelScope.launch{
            val user = selectedUser.value
            var status : Boolean = false
            if (user != null) {
                status = repository.logOutUser() //tu po prostu czyszcze token przez co juz nic nie dostanie od serwera
            }
            stopGlobalPasswordCheckPolling() //zatrzymuje globalny polling
            selectedUserMutable.value = null
            clearAppState()
            if (status == true){
                selectedUserEventMutable.emit(ProcessEvent.Success(null))
            }
            else {
                selectedUserEventMutable.emit(ProcessEvent.Error("Something went wrong while logging out."))
            }
        }
    }
    fun clearAppState(){
        selectedUserMutable.value = null
        welcomeStateMutable.value = false
        myRoomsMutable.value = listOf<RoomData>()
        discoverRoomsMutable.value = listOf<RoomData>()
        searchMyRoomsTextMutable.value =""
        searchDiscoverTextMutable.value =""
        selectedRoomMutable.value = null
        roomUsersMutable.value = listOf<UserData>()
        messagesMutable.value = listOf<Message>()
        recentsMutable.value = listOf<Recent>()
        selectedPopupMutable.value = null
        clearQueuedPopups()
        stopRealtime()
        knownUserIds.clear()
        // Zatrzymanie wszystkich polling'ów
        stopJoinRequestPolling()
        stopPendingRequestsPolling()
        stopWaitingForKeyPolling()
        stopPasswordVerificationPolling()
        stopGlobalPasswordCheckPolling()
        handledPasswordChecks.clear()
        Log.d("POLLING", "Wyczyszczono wszystkie klucze weryfikacji przy wylogowaniu")

    }
//    // TODO tutaj chyba jakas oblusge/pola do additionalSettings
    fun updateUser(userName: String, currentPassword: String, newPassword: String, passwordConfirmation: String, avatar: String, additionalSettings: String){
        viewModelScope.launch {
            if (!validateUpdateUser(userName, currentPassword, newPassword, passwordConfirmation, avatar, additionalSettings)) {
                updateUserEventMutable.emit(ProcessEvent.Error("Failed to update account. Please try again."))
                return@launch
            }

            val currentUser = selectedUser.value
            if (currentUser == null) {
                updateUserEventMutable.emit(ProcessEvent.Error("No user logged in."))
                return@launch
            }
            try {

                val userData = UserData(
                    id = currentUser.id,
                    login = currentUser.login,
                    name = if (userName.isNotBlank()) userName else currentUser.name,
                    avatar = if (avatar.isNotBlank()) avatar else currentUser.avatar,
                    publicKey = currentUser.publicKey,
                    additionalSettings = additionalSettings
                )

                val result = repository.updateUser(userData, currentPassword, newPassword)

                if (result) {
                    // update lokalnego usera w stanie UI
                    selectedUserMutable.value = currentUser.copy(
                        name = userData.name,
                        avatar = userData.avatar,
                        additionalSettings = userData.additionalSettings
                    )
                    //selectedUserMutable.value = currentUser.copy(name = userName, avatar = avatar, additionalSettings = additionalSettings)
                    updateUserEventMutable.emit(ProcessEvent.Success(Unit))
                } else {
                    updateUserEventMutable.emit(ProcessEvent.Error("Update failed."))
                }
            } catch (e: Exception) {
                Log.e("UpdateUser", "Update failed", e)
                updateUserEventMutable.emit(ProcessEvent.Error("Update failed: ${e.message}"))
            }
        }
    }


    fun validateUpdateUser(userName: String, currentPassword: String, newPassword: String, passwordConfirmation: String, avatar: String, additionalSettings: String): Boolean {
        val user = selectedUser.value
        if (user == null) {
            return false
        }
        val userNameChanged = userName != user.name
        val avatarChanged = avatar != user.avatar
        val additionalSettingsChanged = additionalSettings != user.additionalSettings
        val passwordChanged = newPassword.isNotBlank() || passwordConfirmation.isNotBlank()
        if (!userNameChanged && !avatarChanged && !passwordChanged && !additionalSettingsChanged) {
            return false
        }
        if (userNameChanged && userName.isBlank()) {
            return false
        }
        if (passwordChanged) {
            if (currentPassword.isBlank()) {
                return false
            }
            if (validatePassword(newPassword, passwordConfirmation) != PasswordValidationResult.CORRECT) {
                return false
            }
        }
        return true
    }
    fun deleteUser(password: String){
        viewModelScope.launch {
            val user = selectedUser.value
            if (user == null) {
                deleteUserEventMutable.emit(ProcessEvent.Error("No user logged in."))
                return@launch
            }
            val status = repository.deleteUser(password)

            if (status) {
                selectedUserMutable.value = null
                clearAppState()
                deleteUserEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                deleteUserEventMutable.emit(ProcessEvent.Error("Something went wrong while deleting account."))
            }
        }
    }
    fun resetWelcomeState(){
        welcomeStateMutable.value = false
    }


    /**
     * Ładuje pokoje użytkownika z serwera
     *
     * Po załadowaniu pokojów uruchamia globalny polling sprawdzania haseł
     * jeśli użytkownik jest zalogowany
     */
    fun loadMyRooms() {
        viewModelScope.launch {
            if (::roomRepository.isInitialized) {
                Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("ROOM", "Ładuję moje pokoje...")

                val roomsFromApi = roomRepository.getMyRooms()

                Log.d("ROOM", "Otrzymałem ${roomsFromApi.size} pokoi z serwera:")
                roomsFromApi.forEach { room ->
                    Log.d("ROOM", "  ${room.name}")
                    Log.d("ROOM", "     ID: ${room.idRoom}")
                    Log.d("ROOM", "     Private: ${room.isPrivate}")
                    Log.d("ROOM", "     Admin: ${room.idAdmin}")
                }
                Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                myRoomsMutable.value = roomsFromApi

                if (selectedUser.value != null && passwordCheckPollingJob?.isActive != true) {
                    startGlobalPasswordCheckPolling()
                }
            } else {
                Log.e("loadMyRooms", "RoomRepository nie jest zainicjalizowany!")
            }
        }
    }

    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            if (::roomRepository.isInitialized) {
                val allRooms = roomRepository.getAllRooms()
                val myRooms = roomRepository.getMyRooms()
                val myRoomIds = myRooms.map { it.idRoom }.toSet()
                val discoverRooms = allRooms.filter { it.idRoom !in myRoomIds }
                //sprawdzenie czemu nie pokazuje niewidocznych pokoi
                Log.d("NearNetVM", "All rooms from server")
                allRooms.forEach { room ->
                    Log.d(
                        "Rooms",
                        "Room: name='${room.name}', id='${room.idRoom}', isVisible=${room.isVisible}, isPrivate=${room.isPrivate}, idAdmin=${room.idAdmin}"
                    )
                }
                Log.d("NearNetVM", "Discover rooms")
                discoverRooms.forEach { room ->
                    Log.d(
                        "Rooms",
                        "Discover Room: name='${room.name}', id='${room.idRoom}', isVisible=${room.isVisible}, isPrivate=${room.isPrivate}, idAdmin=${room.idAdmin}"
                    )
                }
                discoverRoomsMutable.value = discoverRooms
            } else {
                Log.e("loadDiscoverRooms", "RoomRepository is not initialized!")
            }
        }
    }

    fun createRoom(
        name: String,
        description: String,
        avatar: String,
        password: String?,
        passwordConfirmation: String?,
        isPrivate: Boolean,
        isVisible: Boolean,
        additionalSettings: String = "",
    ) {
        viewModelScope.launch {
            if (!validateRoom(name, description, password, passwordConfirmation, avatar, isPrivate, isVisible, additionalSettings, false)) {
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                return@launch
            }

            if (::roomRepository.isInitialized) {
                try {
                    // jeśli pokój jest publiczny, hasło będzie puste
                    val createdRoomData = roomRepository.addRoom(
                        name = name,
                        description = description,
                        password = password ?: "",
                        isPrivate = isPrivate,
                        isVisible = isVisible,
                        additionalSettings = additionalSettings,
                        avatar = avatar
                    )

                    if (createdRoomData != null) {
                        // Emitujemy bezpośrednio RoomData
                        registerRoomEventMutable.emit(ProcessEvent.Success(createdRoomData))
                    } else {
                        Log.e("createRoom", "Nie udało się utworzyć pokoju na serwerze")
                        registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                    }

                } catch (e: Exception) {
                    Log.e("createRoom", "Błąd podczas tworzenia pokoju", e)
                    registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                }

            } else {
                Log.e("createRoom", "RoomRepository nie jest zainicjalizowane!")
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
            }
        }
    }

    fun updateRoom(
        name: String,
        description: String,
        avatar: String,
        password: String?,
        passwordConfirmation: String?,
        isPrivate: Boolean,
        isVisible: Boolean,
        additionalSettings: String
    ) {
        viewModelScope.launch {

            if (!validateRoom(name, description, password, passwordConfirmation, avatar, isPrivate, isVisible, additionalSettings, true)) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
                return@launch
            }

            val currentRoom = selectedRoom.value
            if (currentRoom == null) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
                return@launch
            }

            val updatedRoomData = currentRoom.copy(
                name = name.trim(),
                description = description.trim(),
                avatar = avatar,
                password = password ?: currentRoom.password,
                isPrivate = isPrivate,
                isVisible = isVisible,
                additionalSettings = additionalSettings
            )

            val result = roomRepository.updateRoom(updatedRoomData)

            if (result != null) {
                selectedRoomMutable.value = result
                updateRoomEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
            }
        }
    }

    fun updateRoomAdmin(idAdmin: String){
        viewModelScope.launch {
            val room = selectedRoom.value
            val result = roomRepository.updateRoomAdmin(room!!.idRoom)
            if (result) {

                // Zaktualizuj lokalny stan pokoju
                val updatedRoom = room.copy(idAdmin = idAdmin)
                selectedRoomMutable.value = updatedRoom

                // Zaktualizuj listę pokoi
                val updatedRooms = myRooms.value.map { r ->
                    if (r.idRoom == room.idRoom) updatedRoom else r
                }
                myRoomsMutable.value = updatedRooms

                updateRoomEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
            }
        }
    }

    fun validateRoom(name: String, description: String, password: String?, passwordConfirmation: String?, avatar: String, isPrivate: Boolean, isVisible: Boolean, additionalSettings: String, update: Boolean) : Boolean {
        var nameChanged = true
        var descriptionChanged = true
        var passwordChanged = true
        var avatarChanged = true
        var isPrivateChanged = true
        var isVisibleChanged = true
        var additionalSettingsChanged = true
        if (update) {
            val room = selectedRoom.value
            if (room == null) {
                return false
            }
            nameChanged = name != room.name
            descriptionChanged = description != room.description
            passwordChanged = password != null && password.isNotEmpty()
            avatarChanged = avatar != room.avatar
            isPrivateChanged = isPrivate != room.isPrivate
            isVisibleChanged = isVisible != room.isVisible
            additionalSettingsChanged = additionalSettings != room.additionalSettings
            if (!nameChanged && !descriptionChanged && !passwordChanged && !avatarChanged && !isPrivateChanged && !isVisibleChanged && !additionalSettingsChanged) {
                return false
            }
        }
        if (nameChanged && name.isBlank()) {
            return false
        }
        if (nameChanged && name.length > ROOM_NAME_MAX_LENGTH) {
            return false
        }
        if (descriptionChanged && description.length > ROOM_DESCRIPTION_MAX_LENGTH) {
            return false
        }
        if (passwordChanged && password != null) {
            if (password.isBlank()) {
                return false
            }
            if (password != passwordConfirmation) {
                return false
            }
        }
        return true
    }
    fun deleteRoom(room: RoomData?) {
        viewModelScope.launch {
            val selectedRoom = selectedRoom.value
            if (selectedRoom == null) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to delete room. Please try again."))
                return@launch
            }
            //TODO Call asynchronous function to delete room, when user is its admin.
            val status = roomRepository.deleteRoom(selectedRoom.idRoom)

            if (status) {
                selectedRoomMutable.value = null
                deleteRoomEventMutable.emit(ProcessEvent.Success(null))
            } else {
                deleteRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while deleting the room."))
            }
        }
    }

    /**
     * Dołącza do pokoju
     *
     * Proces zależy od typu pokoju:
     * - PUBLICZNY: Bezpośrednie dołączenie
     * - PRYWATNY Z HASŁEM: Weryfikacja hasła (polling)
     * - PRYWATNY BEZ HASŁA: Prośba do admina (polling)
     *
     * @param room Pokój do którego użytkownik chce dołączyć
     * @param password Hasło pokoju (jeśli prywatny)
     */
    fun joinRoom(room: RoomData, password: String) {
        viewModelScope.launch {
            try {
                Log.d("VIEWMODEL", "Próba dołączenia do pokoju: ${room.name}")

                if (!::roomRepository.isInitialized) {
                    Log.e("VIEWMODEL", "RoomRepository nie jest zainicjalizowany!")
                    joinRoomEventMutable.emit(ProcessEvent.Error("Internal error"))
                    return@launch
                }

                // POKÓJ PUBLICZNY - standardowe dołączenie
                if (!room.isPrivate) {
                    val joinSuccess = roomRepository.addMyselfToRoom(room.idRoom, "")
                    if (joinSuccess) {
                        Log.d("VIEWMODEL", "Dołączono do pokoju publicznego")
                        joinRoomEventMutable.emit(ProcessEvent.Success("You have joined the room ${room.name}!"))
                    } else {
                        joinRoomEventMutable.emit(ProcessEvent.Error("Failed to join room"))
                    }
                    return@launch
                }

                // POKÓJ PRYWATNY Z HASŁEM - weryfikacja automatyczna
                Log.d("VIEWMODEL", "Pokój prywatny - wysyłam żądanie weryfikacji hasła...")

                // Wyślij prośbę o weryfikację hasła
                val requestSent = roomRepository.requestJoinByPassword(room.idRoom)

                if (!requestSent) {
                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request"))
                    return@launch
                }

                Log.d("VIEWMODEL", "Żądanie wysłane, uruchamiam polling weryfikacji hasła...")

                // Rozpocznij polling weryfikacji hasła
                startPasswordVerificationPolling(room, password)

                joinRoomEventMutable.emit(ProcessEvent.Success("Verifying password. This may take some time."))

            } catch (e: Exception) {
                Log.e("VIEWMODEL", "Wyjątek podczas dołączania do pokoju", e)
                joinRoomEventMutable.emit(ProcessEvent.Error("Unexpected error while joining the room."))
            }
        }
    }

    // ============================================================================
    // POLLING #1: PROŚBY O DOŁĄCZENIE DO POKOJU (akceptacja admina)
    // ============================================================================

    private var joinRequestPollingJob: Job? = null
    /**
     * Wysyła prośbę do admina o dołączenie do pokoju prywatnego
     *
     * Po wysłaniu prośby uruchamia polling oczekujący na decyzję admina
     * Polling sprawdza status co 5 sekund przez maksymalnie 10 minut
     *
     * @param room Pokój prywatny do którego użytkownik chce dołączyć
     */
    fun joinRoomRequest(room: RoomData) {
        viewModelScope.launch {
            if (!room.isPrivate) {
                joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request — the room is public."))
                return@launch
            }
            //TODO Marek funkcja wysyłająca prośbę do Admina
            val requestSuccess = roomRepository.sendJoinRequest(roomId = room.idRoom)

            if (!requestSuccess) {
                joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request — please try again."))
                return@launch
            }

            joinRoomEventMutable.emit(ProcessEvent.Success("Keep your fingers crossed for approval!"))
            Log.d("VIEWMODEL", "Prośba wysłana, oczekuję na decyzję admina...")

            // Uruchom polling oczekiwania na decyzję admina
            startJoinRequestPolling(room)

    }}


    /**
     * Polling oczekiwania na decyzję admina
     *
     * Sprawdza status prośby co 5 sekund przez maksymalnie 10 minut
     *
     * Możliwe statusy:
     * - "pending": Nadal oczekuje na decyzję
     * - "accepted": Zaakceptowano - pobiera klucz pokoju
     * - "rejected": Odrzucono - kończy polling
     * - "inRoom": Już jest członkiem - kończy polling
     *
     * @param room Pokój którego dotyczy prośba
     */
    private fun startJoinRequestPolling(room: RoomData) {
        stopJoinRequestPolling() // Zatrzymaj ewentualny poprzedni polling

        joinRequestPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut sprawdzania (co 5 sekund)

            Log.d("POLLING", "Rozpoczynam sprawdzanie statusu prośby dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(5000) // Sprawdzaj co 5 sekund

                try {
                    // Sprawdź status prośby
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        Log.w("POLLING", "Nie można sprawdzić statusu (attempt ${attempts + 1})")
                        attempts++
                        continue
                    }

                    Log.d("POLLING", "Status prośby: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {
                        "accepted" -> {
                            Log.d("POLLING", "Prośba zaakceptowana!")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                Log.d("POLLING", "  Otrzymano zaszyfrowany klucz, rozpoczynam deszyfrowanie...")

                                // Użyj otrzymanego klucza
                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey
                                )

                                if (keyFetched) {
                                    Log.d("POLLING", "  Klucz odszyfrowany i zapisany pomyślnie!")

                                } else {
                                    Log.e("POLLING", "  Nie udało się odszyfrować klucza")
                                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to decrypt room key"))
                                }
                            } else {
                                Log.w("POLLING", "  Zaakceptowano, ale brak klucza (pokój publiczny?)")
                            }

                            // Zakończ polling
                            break
                        }

                        "rejected" -> {
                            Log.d("POLLING", "Prośba odrzucona przez admina")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Your request was rejected by the admin"))
                            break
                        }

                        "pending" -> {
                            // Kontynuuj oczekiwanie
                            Log.d("POLLING", "Nadal oczekuje na decyzję admina...")
                        }

                        "inRoom" -> {
                            Log.d("POLLING", "Już jesteś członkiem pokoju")
                            break
                        }

                        else -> {
                            Log.w("POLLING", "Nieznany status: ${requestStatus.status}")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("POLLING", "Błąd sprawdzania statusu prośby", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("POLLING", "Przekroczono limit czasu oczekiwania na odpowiedź admina")
                joinRoomEventMutable.emit(ProcessEvent.Error("Admin hasn't responded yet. Please try again later."))
            }

            joinRequestPollingJob = null
        }
    }

    /**
     * Zatrzymuje polling oczekiwania na decyzję admina
     */
    fun stopJoinRequestPolling() {
        joinRequestPollingJob?.cancel()
        joinRequestPollingJob = null
        Log.d("POLLING", "Zatrzymano sprawdzanie statusu prośby")
    }

    // ============================================================================
    // POLLING #2: WERYFIKACJA HASŁA
    // ============================================================================


    private var passwordVerificationPollingJob: Job? = null

    /**
     * Polling weryfikacji hasła pokoju prywatnego
     *
     * Sprawdza status weryfikacji co 3 sekundy przez maksymalnie 10 minut
     *
     * Przepływ:
     * 1. Użytkownik wysyła żądanie z hasłem
     * 2. Inny członek pokoju deklaruje sprawdzenie (status: "declaredPasswordCheck")
     * 3. Użytkownik wysyła zaszyfrowane hasło do weryfikatora
     * 4. Weryfikator sprawdza hasło i odpowiada (status: "accepted" lub "rejected")
     * 5. Jeśli accepted - pobiera klucz pokoju
     *
     * @param room Pokój do którego użytkownik dołącza
     * @param password Hasło podane przez użytkownika
     */
    private fun startPasswordVerificationPolling(room: RoomData, password: String) {
        stopPasswordVerificationPolling()

        passwordVerificationPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut

            Log.d("POLLING", "Rozpoczynam weryfikację hasła dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(3000) // Co 3 sekundy

                try {
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        attempts++
                        continue
                    }

                    Log.d("POLLING", "Status weryfikacji: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {

                        "declaredPasswordCheck" -> {
                            // Ktoś zadeklarował sprawdzenie - wyślij zaszyfrowane hasło
                            Log.d("POLLING", "Weryfikator gotowy - wysyłam zaszyfrowane hasło")

                            val checkerId = requestStatus.encryptedRoomKey // ID weryfikatora

                            Log.d("POLLING", "CheckerId: $checkerId")

                            if (checkerId.isNullOrEmpty()) {
                                Log.e("POLLING", "CheckerId jest pusty!")
                                continue
                            }

                            Log.d("POLLING", "CheckerId OK: $checkerId")

                            val context = contextProvider?.invoke()
                            if (context == null) {
                                Log.e("POLLING", "Context niedostępny")
                                continue
                            }

                            Log.d("POLLING", "Context OK")
                            // Pobierz klucz publiczny weryfikatora
                            val checkerPublicKey = PublicKeyManager(context).getPublicKeyForUser(checkerId)

                            Log.d("POLLING", "PublicKey dla $checkerId: ${if (checkerPublicKey != null) "FOUND" else "NULL"}")

                            if (checkerPublicKey != null) {
                                Log.d("POLLING", "Klucz publiczny weryfikatora pobrany")

                                try {
                                    // Zaszyfruj hasło kluczem publicznym weryfikatora
                                    val encryptedPassword = CryptoUtils.encryptStringWithRSA(password, checkerPublicKey)

                                    Log.d("POLLING", "Hasło zaszyfrowane")
                                    Log.d("POLLING", "Encrypted password (50 chars): ${encryptedPassword.take(50)}")
                                    // Wyślij zaszyfrowane hasło
                                    val sent = roomRepository.sendEncryptedPassword(room.idRoom, encryptedPassword)

                                    if (sent) {
                                        Log.d("POLLING", "Zaszyfrowane hasło wysłane pomyślnie")
                                    } else {
                                        Log.e("POLLING", "Nie udało się wysłać zaszyfrowanego hasła")
                                    }
                                } catch (e: Exception) {
                                    Log.e("POLLING", "Błąd szyfrowania hasła", e)
                                }
                            } else {
                                Log.e("POLLING", "Nie można pobrać klucza publicznego weryfikatora")
                            }
                        }

                        "accepted" -> {
                            Log.d("POLLING", "Hasło zweryfikowane! Pobieram dane pokoju...")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                keysBeingSaved.add(room.idRoom)

                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey
                                )

                                keysBeingSaved.remove(room.idRoom)

                                if (keyFetched) {
                                    Log.d("POLLING", "Dane pokoju zapisane lokalnie!")
                                }
                            }

                            break
                        }

                        "rejected" -> {
                            Log.d("POLLING", "Niepoprawne hasło")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Incorrect password"))
                            break
                        }

                        "requestJoin" -> {
                            Log.d("POLLING", "Czekam na weryfikatora...")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("POLLING", "Błąd weryfikacji hasła", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("POLLING", "Timeout weryfikacji hasła")
            }

            passwordVerificationPollingJob = null
        }
    }

    /**
     * Zatrzymuje polling weryfikacji hasła
     */
    fun stopPasswordVerificationPolling() {
        passwordVerificationPollingJob?.cancel()
        passwordVerificationPollingJob = null
        Log.d("POLLING", "Zatrzymano weryfikację hasła")
    }

    // ============================================================================
    // POLLING #3: GLOBALNY POLLING SPRAWDZANIA HASEŁ
    // ============================================================================

    private var passwordCheckPollingJob: Job? = null
    private val handledPasswordChecks = mutableSetOf<String>()

    /**
     * Usuwa wszystkie wpisy z handledPasswordChecks związane z danym użytkownikiem w danym pokoju
     *
     * Używane gdy:
     * - Użytkownik opuszcza pokój
     * - Admin usuwa użytkownika z pokoju
     *
     * @param userId ID użytkownika
     * @param roomId ID pokoju
     */
    private fun cleanupHandledPasswordChecksForUser(userId: String, roomId: String) {
        val keysToRemove = mutableListOf<String>()

        // Wszystkie możliwe statusy
        val statuses = listOf(
            "requestJoin",
            "declaredPasswordCheck",
            "passwordReadyToCheck",
            "waitingForKey"
        )

        statuses.forEach { status ->
            val key = "$userId-$roomId-$status"
            if (handledPasswordChecks.contains(key)) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { key ->
            handledPasswordChecks.remove(key)
        }

        if (keysToRemove.isNotEmpty()) {
            Log.d("POLLING", "Wyczyszczono ${keysToRemove.size} kluczy dla użytkownika $userId w pokoju $roomId")
            Log.d("POLLING", "Wyczyszczono ${keysToRemove.size} kluczy dla użytkownika $userId w pokoju $roomId: $keysToRemove")
        }
    }

    /**
     * Globalny polling sprawdzający prośby o weryfikację haseł we WSZYSTKICH pokojach użytkownika
     *
     * Uruchamiany automatycznie po zalogowaniu
     * Sprawdza wszystkie prywatne pokoje użytkownika co 3 sekundy
     *
     * Dla każdego użytkownika czekającego:
     * 1. "requestJoin" → Automatycznie deklaruje sprawdzenie hasła
     * 2. "passwordReadyToCheck" → Automatycznie weryfikuje hasło
     * 3. "waitingForKey" → Automatycznie wysyła klucz pokoju
     *
     * Mechanizm deduplikacji: handledPasswordChecks zapobiega wielokrotnemu przetwarzaniu tego samego requestu
     */

    fun startGlobalPasswordCheckPolling() {
        stopGlobalPasswordCheckPolling()

        passwordCheckPollingJob = viewModelScope.launch {
            Log.d("POLLING", "Rozpoczynam globalny polling sprawdzania haseł")

            while (isActive) {
                try {
                    val myRoomsList = myRooms.value

                    //Log.d("POLLING", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    //Log.d("POLLING", "Globalny polling - sprawdzam ${myRoomsList.size} pokoi")

                    val privateRooms = myRoomsList.filter { it.isPrivate }
                    Log.d("ROOM", " Prywatnych pokoi do sprawdzenia: ${privateRooms.size}")

                    privateRooms.forEach { room ->
                        //Log.d("POLLING", " Checking room: ${room.name} (${room.idRoom})")
                    }

                    privateRooms.forEach { room ->
                        try {
                            Log.d("POLLING", " [${room.name}] Pobieram statusy użytkowników...")

                            val usersWaiting = roomRepository.getRoomUsersStatus(room.idRoom)

                            Log.d("POLLING", " [${room.name}] Znaleziono ${usersWaiting.size} użytkowników czekających")

                            usersWaiting.forEach { userStatus ->
                                Log.d("POLLING", "     User: ${userStatus.userId}")
                                Log.d("POLLING", "     Status: ${userStatus.status}")
                                Log.d("POLLING", "     EncryptedRoomKey: ${userStatus.encryptedRoomKey?.take(20) ?: "null"}")

                                if (userStatus.status == "declaredPasswordCheck") {
                                    val requestTime = try {
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                                            .parse(userStatus.requestedAt ?: "")?.time ?: 0L
                                    } catch (e: Exception) {
                                        Log.e("POLLING-DEBUG", "Błąd parsowania: ${e.message}")
                                        0L
                                    }

                                    Log.d("POLLING-DEBUG", "Parsed time: $requestTime")
                                    Log.d("POLLING-DEBUG", "Current time: ${System.currentTimeMillis()}")

                                    val now = System.currentTimeMillis()
                                    val timeoutMs = 30000 // 30 sekund

                                    if (requestTime > 0 && now - requestTime > timeoutMs) {
                                        Log.d("POLLING", "[${room.name}] Timeout - resetuję")

                                        launch {
                                            val reset = roomRepository.resetPasswordCheckTimeout(room.idRoom, userStatus.userId)
                                            if (reset) {
                                                Log.d("POLLING", "[${room.name}] Status zresetowany do requestJoin")
                                                val oldDeclaredKey = "${userStatus.userId}-${room.idRoom}-declaredPasswordCheck"
                                                val oldRequestKey = "${userStatus.userId}-${room.idRoom}-requestJoin"

                                                handledPasswordChecks.remove(oldDeclaredKey)
                                                handledPasswordChecks.remove(oldRequestKey)
                                            } else {
                                                Log.e("POLLING", "[${room.name}] Nie udało się zresetować statusu")
                                            }
                                        }
                                        return@forEach
                                    }
                                }

                                val key = "${userStatus.userId}-${room.idRoom}-${userStatus.status}"

                                // Sprawdź czy już obsłużyliśmy
                                if (handledPasswordChecks.contains(key)) {
                                    Log.d("POLLING", " Już obsłużone - pomijam")
                                    return@forEach
                                }

                                Log.d("POLLING", "Nowy request - obsługuję!")

                                when (userStatus.status) {
                                    "requestJoin" -> {
                                        Log.d("POLLING", "[${room.name}] Nowy użytkownik ${userStatus.userId} czeka - deklaruję sprawdzenie")

                                        launch {
                                            val declared = roomRepository.declarePasswordCheck(room.idRoom, userStatus.userId)

                                            if (declared) {
                                                Log.d("POLLING", "[${room.name}] Zadeklarowano sprawdzenie hasła")
                                                //handledPasswordChecks.add(key)  //
                                            } else {
                                                Log.e("POLLING", "[${room.name}] Nie udało się zadeklarować sprawdzenia - NIE dodaję do handled (retry możliwy)")
                                            }
                                        }
                                    }

                                    "passwordReadyToCheck" -> {
                                        // Użytkownik wysłał zaszyfrowane hasło
                                        Log.d("POLLING", "[${room.name}] Otrzymano zaszyfrowane hasło od ${userStatus.userId} - sprawdzam")

                                        //handledPasswordChecks.add(key)

                                        launch {
                                            try {
                                                verifyUserPassword(room, userStatus)
                                            } catch (e: Exception) {
                                                Log.e("POLLING", "[${room.name}] Błąd weryfikacji - usuwam klucz z handled", e)
                                                //handledPasswordChecks.remove(key)
                                            }
                                        }
                                    }

                                    "waitingForKey" -> {
                                        // Użytkownik potrzebuje klucza - AUTOMATYCZNIE wyślij
                                        Log.d("POLLING", "[${room.name}] Użytkownik ${userStatus.userId} potrzebuje klucza")

                                        handledPasswordChecks.add(key)

                                        launch {
                                            sendKeyToUser(room, userStatus.userId)
                                        }
                                    }

                                    else -> {
                                        Log.d("POLLING", "  Status ${userStatus.status} - ignoruję")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("POLLING", "Błąd sprawdzania pokoju ${room.name}", e)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("POLLING", "Błąd globalnego sprawdzania haseł", e)
                }

                delay(3000) // Co 3 sekundy
            }
        }
    }

    /**
     * Zatrzymuje globalny polling sprawdzania haseł
     *
     * Wywoływane przy wylogowaniu
     */
    fun stopGlobalPasswordCheckPolling() {
        passwordCheckPollingJob?.cancel()
        passwordCheckPollingJob = null
        handledPasswordChecks.clear()
        Log.d("POLLING", "Zatrzymano globalny polling sprawdzania haseł")
    }

    /**
     * Wysyła klucz AES pokoju do użytkownika
     *
     * Używane gdy użytkownik utracił klucz (zmiana urządzenia)
     *
     * Proces:
     * 1. Pobiera klucz AES pokoju i hasło z lokalnego storage
     * 2. Pobiera klucz publiczny RSA użytkownika docelowego
     * 3. Szyfruje klucz AES i hasło kluczem publicznym użytkownika
     * 4. Pakuje do JSON i wysyła na serwer
     *
     * @param room Pokój którego klucz wysyłamy
     * @param targetUserId ID użytkownika który potrzebuje klucza
     */
    private suspend fun sendKeyToUser(room: RoomData, targetUserId: String) {
        try {

            Log.d("POLLING", " WYSYŁAM KLUCZ - START")
            Log.d("POLLING", " Room: ${room.name}")
            Log.d("POLLING", " Target user: $targetUserId")

            val context = contextProvider?.invoke()
            if (context == null) {
                Log.e("POLLING", "Context niedostępny")
                return
            }

            // Pobierz klucz AES pokoju
            val roomAESKeyBase64 = roomRepository.getRoomAESKey(room.idRoom)
            if (roomAESKeyBase64 == null) {
                Log.e("POLLING", "Nie mam klucza AES pokoju!")
                return
            }

            Log.d("ROOM", "✓ Klucz AES pokoju pobrany")

            // Pobierz hasło pokoju (jeśli jest)
            val roomPassword = roomRepository.getRoomPassword(room.idRoom) ?: ""

            if (roomPassword.isNotEmpty()) {
                Log.d("POLLING", "Hasło pokoju pobrane")
            }

            val roomAESKey = CryptoUtils.stringToAESKey(roomAESKeyBase64)

            // Pobierz PublicKey użytkownika
            val targetPublicKey = PublicKeyManager(context).getPublicKeyForUser(targetUserId)
            if (targetPublicKey == null) {
                Log.e("POLLING", "Nie można pobrać PublicKey użytkownika $targetUserId")
                return
            }

            Log.d("POLLING", "PublicKey użytkownika pobrany")

            // Zaszyfruj klucz AES
            Log.d("POLLING", "Szyfruję klucz AES...")
            val encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, targetPublicKey)

            Log.d("POLLING", "Klucz AES zaszyfrowany")

            // Zaszyfruj hasło
            Log.d("POLLING", "Szyfruję hasło pokoju...")
            val encryptedPassword = CryptoUtils.encryptStringWithRSA(roomPassword, targetPublicKey)

            Log.d("POLLING", "Hasło pokoju zaszyfrowane")

            // Stwórz JSON
            val jsonData = JSONObject().apply {
                put("encryptedAESKey", encryptedAESKey)
                put("encryptedPassword", encryptedPassword)
            }

            val jsonString = jsonData.toString()

            Log.d("POLLING", "JSON utworzony")
            Log.d("POLLING", "Wysyłam klucz do użytkownika...")

            val sent = roomRepository.sendRoomKeyToUser(room.idRoom, targetUserId, jsonString)

            if (sent) {
                Log.d("POLLING", "Klucz wysłany POMYŚLNIE!")
            } else {
                Log.e("POLLING", "Nie udało się wysłać klucza")
            }

        } catch (e: Exception) {
            Log.e("POLLING", "BŁĄD wysyłania klucza", e)
        }

        Log.d("ROOM", "WYSYŁAM KLUCZ - KONIEC")
    }


    /**
     * Weryfikuje hasło użytkownika w procesie dołączania do pokoju
     *
     * Proces:
     * 1. Odszyfrowuje hasło przesłane przez użytkownika (używając swojego klucza prywatnego)
     * 2. Porównuje z hasłem pokoju przechowywanym lokalnie
     * 3. Jeśli poprawne - szyfruje i wysyła klucz AES + hasło do użytkownika
     * 4. Jeśli niepoprawne - loguje informację (serwer automatycznie odrzuca)
     *
     * @param room Pokój którego dotyczy weryfikacja
     * @param userStatus Status użytkownika z zaszyfrowanym hasłem
     */
    private suspend fun verifyUserPassword(room: RoomData, userStatus: UserStatus) {

        Log.d("POLLING", "WERYFIKACJA HASŁA")
        Log.d("POLLING", "   Room: ${room.name} (${room.idRoom})")
        Log.d("POLLING", "   User: ${userStatus.userId}")

        try {
            val context = contextProvider?.invoke()
            if (context == null) {
                Log.e("POLLING", "Context niedostępny")
                return
            }

            // Pobierz swój klucz prywatny
            val myLogin = UserRepository.getLoginFromPreferences(context)
            if (myLogin == null) {
                Log.e("POLLING", "Nie można pobrać loginu")
                return
            }

            // Pobierz swój klucz prywatny
            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin)
            if (myPrivateKey == null) {
                Log.e("POLLING", "Nie można pobrać PrivateKey")
                return
            }

            // Odszyfruj hasło
            val encryptedPassword = userStatus.encryptedRoomKey
            if (encryptedPassword == null) {
                Log.e("POLLING", "Brak zaszyfrowanego hasła")
                return
            }

            val decryptedPassword = CryptoUtils.decryptStringWithRSA(encryptedPassword, myPrivateKey)

            Log.d("POLLING", "Hasło odszyfrowane: [${decryptedPassword.length} znaków]")

            // Pobierz hasło pokoju
            val roomPassword = roomRepository.getRoomPassword(room.idRoom)

            Log.d("POLLING", "Pobieram hasło pokoju...")

            if (roomPassword == null) {
                Log.e("POLLING", "Nie mam hasła pokoju - nie mogę zweryfikować")
                return
            }
            Log.d("POLLING", "Hasło pokoju pobrane")


            val isCorrect = (decryptedPassword == roomPassword)
            Log.d("POLLING", "Wynik porównania: $isCorrect")

            if (isCorrect) {
                Log.d("POLLING", "Wysyłam dane pokoju do użytkownika...")

                // Pobierz klucz AES pokoju
                val roomAESKeyBase64 = roomRepository.getRoomAESKey(room.idRoom)
                if (roomAESKeyBase64 == null) {
                    Log.e("POLLING", "Nie mam klucza pokoju!")
                    return
                }

                val roomAESKey = CryptoUtils.stringToAESKey(roomAESKeyBase64)

                // Pobierz klucz publiczny nowego użytkownika
                val targetPublicKey = PublicKeyManager(context).getPublicKeyForUser(userStatus.userId)
                if (targetPublicKey == null) {
                    Log.e("POLLING", "Nie można pobrać klucza publicznego użytkownika ${userStatus.userId}")
                    return
                }


                // Zaszyfruj klucz AES i haslo
                val encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, targetPublicKey)
                val encryptedPasswordToSend = CryptoUtils.encryptStringWithRSA(roomPassword, targetPublicKey)


                // Stwórz JSON
                val jsonData = JSONObject().apply {
                    put("encryptedAESKey", encryptedAESKey)
                    put("encryptedPassword", encryptedPasswordToSend)
                }

                val jsonString = jsonData.toString()

                // Wyślij JSON
                val sent = roomRepository.sendRoomKeyToUser(room.idRoom, userStatus.userId, jsonString)

                if (sent) {
                    Log.d("POLLING", "Dane pokoju wysłane POMYŚLNIE!")
                } else {
                    Log.e("POLLING", "Nie udało się wysłać danych")
                }
            } else {
                Log.d("POLLING", "Hasło NIEPOPRAWNE - odrzucam request")
                val rejected = roomRepository.rejectPassword(room.idRoom, userStatus.userId)
                if (rejected) {
                    Log.d("POLLING", "Request odrzucony - user dostanie błąd")
                } else {
                    Log.e("POLLING", "Nie udało się odrzucić requestu")
                }

            }

        } catch (e: Exception) {
            Log.e("POLLING", "BŁĄD weryfikacji hasła", e)
        }

        Log.d("POLLING", "WERYFIKACJA HASŁA - KONIEC")

    }

    // ============================================================================
    // POLLING #4: OCZEKIWANIE NA KLUCZ
    // ============================================================================

    private var waitingForKeyPollingJob: Job? = null

    /**
     * Polling oczekiwania na klucz pokoju
     *
     * Używany gdy użytkownik nie ma klucza lokalnie (np. nowe urządzenie)
     * Sprawdza co 5 sekund przez 10 minut czy inni członkowie pokoju wysłali klucz
     *
     * @param room Pokój którego klucza oczekujemy
     */
    private fun startWaitingForKeyPolling(room: RoomData) {
        stopWaitingForKeyPolling()

        waitingForKeyPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut

            Log.d("POLLING", "Czekam na klucz AES i hasło dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(5000) // Co 5 sekund

                try {
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        attempts++
                        continue
                    }

                    Log.d("POLLING", "  Status: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {
                        "accepted" -> {
                            Log.d("POLLING", "Otrzymano dane pokoju!")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                keysBeingSaved.add(room.idRoom)

                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey
                                )

                                keysBeingSaved.remove(room.idRoom)

                                if (keyFetched) {
                                    Log.d("POLLING", "Dane odszyfrowane i zapisane!")
                                } else {
                                    Log.e("POLLING", "Nie udało się odszyfrować")
                                }
                            } else {
                                Log.w("POLLING", "Status 'accepted' ale brak danych")
                            }

                            break
                        }

                        "rejected" -> {
                            Log.d("POLLING", "Prośba odrzucona przez admina")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Your request was rejected by the admin"))
                            break
                        }

                        "pending" -> {
                            Log.d("POLLING", "Nadal oczekuje na decyzję admina...")
                        }

                        "waitingForKey" -> {
                            Log.d("POLLING", "Czekam na klucz od innych użytkowników... (attempt ${attempts + 1}/$maxAttempts)")
                        }

                        "inRoom" -> {
                            Log.d("POLLING", "Już jesteś członkiem pokoju")
                            break
                        }

                        else -> {
                            Log.w("POLLING", "Nieznany status: ${requestStatus.status}")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("POLLING", "Błąd podczas oczekiwania", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("POLLING", "Timeout - nie otrzymano danych w ciągu 10 minut")
                joinRoomEventMutable.emit(ProcessEvent.Error("Admin hasn't responded yet. Please try again later."))
            }

            waitingForKeyPollingJob = null
        }
    }

    /**
     * Zatrzymuje polling oczekiwania na klucz
     */
    fun stopWaitingForKeyPolling() {
        waitingForKeyPollingJob?.cancel()
        waitingForKeyPollingJob = null
        Log.d("POLLING", "Zatrzymano oczekiwanie na klucz")
    }


    //woła się, gdy admin zatwierdzi dołączenie jakiegoś usera do pokoju
    //TODO ponawianie zrobić na serwerze jak admin nieaktywny w danym momencie, by jak wejdzie to zobaczył popup, że ktoś go pyta o dołączenie
    fun joinRoomAdminApprove(user: UserData, room: RoomData, accept: Boolean){ //jaki user i do jakiego pokoju chce dołączyć
        viewModelScope.launch {
            Log.d("ROOM", "=== Admin akceptuje prośbę ===")
            Log.d("ROOM", "  User ID: ${user.id}")
            Log.d("ROOM", "  User login: ${user.login}")
            Log.d("ROOM", "  Room ID: ${room.idRoom}")
            Log.d("ROOM", "  Accept: $accept")
            val approveSuccess = roomRepository.respondToJoinRequest(
                roomId = room.idRoom,
                userId = user.id,
                accept = accept
            )
            //TODO Marek funkcja dołączająca usera do pokoju
            if (approveSuccess){
                joinRoomAdminApproveEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                joinRoomAdminApproveEventMutable.emit(ProcessEvent.Error("Failed to send approve — please approve again."))
            }
        }
    }

    private var pendingRequestsJob: Job? = null
    private val handledRequests = mutableSetOf<String>()

    fun startPendingRequestsPolling(room: RoomData) {
        pendingRequestsJob?.cancel()

        pendingRequestsJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // GET /api/rooms/:id/requests
                    val requests = roomRepository.getPendingRequests(room.idRoom)

                    requests.forEach { user ->
                        val requestKey = "${user.id}-${room.idRoom}"
                        if (!handledRequests.contains(requestKey)) {
                            handledRequests.add(requestKey)
                            selectPopup(
                                PopupType.JOIN_ROOM_APPROVAL,
                                PopupContextApprovalData(user, room)
                            )
                        }
                    }

                } catch (e: Exception) {
                    Log.e("NearNetVM", "Failed to fetch pending requests", e)
                }

                delay(5000) // sprawdzaj co 5 sekund
            }
        }
    }

    fun stopPendingRequestsPolling() {
        pendingRequestsJob?.cancel()
        pendingRequestsJob = null
        handledRequests.clear()
    }



//    fun leaveRoom(){
//        viewModelScope.launch {
//            var isLeftRoom : Boolean = false
//            val room = selectedRoom.value!!
//            //TODO Call asynchronous function to user leave their room.
//            isLeftRoom = roomRepository.leaveRoom(room.idRoom)
//            if (isLeftRoom){
//                leaveRoomEventMutable.emit(ProcessEvent.Success(Unit))
//            } else { //błąd gdzieś i nie udało się
//                leaveRoomEventMutable.emit(ProcessEvent.Error("Failed to leave the room. Please try again."))
//            }
//        }
//    }

//    fun leaveRoom(){
//        viewModelScope.launch {
//            Log.d("LEAVE_ROOM_DEBUG", "=== leaveRoom() STARTED ===")
//
//            var isLeftRoom : Boolean = false
//            val room = selectedRoom.value!!
//            val userId = selectedUser.value?.id  // ← DODANE
//            Log.d("LEAVE_ROOM_DEBUG", "Room: ${room.name}, UserId: $userId")
//            //TODO Call asynchronous function to user leave their room.
//            isLeftRoom = roomRepository.leaveRoom(room.idRoom)
//            if (isLeftRoom){
//                //Wyczyść klucze weryfikacji dla tego użytkownika
//                if (userId != null) {
//                    cleanupHandledPasswordChecksForUser(userId, room.idRoom)
//                }
//
//                leaveRoomEventMutable.emit(ProcessEvent.Success(Unit))
//            } else { //błąd gdzieś i nie udało się
//                leaveRoomEventMutable.emit(ProcessEvent.Error("Failed to leave the room. Please try again."))
//            }
//        }
//    }

    fun leaveRoom(){
        viewModelScope.launch {
            Log.d("LEAVE_DEBUG", "====== LEAVE ROOM START ======")
            var isLeftRoom : Boolean = false
            val room = selectedRoom.value!!
            val userId = selectedUser.value?.id
            Log.d("LEAVE_DEBUG", "Room: ${room.name} (${room.idRoom})")
            Log.d("LEAVE_DEBUG", "UserId: $userId")

            //TODO Call asynchronous function to user leave their room.
            isLeftRoom = roomRepository.leaveRoom(room.idRoom)

            Log.d("LEAVE_DEBUG", "Left room: $isLeftRoom")

            if (isLeftRoom){
                //Wyczyść klucze weryfikacji dla tego użytkownika
                if (userId != null) {
                    Log.d("LEAVE_DEBUG", "Calling cleanup for userId=$userId, roomId=${room.idRoom}")
                    cleanupHandledPasswordChecksForUser(userId, room.idRoom)
                    Log.d("LEAVE_DEBUG", "Cleanup completed")
                } else {
                    Log.e("LEAVE_DEBUG", "userId is NULL - cannot cleanup!")
                }

                leaveRoomEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                Log.e("LEAVE_DEBUG", "Failed to leave room!")
                leaveRoomEventMutable.emit(ProcessEvent.Error("Failed to leave the room. Please try again."))
            }
        }
    }


    fun removeUserFromRoom(user: UserData, room: RoomData) {
        viewModelScope.launch {
            //TODO Call function to remove user from the room.
            val isUserRemoved = roomRepository.removeUserFromRoom(room.idRoom, user.id)
            if (isUserRemoved) {
                //Wyczyść klucze weryfikacji dla usuniętego użytkownika
                cleanupHandledPasswordChecksForUser(user.id, room.idRoom)

                roomUsersMutable.value = roomUsersMutable.value.filter { it.id != user.id }
            } else {
                leaveRoomEventMutable.emit(ProcessEvent.Error("Failed to remove the user from the room."))
            }
        }
    }

    fun dropAdmin() {
        viewModelScope.launch {
            Log.d("ViewModel", "dropAdmin called")

            val room = selectedRoom.value
            val user = selectedUser.value

            // Walidacja: pokój musi być wybrany
            if (room == null) {
                Log.e("ViewModel", "✗ No room selected")
                dropAdminEventMutable.emit(
                    ProcessEvent.Error("No room selected")
                )
                return@launch
            }

            // Walidacja: user musi być zalogowany
            if (user == null) {
                Log.e("ViewModel", "✗ No user logged in")
                dropAdminEventMutable.emit(
                    ProcessEvent.Error("Not logged in")
                )
                return@launch
            }

            // Walidacja: user MUSI być adminem
            if (room.idAdmin != user.id) {
                Log.e("ViewModel", "✗ User is not the admin")
                dropAdminEventMutable.emit(
                    ProcessEvent.Error("You are not the admin of this room")
                )
                return@launch
            }

            Log.d("ViewModel", "User ${user.id} is dropping admin status for room ${room.idRoom}")

            // Wywołaj API
            val result = roomRepository.dropAdmin(room.idRoom)

            if (result) {
                Log.d("ViewModel", "✓ Admin status dropped successfully")

                // Zaktualizuj lokalny stan pokoju
                val updatedRoom = room.copy(idAdmin = null)
                selectedRoomMutable.value = updatedRoom

                // Zaktualizuj listę pokoi
                val updatedRooms = myRooms.value.map { r ->
                    if (r.idRoom == room.idRoom) updatedRoom else r
                }
                myRoomsMutable.value = updatedRooms

                dropAdminEventMutable.emit(ProcessEvent.Success(Unit))

            } else {
                Log.e("ViewModel", "✗ Failed to drop admin status")
                dropAdminEventMutable.emit(
                    ProcessEvent.Error("Failed to leave the admin role. Please try again.")
                )
            }
        }
    }

    fun filterMyRooms(filterText: String){
        searchMyRoomsTextMutable.value = filterText
    }
    fun filterDiscoverRooms(filterText: String){
        searchDiscoverTextMutable.value = filterText
    }


    /**
     * Wybiera pokój i przygotowuje do wejścia
     *
     * Proces weryfikacji dla pokoju prywatnego:
     * 1. Sprawdza czy użytkownik ma klucz AES lokalnie
     * 2. Jeśli NIE MA klucza:
     *    a) Wysyła request o ponowne wysłanie klucza
     *    b) Uruchamia polling oczekiwania na klucz (POLLING #4)
     *    c) Pokazuje komunikat o oczekiwaniu
     * 3. Jeśli MA klucz:
     *    - Normalnie wchodzi do pokoju
     *
     * Dla pokoju publicznego:
     * - Bezpośrednie wejście (brak weryfikacji klucza)
     *
     * Po wejściu:
     * - Czyści listę znanych użytkowników (knownUserIds)
     * - Ustawia wybrany pokój w state
     * - Emituje event Success/ErrorUnresolved reference 'resetWelcomeState'.
     *
     * @param room Pokój który użytkownik chce otworzyć
     * @param verifyKeyExist Czy weryfikować istnienie klucza (domyślnie true)
     */
    //w tej wersji moze byc wyscig, ale nasza apka taka mala, ze raczej nie bedzie problemu
    fun selectRoom(room: RoomData, verifyKeyExist: Boolean = true) {
        viewModelScope.launch {
            // weryfikacja klucza dla pokoju prywatnego
            if (verifyKeyExist && room.isPrivate) {
                var hasKey = roomRepository.hasRoomAESKey(room.idRoom)

                if (!hasKey) {
                    Log.w("VIEWMODEL", "Brak klucza AES dla pokoju ${room.name}")
                    Log.d("VIEWMODEL", "Rozpoczynam pobieranie klucza od innych użytkowników...")

                    // Wyślij request o ponowne wysłanie klucza
                    // Request trafi do wszystkich członków pokoju
                    // Globalny polling (POLLING #3) u innych członków wykryje status "waitingForKey"
                    // i automatycznie wyśle klucz
                    val requested = roomRepository.requestKeyAgain(room.idRoom)

                    if (requested) {
                        Log.d("VIEWMODEL", "Request wysłany - czekam na klucz...")

                        // Rozpocznij polling odzyskiwania klucza
                        startWaitingForKeyPolling(room)

                        // Powiadom użytkownika, ze musi poczekac
                        selectedRoomEventMutable.emit(
                            ProcessEvent.Error("Waiting for encryption key. Please wait...")
                        )
                    } else {
                        Log.e("VIEWMODEL", "Nie udało się wysłać requestu")
                        selectedRoomEventMutable.emit(
                            ProcessEvent.Error("Cannot access this room. Please try again later.")
                        )
                    }

                    return@launch
                }
                // Klucz jest dostępny lokalnie
                Log.d("VIEWMODEL", "Mam klucz AES - wchodzę do pokoju")
            }

            // Normalnie wchodzimy do pokoju
            // Wyczyść listę znanych użytkowników
            // Przy zmianie pokoju resetujemy listę, aby nowi użytkownicy
            // byli wykrywani przez SSE stream
            knownUserIds.clear()
            // Ustaw wybrany pokój w state
            selectedRoomMutable.value = room
            Log.d("VIEWMODEL", "Wybrany pokój: ${room.name}")

            // Emit event Success/Error
            if (selectedRoomMutable.value != null) {
                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
            } else {
                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
            }
        }
    }
    //w room repo jest funkcja co uzywam
    private suspend fun verifyRoomKeyExist(room: RoomData): Boolean {
        val hasKey = roomRepository.verifyRoomKeyExists(room.idRoom, room.isPrivate)
        if (hasKey) {
            Log.d("ViewModel", "User has key - allowing access")
            return true
        }

        // Jeśli nie ma klucza lokalnie, sprawdź czy jest dostępny na serwerze
        Log.d("ViewModel", "No local key, checking server...")
        val fetchSuccess = roomRepository.fetchAndDecryptRoomKey(room.idRoom)

        if (fetchSuccess) {
            Log.d("ViewModel", "Successfully fetched key from server")
            return true
        }

        // Jeśli nie udało się pobrać klucza z serwera, wyślij request o ponowne wysłanie
        Log.d("ViewModel", "Failed to fetch from server, requesting key again...")
        val result = roomRepository.requestKeyAgain(room.idRoom)

        if (!result) {
            verifyKeyExistEventMutable.emit(ProcessEvent.Error("You'll need to wait before you can access this room. Please try again later."))
            //rozesłanie prośby do userów o przesłanie hasła i klucza pokoju, bez weryfikacji czy mu to przysługuje ;)
        }
        return result
    }

    private suspend fun refreshRoomUsers() {
        val currentRoom = selectedRoom.value ?: return

        try {
            val response = MessageUtils.requestRoomUsers(currentRoom.idRoom)
            if (response != null) {
                roomUsersMutable.value = response.userList.rooms
            } else {
                Log.e("NearNetVM", "Nie udało się odświeżyć listy uzytkowników")
            }
        } catch (e: Exception) {
            Log.e("NearNetVM", "Błąd odświeżania listy użytkowników", e)
        }
    }

    suspend fun loadMessages(room: RoomData) {


        //Pobranie wiadomości z serwera
        Log.d("loadMessages", "Pobieram wiadomości dla pokoju=${room.idRoom}")

        val startTime = System.currentTimeMillis()
        //Zuzycie pamieci przed zaladowaniem wiadomosci
        logMemoryUsage("PRZED")
        Log.d("PERFORMANCE_TEST_2", "Rozpoczynam ładowanie wiadomości dla pokoju: ${room.name}")

        val response = try {
            Log.d("loadMessages", "Pobieram wiadomości dla pokoju=${room.idRoom}")
            MessageUtils.requestLastMessages(room.idRoom)
        } catch (e: Exception) {
            Log.e("loadMessages", "Błąd podczas pobierania wiadomości dla pokoju=${room.idRoom}", e)
            null
        }

        if (response == null) {
            Log.e("loadMessages", "Serwer zwrócił pustą odpowiedź dla pokoju=${room.idRoom}")
            return
        }

        val messageList = response.`package`?.messageList
        if (messageList.isNullOrEmpty()) {
            Log.w("loadMessages", "Brak wiadomości w historii dla pokoju=${room.idRoom}")
        } else {
            Log.d("loadMessages", "Otrzymano ${messageList.size} wiadomości dla pokoju=${room.idRoom}")
        }

        //Pobranie listy użytkowników pokoju
        val userResponse = try {
            Log.d("loadMessages", "Pobieram użytkowników dla pokoju=${room.idRoom}")
            MessageUtils.requestRoomUsers(room.idRoom)
        } catch (e: Exception) {
            Log.e("loadMessages", "Błąd podczas pobierania listy użytkowników dla pokoju=${room.idRoom}", e)
            null
        }

        //Zapisywanie listy userów i wiadomości do zmiennych
        roomUsersMutable.value = userResponse?.userList?.rooms?.map { it.copy() } ?: listOf()
        messagesMutable.value = MessageUtils.mapPayloadToMessages(
            room.idRoom,
            messageList ?: emptyList()
        )
        //Zuzycie pamieci po zaladowaniem wiadomosci
        logMemoryUsage("PO")
        // KONIEC POMIARU
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val msgCount = messagesMutable.value.size

        Log.d("PERFORMANCE_TEST_2", "CZAS_LADOWANIA: ${duration}ms | LICZBA_WIADOMOSCI: ${msgCount}")

    }


    fun sendMessage(messageText : String, room : RoomData, messageType: MessageType){
        viewModelScope.launch{
            // TODO Call asynchronous function to send messages
            val user = selectedUser.value
            if (user == null){
                Log.e("sendMessage", "selectedUser jest NULL!")
                return@launch
            }
            val timestamp = System.currentTimeMillis().toString()
            Log.d("sendMessage", "Użytkownik: id='${user.id}', nazwa='${user.name}'")
            val newMessage = Message(
                id = timestamp,
                roomId = room.idRoom,
                userId = user.id,
                messageType = messageType.name,
                message = messageText,
                additionalData = "",
                timestamp = timestamp
            )

            //Log.d("sendMessage", "Wysyłam wiadomość na backend: $newMessage")
            Log.d("sendMessage", "Wysyłam wiadomość: userId='${newMessage.userId}'")
            //POMIAR CZASU - START
            val startTime = System.currentTimeMillis()
            try {
                val success = MessageUtils.sendMessage(room.idRoom, newMessage)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                if (success) {
                    Log.d("sendMessage", "Wiadomość wysłana poprawnie")
                    Log.d("PERFORMANCE_TEST_1", "CZAS_WYSYLANIA: ${duration}ms")
                } else {
                    Log.e("sendMessage", "Nie udało się wysłać wiadomości")
                }
            } catch (e: Exception) {
                Log.e("sendMessage", "Exception w sendMessage", e)
            }
        }
    }

    fun startRealtime(room: RoomData) {
        val userId = selectedUser.value?.id ?: return
        stopRealtimeFlag = false

        MessageUtils.receiveMessagesStream(
            room.idRoom,
            userId,
            onMessage = { newMessages ->
                viewModelScope.launch(Dispatchers.Main) {
                    newMessages.forEach { msg ->
                        val userExists = roomUsers.value.any { it.id == msg.userId }

                        if (!userExists && !knownUserIds.contains(msg.userId)) {
                            Log.d("NearNetVM", "Nowy uzytkownik ${msg.userId}, odswiezam liste")
                            knownUserIds.add(msg.userId)

                            // odswiezenie listy uzytkownikow
                            viewModelScope.launch {
                                refreshRoomUsers()
                            }
                        }
                    }

                    messagesMutable.update { old ->
                        (old + newMessages).distinctBy { it.id }
                    }
                }
            },
            onReconnect = {
                // jesli ktos recznie zatrzymal realtime — nie rob reconnect
                if (stopRealtimeFlag || room.idRoom != selectedRoom.value?.idRoom) return@receiveMessagesStream

                reconnectJob?.cancel()
                reconnectJob = viewModelScope.launch {
                    Log.w("SSE", "Reconnecting... fetching last messages.")
                    try {
                        val refreshed = MessageUtils.requestLastMessages(room.idRoom)
                        val messages = refreshed?.`package`?.messageList ?: emptyList()
                        val mapped = MessageUtils.mapPayloadToMessages(room.idRoom, messages)
                        messagesMutable.update { old ->
                            (old + mapped).distinctBy { it.id }
                        }
                        Log.i("SSE", "Reconnect successful — messages refreshed")
                    } catch (e: Exception) {
                        Log.e("SSE", "Reconnect failed", e)
                    }
                }
            }
        )
    }

    /**
     * Generuje i wysyła określoną liczbę wiadomości testowych do pokoju
     * Używane do testów wydajnościowych
     *
     * @param room Pokój docelowy
     * @param count Liczba wiadomości do wygenerowania
     * @param delayMs Opóźnienie między wiadomościami (ms)
     */
    fun generateTestMessages(room: RoomData, count: Int, delayMs: Long = 50) {
        viewModelScope.launch {
            Log.d("TEST_GENERATOR", "GENERATOR WIADOMOŚCI TESTOWYCH")
            Log.d("TEST_GENERATOR", "Pokój: ${room.name}")
            Log.d("TEST_GENERATOR", "Liczba: $count wiadomości")

            val startTime = System.currentTimeMillis()
            var successCount = 0
            var errorCount = 0

            for (i in 1..count) {
                val user = selectedUser.value
                if (user == null) {
                    Log.e("TEST_GENERATOR", "Brak zalogowanego użytkownika - przerwano na #$i")
                    break
                }

                val timestamp = System.currentTimeMillis().toString()
                val testMessage = Message(
                    id = timestamp,
                    roomId = room.idRoom,
                    userId = user.id,
                    messageType = MessageType.TEXT.name,
                    message = "Wiadomość testowa #$i z ${count}",
                    additionalData = "",
                    timestamp = timestamp
                )

                try {
                    val success = MessageUtils.sendMessage(room.idRoom, testMessage)

                    if (success) {
                        successCount++

                        // Log co 50 wiadomości
                        if (i % 50 == 0) {
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                            val rate = i / elapsed
                            Log.d("TEST_GENERATOR", "Postęp: $i/$count (${String.format("%.1f", rate)} msg/s)")
                        }
                    } else {
                        errorCount++
                        Log.e("TEST_GENERATOR", "Błąd wysyłania #$i")
                    }

                } catch (e: Exception) {
                    errorCount++
                    Log.e("TEST_GENERATOR", "Wyjątek #$i: ${e.message}")
                }

                // Opóźnienie między wiadomościami
                if (i < count) {
                    delay(delayMs)
                }
            }

            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0


            Log.d("TEST_GENERATOR", "ZAKOŃCZONO GENEROWANIE")
            Log.d("TEST_GENERATOR", "Sukces: $successCount")
            Log.d("TEST_GENERATOR", "Błędy: $errorCount")
            Log.d("TEST_GENERATOR", "Łącznie: ${successCount + errorCount}")
            Log.d("TEST_GENERATOR", "Czas: ${String.format("%.2f", totalTime)}s")
            Log.d("TEST_GENERATOR", "Średnia: ${String.format("%.2f", successCount / totalTime)} msg/s")
        }
    }

    fun stopRealtime() {
        stopRealtimeFlag = true
        reconnectJob?.cancel()
        reconnectJob = null
        MessageUtils.stopReceivingMessages()
        Log.d("SSE", "Zatrzymano połączenie SSE")
    }

    fun loadRecentMessages() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch recent messages here.
            //recentMutable.value = getRecentMessages(idUser) //zwraca listę trójek (Room, lastMessage,user)
            //funkcja: grupuje wiadomości po pokojach, dla każdej grupy uzyskuje dane pokoju, a następnie tworzy trójki
            //typu (wiadomość, pokój, nazwa użytkownika), w SQL join pokoju do wiadomości i do usera, i groupby po pokojach ,
            //a potem select na te trójki
            try {
                val rooms = roomRepository.getMyRooms()
                myRoomsMutable.value = rooms

                val allRecents = mutableListOf<Recent>()

                for (room in rooms) {
                    val response = MessageUtils.requestLastMessages(room.idRoom)

                    if (response?.`package`?.messageList.isNullOrEmpty()) continue

                    val userResponse = try {
                        MessageUtils.requestRoomUsers(room.idRoom)
                    } catch (e: Exception) {
                        Log.e("loadRecentMessages", "Błąd przy pobieraniu użytkowników pokoju=${room.idRoom}", e)
                        null
                    }
                    //mapowanie id -> user
                    val userMap = userResponse?.userList?.rooms
                        ?.associate { user -> user.id to user }
                        ?: emptyMap()

                    val messages = MessageUtils.mapPayloadToMessages(
                        room.idRoom,
                        response?.`package`?.messageList ?: emptyList()
                    )

                    val latest = messages.maxByOrNull { it.timestamp } ?: continue

                    val recentItem = Recent(
                        message = latest,
                        room = room,
                        user = userMap[latest.userId]
                    )

                    allRecents.add(recentItem)
                }

                val sorted = allRecents.sortedByDescending { it.message.timestamp }
                recentsMutable.value = sorted

                Log.d("NearNetVM", "loadRecentMessages: Loaded ${sorted.size} recents")

            } catch (e: Exception) {
                Log.e("NearNetVM", "loadRecentMessages error", e)
            }
        }
    }

    //for popups management: show, close, clear list
    fun selectPopup(popupType: PopupType, data: Any? = null) {
        val currentPopup = PopupContext(popupType, data)
        if (selectedPopupMutable.value == null) {
            selectedPopupMutable.value = currentPopup
        } else {
            queuedPopupList.push(currentPopup)
        }
    }
    fun closePopup() {
        if (queuedPopupList.isEmpty()) {
            selectedPopupMutable.value = null
        } else {
            selectedPopupMutable.value = queuedPopupList.pop()
        }
    }
    fun clearQueuedPopups() {
        queuedPopupList.clear()
    }

    fun setServerAddress(serverAddress : String){
        viewModelScope.launch {
            // TODO Call asynchronous function to set a custom server address and port.
            // setServerAddress(serverAddress) //funkcja wpisuje damyślny serwer i port w shared preferences
            val context = contextProvider?.invoke()
            if (context == null) {
                Log.e("ViewModel", "Brak kontekstu - nie można zapisać adresu serwera")
                return@launch
            }

            val success = ServerConfig.setCustomServer(context, serverAddress)

            if (success) {
                Log.d("ViewModel", "Serwer ustawiony: $serverAddress")

                ServerConfig.clearCache()

                Toast.makeText(context, "Server changed to $serverAddress", Toast.LENGTH_LONG).show()

                // Wyloguj i wyczyść stan
                clearAppState()
                selectedUserMutable.value = null

                // Nie restartuj - po prostu komunikat
                delay(1000)
                Toast.makeText(context, "Please restart the app", Toast.LENGTH_LONG).show()

                // Zamknij aplikację (user musi ręcznie otworzyć)
                if (context is Activity) {
                    context.finishAffinity()
                }
                exitProcess(0)
            } else {
                Log.e("ViewModel", "Nie udało się ustawić serwera")
                Toast.makeText(context, "Invalid server address", Toast.LENGTH_SHORT).show()
            }

        }
    }
    fun setDefaultServerAddress(){
        viewModelScope.launch {
            // TODO Call asynchronous function to set the default server address and port.
            // setDefaultServerAddress() //funkcja wpisuje pusty string w shared preferences (czy tam kasuje ten wpis z shared preferences z customowym adresem serwera)
            val context = contextProvider?.invoke()
            if (context == null) {
                Log.e("ViewModel", "Brak kontekstu - nie można przywrócić domyślnego serwera")
                return@launch
            }

            ServerConfig.setDefaultServer(context)
            ServerConfig.clearCache()

            Log.d("ViewModel", "Przywrócono domyślny serwer")

            Toast.makeText(context, "Default server restored", Toast.LENGTH_LONG).show()

            // Wyloguj i wyczyść
            clearAppState()
            selectedUserMutable.value = null

            delay(1000)
            Toast.makeText(context, "Please restart the app", Toast.LENGTH_LONG).show()

            // Zamknij aplikację
            if (context is Activity) {
                context.finishAffinity()
            }
            exitProcess(0)
        }
    }

    /**
     * Restartuje aplikację
     */
    private suspend fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        delay(1000)
        // Zabij obecny proces
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    //I TERAZ NA START APKI SPRAWDZASZ, CZY JEST COŚ W SHARED PREFERENCES, JAK JEST TO TO STOSUJESZ JAKO SERWER NA KTÓRY SIĘ ŁĄCZĘ Z TEGO URZĄDZENIA, JAK NIE MA -TO ŁĄCZĘ SIĘ NA DOMYŚLNY SERWER, czyli TEN Z ServerConfig
    //do shared preferences ustawia serwer i port funkcja setServerAddress
    //z shared preferences kasuje serwer i port usera funkcja setDefaultServerAddress

    fun validateServerAddress(serverAddress: String) : Boolean {
        val parts = serverAddress.split(":")
        if (parts.size != 2) return false

        val ip = parts[0]
        val port = parts[1].toIntOrNull() ?: return false

        // Sprawdzenie zakresu portu
        if (port !in 1..65535) return false

        // Sprawdzenie poprawności IP
        val ipRegex = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}$")
        if (!ip.matches(ipRegex)) return false

        return true
    }

    /**
     * pomiar zużycia pamięci RAM przy łądowaniu wiadomości w pokoju
     */
    fun logMemoryUsage(label: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val percentUsed = (usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()

        Log.d("PERFORMANCE_TEST_3", "PAMIEC_RAM [$label]: ${usedMemory}MB / ${maxMemory}MB (${percentUsed}%)")
    }

    /**
    * Cleanup przy niszczeniu ViewModelu
    *
    * Zatrzymuje wszystkie aktywne polling'i i połączenia
    */
    override fun onCleared() {
        super.onCleared()
        stopJoinRequestPolling()
        stopPendingRequestsPolling()
        stopRealtime()
        stopWaitingForKeyPolling()
        stopPasswordVerificationPolling()
        stopGlobalPasswordCheckPolling()
    }

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
