package com.nearnet.ui.model

import android.content.Context
import android.util.Log
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
import com.nearnet.sessionlayer.logic.UserStatus
import org.json.JSONObject


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
    USER_LIST_IN_ROOM
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
    //pozwala uzywac messageUtils bez uzywania LocalContext.current w ViewModelu
//    fun initMessageUtils(context: Context) {
//        if (!::messageUtils.isInitialized) {
//            messageUtils = MessageUtils { UserRepository.getTokenFromPreferences(context) }
//        }
//    }
    //przerobienie class na object
//    fun initMessageUtils(context: Context) {
//        MessageUtils.init { UserRepository.getTokenFromPreferences(context) }
//    }
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
    private val joinRoomEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
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


    //constructor to VievModel
    init {

    }

    //TODO dziala ok - ujednolicielm UserData
    fun logInUser(login: String, password: String) {
        viewModelScope.launch {
            try {
                val userData = repository.loginUser(login, password)

                selectedUserMutable.value = userData
                selectedUserEventMutable.emit(ProcessEvent.Success(userData))
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
            stopGlobalPasswordCheckPolling()
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
        stopJoinRequestPolling()
        stopPendingRequestsPolling()
        stopWaitingForKeyPolling()
        stopPasswordVerificationPolling()
        stopGlobalPasswordCheckPolling()

    }
    // TODO tutaj chyba jakas oblusge/pola do additionalSettings
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
                    additionalSettings = if (additionalSettings.isNotBlank()) additionalSettings else currentUser.additionalSettings
                )

                val result = repository.updateUser(userData, currentPassword, newPassword)

                if (result) {
                    // update lokalnego usera w stanie UI
                    selectedUserMutable.value = currentUser.copy(name = userName, avatar = avatar, additionalSettings = additionalSettings)
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
//    fun loadMyRooms() {
//        viewModelScope.launch {
//            // TODO Call asynchronous function to fetch my rooms here.
//            if (::roomRepository.isInitialized) {
//                val roomsFromApi = roomRepository.getMyRooms()
//                myRoomsMutable.value = roomsFromApi
//                if (selectedUser.value != null && passwordCheckPollingJob?.isActive != true) {
//                    startGlobalPasswordCheckPolling()
//                }
//            } else {
//                Log.e("loadMyRooms", "RoomRepository is not initialized!")
//            }
//        }
//    }

    fun loadMyRooms() {
        viewModelScope.launch {
            if (::roomRepository.isInitialized) {
                Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("ROOM", "📚 Ładuję moje pokoje...")

                val roomsFromApi = roomRepository.getMyRooms()

                Log.d("ROOM", "📚 Otrzymałem ${roomsFromApi.size} pokoi z serwera:")
                roomsFromApi.forEach { room ->
                    Log.d("ROOM", "  📍 ${room.name}")
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
                Log.e("loadMyRooms", "RoomRepository is not initialized!")
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
//    fun joinRoom(room: RoomData, password: String){
//        viewModelScope.launch {
//            //var status : Boolean = joinRoom(room.id, ””) //funkcja dla Marka -> podawane jest id pokoju gdzie dołączam i hasło lub pusty string->
//            // hasło: dla publicznego pokoju pusty string podaję, dla  prywatnego podaję hasło które użytkownik wpisał lub pusty string gdy go nie zna,
//            // jak jest publiczny lub użytkownik poda hasło, to klucz do rozszyfrowania wiadomości dostaje od dowolnego użytkownika, gdzie klucz jest zaszyfrowany przez RSA (+ osobno wiadomości zaszyfrowane AES, któree tym kluczem rozszyfruje sobie)
//            // jak jest pokój prywatny i użytkownik nie zna hasła, to prośba o dołączenie idzie do admina i on potwierdza i wysyła mu on ten klucz szyfrowany przez RSA (+ osobno też wiadomości zaszyfrowane AES)
//            //4 przypadki!!!
//            //1.pokój prywatny i nie ma hasła od użytkownika (pusty string) ->prośba do admina o dołącznie
//            //2.pokój prywatny i jest hasło od użytkownika -> dołącza po sprawdzeniu poprawności z hashem na serwerze lub status=false
//            //3.pokój publiczny i nie ma hasła (pusty string) -> dołącza
//            //4.pokój publiczny i jest hasło - PRZYPADEK NIE MA PRAWA ZAJŚĆ, w razie czego ignorujemy hasło i wpuszczamy do pokoju ->dołącza
//            //hasło do pokoju trzymane w postaci hasha na serwerze, dodawane przy tworzeniu pokoju
//            //var status :Boolean = true //wykomentować
//            try {
//                Log.d("NearNetVM", "Attempting to join room: ${room.name} with password=${if (password.isBlank()) "<empty>" else "<provided>"}")
//
//                var joinSuccess = false
//
//                //TODO tutaj trzeba dodacpopup z haslem, bo w rpzeciwnym wypadku dla kazdego pokoju prwatnego nawet z haselm sie te przypadek wyzej odpala
//                // publiczny lub prywatny z hasłem
//                val passwordToSend = if (room.isPrivate) password else "" // publiczny zawsze pusty string
//                Log.d("NearNetVM", "Joining room: ${room.name} with password=${if (passwordToSend.isBlank()) "<empty>" else "<provided>"}")
//
//                if (::roomRepository.isInitialized) {
//                    joinSuccess = roomRepository.addMyselfToRoom(room.idRoom, passwordToSend)
//                    Log.d("NearNetVM", "Server returned joinSuccess=$joinSuccess for room: ${room.name}")
//                } else {
//                    Log.e("NearNetVM", "RoomRepository is not initialized!")
//                }
//
//                if (joinSuccess) {
//                    //selectRoom(room) //Nie przenosi do rooma , tylko z powrotem do discovery
//                    if (room.isPrivate && password.isNotBlank()) {
//                        roomRepository.fetchAndDecryptRoomKey(room.idRoom)
//                    }
//                    Log.d("NearNetVM", "Successfully joined room: ${room.name}")
//                } else {
//                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to join room — incorrect password or server error."))
//                    Log.e("NearNetVM", "Could not join room: ${room.name}")
//                }
//
//            } catch (e: Exception) {
//                Log.e("NearNetVM", "Exception in joinRoom", e)
//                joinRoomEventMutable.emit(ProcessEvent.Error("Unexpected error while joining the room."))
//            }
//        }
//    }

    fun joinRoom(room: RoomData, password: String) {
        viewModelScope.launch {
            try {
                Log.d("NearNetVM", "Attempting to join room: ${room.name}")

                if (!::roomRepository.isInitialized) {
                    Log.e("NearNetVM", "RoomRepository is not initialized!")
                    joinRoomEventMutable.emit(ProcessEvent.Error("Internal error"))
                    return@launch
                }

                // Jeśli publiczny - standardowe dołączenie
                if (!room.isPrivate) {
                    val joinSuccess = roomRepository.addMyselfToRoom(room.idRoom, "")
                    if (joinSuccess) {
                        Log.d("NearNetVM", "✅ Joined public room")
                        joinRoomEventMutable.emit(ProcessEvent.Success(Unit))
                    } else {
                        joinRoomEventMutable.emit(ProcessEvent.Error("Failed to join room"))
                    }
                    return@launch
                }

                // ✅ POKÓJ PRYWATNY Z HASŁEM - użyj nowego przepływu
                Log.d("NearNetVM", "🔐 Private room - sending password verification request...")

                // Wyślij prośbę o weryfikację hasła
                val requestSent = roomRepository.requestJoinByPassword(room.idRoom)

                if (!requestSent) {
                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request"))
                    return@launch
                }

                Log.d("NearNetVM", "✅ Request sent, starting password verification polling...")

                // Rozpocznij polling weryfikacji hasła
                startPasswordVerificationPolling(room, password)

                joinRoomEventMutable.emit(ProcessEvent.Success(Unit))

            } catch (e: Exception) {
                Log.e("NearNetVM", "Exception in joinRoom", e)
                joinRoomEventMutable.emit(ProcessEvent.Error("Unexpected error while joining the room."))
            }
        }
    }

    private var joinRequestPollingJob: Job? = null
    //proba do Admina o dołączenie do pokoju
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

            joinRoomEventMutable.emit(ProcessEvent.Success(Unit))
            Log.d("ROOM", "Request sent successfully, waiting for admin approval...")

            startJoinRequestPolling(room)

    }}

    private fun startJoinRequestPolling(room: RoomData) {
        stopJoinRequestPolling() // Zatrzymaj ewentualny poprzedni polling

        joinRequestPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut sprawdzania (co 5 sekund)

            Log.d("ROOM", "🔄 Rozpoczynam sprawdzanie statusu prośby dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(5000) // Sprawdzaj co 5 sekund

                try {
                    // Sprawdź status używając istniejącego endpointu serwera
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        Log.w("ROOM", "⚠️ Nie można sprawdzić statusu prośby (attempt ${attempts + 1})")
                        attempts++
                        continue
                    }

                    Log.d("ROOM", "📊 Status prośby: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {
                        "accepted" -> {
                            Log.d("ROOM", "✅ Prośba zaakceptowana!")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                Log.d("ROOM", "🔑 Otrzymano zaszyfrowany klucz, rozpoczynam deszyfrowanie...")

                                // Użyj otrzymanego klucza
                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey
                                )

                                if (keyFetched) {
                                    Log.d("ROOM", "✅ Klucz odszyfrowany i zapisany pomyślnie!")
                                    // Możesz tutaj dodać nawigację do pokoju lub pokazać powiadomienie
                                    // selectRoom(room) // jeśli chcesz automatycznie otworzyć pokój
                                } else {
                                    Log.e("ROOM", "❌ Nie udało się odszyfrować klucza")
                                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to decrypt room key"))
                                }
                            } else {
                                Log.w("ROOM", "⚠️ Zaakceptowano, ale brak klucza (pokój publiczny?)")
                            }

                            // Zakończ polling
                            break
                        }

                        "rejected" -> {
                            Log.d("ROOM", "❌ Prośba odrzucona przez admina")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Your request was rejected by the admin"))
                            break
                        }

                        "pending" -> {
                            // Kontynuuj oczekiwanie
                            Log.d("ROOM", "⏳ Nadal oczekuje na decyzję admina...")
                        }

                        "inRoom" -> {
                            Log.d("ROOM", "✅ Już jesteś członkiem pokoju")
                            break
                        }

                        else -> {
                            Log.w("ROOM", "⚠️ Nieznany status: ${requestStatus.status}")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("ROOM", "❌ Błąd sprawdzania statusu prośby", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("ROOM", "⏱️ Przekroczono limit czasu oczekiwania na odpowiedź admina")
                joinRoomEventMutable.emit(ProcessEvent.Error("Admin hasn't responded yet. Please try again later."))
            }

            joinRequestPollingJob = null
        }
    }

    fun stopJoinRequestPolling() {
        joinRequestPollingJob?.cancel()
        joinRequestPollingJob = null
        Log.d("ROOM", "🛑 Zatrzymano sprawdzanie statusu prośby")
    }

    private var passwordVerificationPollingJob: Job? = null

    private fun startPasswordVerificationPolling(room: RoomData, password: String) {
        stopPasswordVerificationPolling()

        passwordVerificationPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut

            Log.d("ROOM", "🔄 Rozpoczynam weryfikację hasła dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(3000) // Co 3 sekundy

                try {
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        attempts++
                        continue
                    }

                    Log.d("ROOM", "📊 Status weryfikacji: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {
//                        "declaredPasswordCheck" -> {
//                            // Ktoś zadeklarował sprawdzenie - wyślij zaszyfrowane hasło
//                            Log.d("ROOM", "🔐 Weryfikator gotowy - wysyłam zaszyfrowane hasło")
//
//                            val checkerId = requestStatus.encryptedRoomKey // ID weryfikatora
//
//                            if (!checkerId.isNullOrEmpty()) {
//                                val context = contextProvider?.invoke()
//                                if (context == null) {
//                                    Log.e("ROOM", "✗ Context niedostępny")
//                                    continue
//                                }
//
//                                val checkerPublicKey = PublicKeyManager(context).getPublicKeyForUser(checkerId)
//
//                                if (checkerPublicKey != null) {
//                                    val encryptedPassword = CryptoUtils.encryptStringWithRSA(password, checkerPublicKey)
//
//                                    val sent = roomRepository.sendEncryptedPassword(room.idRoom, encryptedPassword)
//
//                                    if (sent) {
//                                        Log.d("ROOM", "✓ Zaszyfrowane hasło wysłane")
//                                    }
//                                }
//                            }
//                        }
                        "declaredPasswordCheck" -> {
                            Log.d("ROOM", "🔐 Weryfikator gotowy - wysyłam zaszyfrowane hasło")

                            val checkerId = requestStatus.encryptedRoomKey // ID weryfikatora

                            Log.d("ROOM", "🔍 CheckerId: $checkerId")  // ← DODAJ

                            if (checkerId.isNullOrEmpty()) {
                                Log.e("ROOM", "✗ CheckerId jest pusty!")  // ← DODAJ
                                continue
                            }

                            Log.d("ROOM", "✓ CheckerId OK: $checkerId")  // ← DODAJ

                            val context = contextProvider?.invoke()
                            if (context == null) {
                                Log.e("ROOM", "✗ Context niedostępny")
                                continue
                            }

                            Log.d("ROOM", "✓ Context OK")  // ← DODAJ

                            val checkerPublicKey = PublicKeyManager(context).getPublicKeyForUser(checkerId)

                            Log.d("ROOM", "🔍 PublicKey dla $checkerId: ${if (checkerPublicKey != null) "FOUND" else "NULL"}")  // ← DODAJ

                            if (checkerPublicKey != null) {
                                Log.d("ROOM", "✓ Klucz publiczny weryfikatora pobrany")  // ← DODAJ

                                try {
                                    val encryptedPassword = CryptoUtils.encryptStringWithRSA(password, checkerPublicKey)

                                    Log.d("ROOM", "✓ Hasło zaszyfrowane")  // ← DODAJ
                                    Log.d("ROOM", "🔍 Encrypted password (50 chars): ${encryptedPassword.take(50)}")  // ← DODAJ

                                    val sent = roomRepository.sendEncryptedPassword(room.idRoom, encryptedPassword)

                                    if (sent) {
                                        Log.d("ROOM", "✅ Zaszyfrowane hasło wysłane pomyślnie!")
                                    } else {
                                        Log.e("ROOM", "✗ Nie udało się wysłać zaszyfrowanego hasła")
                                    }
                                } catch (e: Exception) {
                                    Log.e("ROOM", "❌ Błąd szyfrowania hasła", e)
                                }
                            } else {
                                Log.e("ROOM", "✗ Nie można pobrać klucza publicznego weryfikatora")
                            }
                        }

                        "accepted" -> {
                            Log.d("ROOM", "✅ Hasło zweryfikowane! Pobieram dane pokoju...")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                keysBeingSaved.add(room.idRoom)

                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey
                                )

                                keysBeingSaved.remove(room.idRoom)

                                if (keyFetched) {
                                    Log.d("ROOM", "✅ Dane pokoju zapisane lokalnie!")
                                }
                            }

                            break
                        }

                        "rejected" -> {
                            Log.d("ROOM", "❌ Niepoprawne hasło")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Incorrect password"))
                            break
                        }

                        "requestJoin" -> {
                            Log.d("ROOM", "⏳ Czekam na weryfikatora...")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("ROOM", "Błąd weryfikacji hasła", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("ROOM", "⏱️ Timeout weryfikacji hasła")
            }

            passwordVerificationPollingJob = null
        }
    }

    fun stopPasswordVerificationPolling() {
        passwordVerificationPollingJob?.cancel()
        passwordVerificationPollingJob = null
        Log.d("ROOM", "🛑 Zatrzymano weryfikację hasła")
    }

    private var passwordCheckPollingJob: Job? = null
    private val handledPasswordChecks = mutableSetOf<String>()

//    fun startGlobalPasswordCheckPolling() {
//        stopGlobalPasswordCheckPolling()
//
//        passwordCheckPollingJob = viewModelScope.launch {
//            Log.d("ROOM", "🔄 Rozpoczynam globalny polling sprawdzania haseł")
//
//            while (isActive) {
//                try {
//                    // Pobierz WSZYSTKIE pokoje użytkownika
//                    val myRoomsList = myRooms.value
//
//                    // Sprawdź każdy pokój prywatny
//                    myRoomsList.filter { it.isPrivate }.forEach { room ->
//                        try {
//                            val usersWaiting = roomRepository.getRoomUsersStatus(room.idRoom)
//
//                            usersWaiting.forEach { userStatus ->
//                                val key = "${userStatus.userId}-${room.idRoom}-${userStatus.status}"
//
//                                // Sprawdź czy już obsłużyliśmy
//                                if (handledPasswordChecks.contains(key)) {
//                                    return@forEach
//                                }
//
//                                when (userStatus.status) {
//                                    "requestJoin" -> {
//                                        // Nowy użytkownik czeka - AUTOMATYCZNIE zadeklaruj sprawdzenie
//                                        Log.d("ROOM", "👤 [${room.name}] Nowy użytkownik ${userStatus.userId} czeka - deklaruję sprawdzenie")
//
//                                        handledPasswordChecks.add(key)
//
//                                        val declared = roomRepository.declarePasswordCheck(room.idRoom, userStatus.userId)
//
//                                        if (declared) {
//                                            Log.d("ROOM", "✓ [${room.name}] Zadeklarowano sprawdzenie hasła")
//                                        }
//                                    }
//
//                                    "passwordReadyToCheck" -> {
//                                        // Użytkownik wysłał zaszyfrowane hasło - AUTOMATYCZNIE sprawdź
//                                        Log.d("ROOM", "🔐 [${room.name}] Otrzymano zaszyfrowane hasło od ${userStatus.userId} - sprawdzam")
//
//                                        handledPasswordChecks.add(key)
//
//                                        // Sprawdź hasło w tle
//                                        launch {
//                                            verifyUserPassword(room, userStatus)
//                                        }
//                                    }
//                                }
//                            }
//                        } catch (e: Exception) {
//                            Log.e("ROOM", "Błąd sprawdzania pokoju ${room.name}", e)
//                        }
//                    }
//
//                } catch (e: Exception) {
//                    Log.e("ROOM", "Błąd globalnego sprawdzania haseł", e)
//                }
//
//                delay(3000) // Co 3 sekundy
//            }
//        }
//    }

    fun startGlobalPasswordCheckPolling() {
        stopGlobalPasswordCheckPolling()

        passwordCheckPollingJob = viewModelScope.launch {
            Log.d("ROOM", "🔄 Rozpoczynam globalny polling sprawdzania haseł")

            while (isActive) {
                try {
                    val myRoomsList = myRooms.value

                    // 🔍 DODAJ SZCZEGÓŁOWE LOGI
                    Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("ROOM", "📊 Globalny polling - sprawdzam ${myRoomsList.size} pokoi")

                    val privateRooms = myRoomsList.filter { it.isPrivate }
                    Log.d("ROOM", "🔐 Prywatnych pokoi do sprawdzenia: ${privateRooms.size}")

                    privateRooms.forEach { room ->
                        Log.d("ROOM", "🔍 Checking room: ${room.name} (${room.idRoom})")
                    }

                    privateRooms.forEach { room ->
                        try {
                            Log.d("ROOM", "🔍 [${room.name}] Pobieram statusy użytkowników...")

                            val usersWaiting = roomRepository.getRoomUsersStatus(room.idRoom)

                            Log.d("ROOM", "📊 [${room.name}] Znaleziono ${usersWaiting.size} użytkowników czekających")

                            usersWaiting.forEach { userStatus ->
                                Log.d("ROOM", "  👤 User: ${userStatus.userId}")
                                Log.d("ROOM", "     Status: ${userStatus.status}")
                                Log.d("ROOM", "     EncryptedRoomKey: ${userStatus.encryptedRoomKey?.take(20) ?: "null"}")

                                val key = "${userStatus.userId}-${room.idRoom}-${userStatus.status}"

                                // Sprawdź czy już obsłużyliśmy
                                if (handledPasswordChecks.contains(key)) {
                                    Log.d("ROOM", "  ⏭️ Już obsłużone - pomijam")
                                    return@forEach
                                }

                                Log.d("ROOM", "  ✨ Nowy request - obsługuję!")

                                when (userStatus.status) {
                                    "requestJoin" -> {
                                        Log.d("ROOM", "👤 [${room.name}] Nowy użytkownik ${userStatus.userId} czeka - deklaruję sprawdzenie")

                                        handledPasswordChecks.add(key)

                                        launch {
                                            val declared = roomRepository.declarePasswordCheck(room.idRoom, userStatus.userId)

                                            if (declared) {
                                                Log.d("ROOM", "✓ [${room.name}] Zadeklarowano sprawdzenie hasła")
                                            } else {
                                                Log.e("ROOM", "✗ [${room.name}] Nie udało się zadeklarować sprawdzenia")
                                            }
                                        }
                                    }

                                    "passwordReadyToCheck" -> {
                                        Log.d("ROOM", "🔐 [${room.name}] Otrzymano zaszyfrowane hasło od ${userStatus.userId} - sprawdzam")

                                        handledPasswordChecks.add(key)

                                        launch {
                                            verifyUserPassword(room, userStatus)
                                        }
                                    }

                                    else -> {
                                        Log.d("ROOM", "  ℹ️ Status ${userStatus.status} - ignoruję")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ROOM", "Błąd sprawdzania pokoju ${room.name}", e)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("ROOM", "Błąd globalnego sprawdzania haseł", e)
                }

                delay(3000) // Co 3 sekundy
            }
        }
    }

    fun stopGlobalPasswordCheckPolling() {
        passwordCheckPollingJob?.cancel()
        passwordCheckPollingJob = null
        handledPasswordChecks.clear()
        Log.d("ROOM", "🛑 Zatrzymano globalny polling sprawdzania haseł")
    }

//    private suspend fun verifyUserPassword(room: RoomData, userStatus: UserStatus) {
//        try {
//            val context = contextProvider?.invoke()
//            if (context == null) {
//                Log.e("ROOM", "✗ Context niedostępny")
//                return
//            }
//
//            // Pobierz swój klucz prywatny
//            val myLogin = UserRepository.getLoginFromPreferences(context) ?: return
//            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin) ?: return
//
//            // Odszyfruj hasło
//            val encryptedPassword = userStatus.encryptedRoomKey ?: return
//            val decryptedPassword = CryptoUtils.decryptStringWithRSA(encryptedPassword, myPrivateKey)
//
//            Log.d("ROOM", "🔓 Odszyfrowano hasło od użytkownika ${userStatus.userId}")
//
//            // Sprawdź hasło - porównaj z lokalnym hasłem pokoju
//            val roomPassword = roomRepository.getRoomPassword(room.idRoom)
//
//            if (roomPassword == null) {
//                Log.w("ROOM", "⚠️ Nie mam hasła pokoju - nie mogę zweryfikować")
//                return
//            }
//
//            val isCorrect = (decryptedPassword == roomPassword)
//
//            if (isCorrect) {
//                Log.d("ROOM", "✅ Hasło poprawne! Wysyłam dane pokoju...")
//
//                // Pobierz klucz AES pokoju
//                val roomAESKeyBase64 = roomRepository.getRoomAESKey(room.idRoom)
//                if (roomAESKeyBase64 == null) {
//                    Log.e("ROOM", "✗ Nie mam klucza pokoju!")
//                    return
//                }
//
//                val roomAESKey = CryptoUtils.stringToAESKey(roomAESKeyBase64)
//
//                // Pobierz klucz publiczny nowego użytkownika
//                val targetPublicKey = PublicKeyManager(context).getPublicKeyForUser(userStatus.userId)
//                if (targetPublicKey == null) {
//                    Log.e("ROOM", "✗ Nie można pobrać klucza publicznego użytkownika ${userStatus.userId}")
//                    return
//                }
//
//                // Zaszyfruj klucz AES
//                val encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, targetPublicKey)
//
//                // Zaszyfruj hasło
//                val encryptedPasswordToSend = CryptoUtils.encryptStringWithRSA(roomPassword, targetPublicKey)
//
//                // Stwórz JSON
//                val jsonData = JSONObject().apply {
//                    put("encryptedAESKey", encryptedAESKey)
//                    put("encryptedPassword", encryptedPasswordToSend)
//                }
//
//                val jsonString = jsonData.toString()
//
//                // Wyślij JSON
//                val sent = roomRepository.sendRoomKeyToUser(room.idRoom, userStatus.userId, jsonString)
//
//                if (sent) {
//                    Log.d("ROOM", "✅ Dane pokoju wysłane pomyślnie")
//                }
//            } else {
//                Log.d("ROOM", "❌ Hasło niepoprawne - nie wysyłam danych")
//            }
//
//        } catch (e: Exception) {
//            Log.e("ROOM", "Błąd weryfikacji hasła", e)
//        }
//    }

    private suspend fun verifyUserPassword(room: RoomData, userStatus: UserStatus) {
        // ✅ DODAJ TEN LOG NA SAMYM POCZĄTKU
        Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("ROOM", "🔐 WERYFIKACJA HASŁA - START")
        Log.d("ROOM", "   Room: ${room.name} (${room.idRoom})")
        Log.d("ROOM", "   User: ${userStatus.userId}")
        Log.d("ROOM", "   Encrypted password: ${userStatus.encryptedRoomKey?.take(50)}")

        try {
            val context = contextProvider?.invoke()
            if (context == null) {
                Log.e("ROOM", "✗ Context niedostępny")
                return
            }

            Log.d("ROOM", "✓ Context OK")

            // Pobierz swój klucz prywatny
            val myLogin = UserRepository.getLoginFromPreferences(context)

            Log.d("ROOM", "🔍 Mój login: $myLogin")

            if (myLogin == null) {
                Log.e("ROOM", "✗ Nie można pobrać loginu")
                return
            }

            Log.d("ROOM", "✓ Login OK: $myLogin")

            val myPrivateKey = CryptoUtils.getPrivateKey(context, myLogin)

            if (myPrivateKey == null) {
                Log.e("ROOM", "✗ Nie można pobrać PrivateKey")
                return
            }

            Log.d("ROOM", "✓ PrivateKey OK")

            // Odszyfruj hasło
            val encryptedPassword = userStatus.encryptedRoomKey

            if (encryptedPassword == null) {
                Log.e("ROOM", "✗ Brak zaszyfrowanego hasła")
                return
            }

            Log.d("ROOM", "🔓 Odszyfrowuję hasło...")

            val decryptedPassword = CryptoUtils.decryptStringWithRSA(encryptedPassword, myPrivateKey)

            Log.d("ROOM", "✓ Hasło odszyfrowane: [${decryptedPassword.length} znaków]")

            // Sprawdź hasło - porównaj z lokalnym hasłem pokoju
            val roomPassword = roomRepository.getRoomPassword(room.idRoom)

            Log.d("ROOM", "🔍 Pobieram hasło pokoju...")

            if (roomPassword == null) {
                Log.e("ROOM", "✗ Nie mam hasła pokoju - nie mogę zweryfikować")
                return
            }

            Log.d("ROOM", "✓ Hasło pokoju: [${roomPassword.length} znaków]")
            Log.d("ROOM", "🔍 Porównuję: '$decryptedPassword' vs '$roomPassword'")

            val isCorrect = (decryptedPassword == roomPassword)

            Log.d("ROOM", "📊 Wynik porównania: $isCorrect")

            if (isCorrect) {
                Log.d("ROOM", "✅ Hasło POPRAWNE! Wysyłam dane pokoju...")

                // Pobierz klucz AES pokoju
                val roomAESKeyBase64 = roomRepository.getRoomAESKey(room.idRoom)
                if (roomAESKeyBase64 == null) {
                    Log.e("ROOM", "✗ Nie mam klucza pokoju!")
                    return
                }

                Log.d("ROOM", "✓ Klucz AES pokoju pobrany")

                val roomAESKey = CryptoUtils.stringToAESKey(roomAESKeyBase64)

                // Pobierz klucz publiczny nowego użytkownika
                val targetPublicKey = PublicKeyManager(context).getPublicKeyForUser(userStatus.userId)
                if (targetPublicKey == null) {
                    Log.e("ROOM", "✗ Nie można pobrać klucza publicznego użytkownika ${userStatus.userId}")
                    return
                }

                Log.d("ROOM", "✓ PublicKey nowego użytkownika pobrany")

                // Zaszyfruj klucz AES
                Log.d("ROOM", "🔐 Szyfruję klucz AES...")
                val encryptedAESKey = CryptoUtils.encryptAESKeyWithRSA(roomAESKey, targetPublicKey)

                Log.d("ROOM", "✓ Klucz AES zaszyfrowany")

                // Zaszyfruj hasło
                Log.d("ROOM", "🔐 Szyfruję hasło pokoju...")
                val encryptedPasswordToSend = CryptoUtils.encryptStringWithRSA(roomPassword, targetPublicKey)

                Log.d("ROOM", "✓ Hasło pokoju zaszyfrowane")

                // Stwórz JSON
                val jsonData = JSONObject().apply {
                    put("encryptedAESKey", encryptedAESKey)
                    put("encryptedPassword", encryptedPasswordToSend)
                }

                val jsonString = jsonData.toString()

                Log.d("ROOM", "📦 JSON utworzony (${jsonString.length} znaków)")
                Log.d("ROOM", "📤 Wysyłam dane do użytkownika...")

                // Wyślij JSON
                val sent = roomRepository.sendRoomKeyToUser(room.idRoom, userStatus.userId, jsonString)

                if (sent) {
                    Log.d("ROOM", "✅✅✅ Dane pokoju wysłane POMYŚLNIE! ✅✅✅")
                } else {
                    Log.e("ROOM", "❌ Nie udało się wysłać danych")
                }
            } else {
                Log.d("ROOM", "❌❌❌ Hasło NIEPOPRAWNE - nie wysyłam danych ❌❌❌")
                Log.d("ROOM", "   Otrzymane: '$decryptedPassword'")
                Log.d("ROOM", "   Oczekiwane: '$roomPassword'")
            }

        } catch (e: Exception) {
            Log.e("ROOM", "❌❌❌ BŁĄD weryfikacji hasła", e)
        }

        Log.d("ROOM", "🔐 WERYFIKACJA HASŁA - KONIEC")
        Log.d("ROOM", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
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

    private var waitingForKeyPollingJob: Job? = null

    private fun startWaitingForKeyPolling(room: RoomData) {
        stopWaitingForKeyPolling()

        waitingForKeyPollingJob = viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 120 // 10 minut

            Log.d("ROOM", "🔄 Czekam na klucz AES i hasło dla pokoju: ${room.name}")

            while (isActive && attempts < maxAttempts) {
                delay(5000) // Co 5 sekund

                try {
                    val requestStatus = roomRepository.checkMyJoinRequest(room.idRoom)

                    if (requestStatus == null) {
                        Log.d("ROOM", "⏳ Sprawdzam status... (attempt ${attempts + 1}/$maxAttempts)")
                        attempts++
                        continue
                    }

                    Log.d("ROOM", "📊 Status: ${requestStatus.status} (attempt ${attempts + 1}/$maxAttempts)")

                    when (requestStatus.status) {
                        "accepted" -> {
                            Log.d("ROOM", "✅ Otrzymano dane pokoju!")

                            if (!requestStatus.encryptedRoomKey.isNullOrEmpty()) {
                                keysBeingSaved.add(room.idRoom)

                                // ✅ Przekaż JSON string
                                val keyFetched = roomRepository.fetchAndDecryptRoomKey(
                                    room.idRoom,
                                    requestStatus.encryptedRoomKey  // ← To jest JSON string
                                )

                                keysBeingSaved.remove(room.idRoom)

                                if (keyFetched) {
                                    Log.d("ROOM", "✅ Dane odszyfrowane i zapisane!")
                                } else {
                                    Log.e("ROOM", "❌ Nie udało się odszyfrować")
                                }
                            } else {
                                Log.w("ROOM", "⚠️ Status 'accepted' ale brak danych")
                            }

                            break
                        }

                        "rejected" -> {
                            Log.d("ROOM", "❌ Prośba odrzucona przez admina")
                            joinRoomEventMutable.emit(ProcessEvent.Error("Your request was rejected by the admin"))
                            break
                        }

                        "pending" -> {
                            Log.d("ROOM", "⏳ Nadal oczekuje na decyzję admina...")
                        }

                        "inRoom" -> {
                            Log.d("ROOM", "✅ Już jesteś członkiem pokoju")
                            break
                        }

                        else -> {
                            Log.w("ROOM", "⚠️ Nieznany status: ${requestStatus.status}")
                        }
                    }

                    attempts++

                } catch (e: Exception) {
                    Log.e("ROOM", "❌ Błąd podczas oczekiwania", e)
                    attempts++
                }
            }

            if (attempts >= maxAttempts) {
                Log.w("ROOM", "⏱️ Timeout - nie otrzymano danych w ciągu 10 minut")
                joinRoomEventMutable.emit(ProcessEvent.Error("Admin hasn't responded yet. Please try again later."))
            }

            waitingForKeyPollingJob = null
        }
    }

    fun stopWaitingForKeyPolling() {
        waitingForKeyPollingJob?.cancel()
        waitingForKeyPollingJob = null
        Log.d("ROOM", "🛑 Zatrzymano oczekiwanie na klucz")
    }

    fun leaveRoom(){
        viewModelScope.launch {
            var isLeftRoom : Boolean = false
            val room = selectedRoom.value!!
            //TODO Call asynchronous function to user leave their room.
            isLeftRoom = roomRepository.leaveRoom(room.idRoom)
            if (isLeftRoom){
                leaveRoomEventMutable.emit(ProcessEvent.Success(Unit))
            } else { //błąd gdzieś i nie udało się
                leaveRoomEventMutable.emit(ProcessEvent.Error("Failed to leave the room. Please try again."))
            }
        }
    }
    fun removeUserFromRoom(user: UserData, room: RoomData) {
        viewModelScope.launch {
            //TODO Call function to remove user from the room.
            val isUserRemoved = roomRepository.removeUserFromRoom(room.idRoom, user.id)
            if (isUserRemoved) {
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
    fun selectRoom(room : RoomData, verifyKeyExist: Boolean = true) {
        viewModelScope.launch {
            if (verifyKeyExist && room.isPrivate) {
                // ✅ SPRAWDŹ czy klucz już nie został zapisany
                var hasKey = roomRepository.hasRoomAESKey(room.idRoom)

                if (!hasKey) {
                    Log.w("ROOM", "Brak klucza dla pokoju ${room.name}, próbuję pobrać...")

                    // Spróbuj pobrać z serwera
                    val keyFetched = roomRepository.fetchAndDecryptRoomKey(room.idRoom)

                    if (keyFetched) {
                        Log.d("ROOM", "✓ Klucz pobrany z serwera!")
                    } else {
                        // ✅ SPRAWDŹ PONOWNIE - może polling już zapisał klucz
                        delay(500) // Poczekaj chwilę
                        hasKey = roomRepository.hasRoomAESKey(room.idRoom)

                        if (hasKey) {
                            Log.d("ROOM", "✓ Klucz został zapisany przez polling!")
                        } else {
                            Log.e("ROOM", "Nie można pobrać klucza z serwera")

                            // Jako ostateczność, poproś innych użytkowników
                            Log.d("ROOM", "Proszę innych użytkowników o klucz...")
                            val requestSuccess = roomRepository.requestKeyAgain(room.idRoom)

                            if (!requestSuccess) {
                                selectedRoomEventMutable.emit(
                                    ProcessEvent.Error("Cannot access this room. Please try again later.")
                                )
                                return@launch
                            }

                            selectedRoomEventMutable.emit(
                                ProcessEvent.Error("Waiting for encryption key. Please try again in a moment.")
                            )
                            return@launch
                        }
                    }
                }
            }

            // Wejście do pokoju
            knownUserIds.clear()
            selectedRoomMutable.value = room
            Log.e("KOT", "SELECT ROOM "+room.name+ " " + room.idAdmin)

            if (selectedRoomMutable.value != null) {
                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
            } else {
                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
            }
        }
    }

//    fun selectRoom(room : RoomData, verifyKeyExist: Boolean = true) {
//        viewModelScope.launch {
//            if (verifyKeyExist && room.isPrivate) {
//                val hasKey = roomRepository.hasRoomAESKey(room.idRoom)
//
//                if (!hasKey) {
//                    Log.w("ROOM", "Brak klucza dla pokoju ${room.name}, próbuję pobrać...")
//
//                    // Spróbuj pobrać z serwera
//                    val keyFetched = roomRepository.fetchAndDecryptRoomKey(room.idRoom)
//
//                    if (!keyFetched) {
//                        Log.e("ROOM", "Nie można pobrać klucza z serwera")
//
//                        // Jako ostateczność, poproś innych użytkowników
//                        Log.d("ROOM", "Proszę innych użytkowników o klucz...")
//                        val requestSuccess = roomRepository.requestKeyAgain(room.idRoom)
//
//                        if (!requestSuccess) {
//                            selectedRoomEventMutable.emit(
//                                ProcessEvent.Error("Cannot access this room. Please try again later.")
//                            )
//                            return@launch
//                        }
//
//                        // Nawet jeśli request się powiódł, użytkownik musi poczekać
//                        selectedRoomEventMutable.emit(
//                            ProcessEvent.Error("Waiting for encryption key. Please try again in a moment.")
//                        )
//                        return@launch
//                    }
//
//                    Log.d("ROOM", "✓ Klucz pobrany z serwera!")
//                }
//            }
//
//            // Wejście do pokoju
//            knownUserIds.clear()
//            selectedRoomMutable.value = room
//            Log.e("KOT", "SELECT ROOM "+room.name+ " " + room.idAdmin)
//
//            if (selectedRoomMutable.value != null) {
//                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
//            } else {
//                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
//            }
//        }
//    }
//    fun selectRoom(room : RoomData, verifyKeyExist: Boolean = true) {
//        viewModelScope.launch {
//            //weryfikacja czy na urządzeniu jest klucz pokoju
//            if (verifyKeyExist && room.isPrivate) {
//                val hasKey = roomRepository.hasRoomAESKey(room.idRoom)
//
//                if (!hasKey) {
//                    Log.w("ROOM", "Brak klucza dla pokoju ${room.name}, próbuję pobrać...")
//
//                    // Spróbuj pobrać klucz z serwera
//                    val keyFetched = roomRepository.fetchAndDecryptRoomKey(room.idRoom)
//
//                    if (!keyFetched) {
//                        Log.e("ROOM", "Nie można pobrać klucza pokoju!")
//                        selectedRoomEventMutable.emit(
//                            ProcessEvent.Error("Cannot access this room - missing encryption key")
//                        )
//                        return@launch
//                    }
//                }
////                val result = verifyRoomKeyExist(room)
////                if (!result) {
////                    return@launch
////                }
//            }
//
//            //to się dzieje, jak jest klucz, czyli result==true
//            knownUserIds.clear() //czyszczenie listy przy zmianie pokoju
//            selectedRoomMutable.value = room
//            Log.e("KOT", "SELECT ROOM "+room.name+ " " + room.idAdmin)
//
//            if (selectedRoomMutable.value != null) {
//                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
//            } else {
//                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
//            }
//        }
//    }

    private suspend fun verifyRoomKeyExist(room: RoomData): Boolean {
        //var result = false
        // result = wywołaj funkcję, która sprawdza czy na urządzeniu jest klucz tego pokoju, jeśli jest to true, jeśli nie to false
        //wyjaśnienie: jeśli true, to dokańcza się select, jeśli false to select jest przerwyany i nie wchodzi do pokoju, ale rozesłana prośba do userów pokoju o klucz pokoju i hasło
        //result = true //to wykomentować potem

//        val hasKey = roomRepository.verifyRoomKeyExists(room.idRoom, room.isPrivate)
//        if (hasKey) {
//            Log.d("ViewModel", "User has key - allowing access")
//            return true
//        }
//
//        val result = roomRepository.requestKeyAgain(room.idRoom)

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

//            if (!::messageUtils.isInitialized) {
//                Log.e("loadMessages", "MessageUtils nie jest zainicjalizowany")
//                return@launch
//            }

        //Log.d("loadMessages", "MessageUtils jest zainicjalizowany — startuję pobieranie wiadomości")

        //Pobranie wiadomości z serwera
        Log.d("loadMessages", "Pobieram wiadomości dla pokoju=${room.idRoom}")
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
            Log.d("sendMessage", "👤 Użytkownik: id='${user.id}', nazwa='${user.name}'")
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

            try {
                val success = MessageUtils.sendMessage(room.idRoom, newMessage)

                if (success) {
                    Log.d("sendMessage", "Wiadomość wysłana poprawnie")
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
