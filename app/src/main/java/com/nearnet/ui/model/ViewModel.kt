package com.nearnet.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nearnet.Recent
import com.nearnet.Room
import com.nearnet.User
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import java.util.LinkedList


//var myRoomsList = listOf(
//    Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
//    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
//    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
//    Room(3, "Biohazard", "Be careful.", false),
//    Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
//    Room(5, "Fallout", null, true),
//    Room(6, "My new world", "Don't join. It's private", true),
//    Room(7, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
//)
//var discoverRoomsList = listOf(
//    Room(0, "Adventure cat games", "Dołącz do kociej przygody.", true),
//    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
//    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
//    Room(3, "Mibik game server", "Mi mi mi! It's me.", false),
//    Room(4, "My new world", "Don't join. It's private", true),
//    Room(5, "Here is the best place.", "We need you.", false),
//    Room(6, "Untitled room", "Join to title this room! ;)", false),
//    Room(7, "Stormvik games", "Witaj! Jestem wikingiem.", true),
//    Room(8, "Biohazard", "Be careful.", false),
//    Room(9, "Fallout", null, true),
//)

var messagesListRecent = listOf(
    Message(id = "0", roomId = "0", userId = "0", message = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", timestamp = "2025-09-28 15:42:17.123", messageType = "TXT", additionalData = ""),
    Message(id = "1", roomId = "0", userId = "Mauris ", message = "Proin a eros quam. Ut sit amet ultrices nisi. Pellentesque ac tristique nisl, id imperdiet est. Integer scelerisque leo at blandit blandit.", timestamp = "2025-09-28 10:15:32.849", messageType = "TXT", additionalData = ""),
    Message(id = "2", roomId = "0", userId ="Orci Kätter", message = "Fusce sed ligula turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem. Pellentesque nec facilisis erat, in pulvinar ipsum. Morbi congue viverra lectus quis fermentum. Duis sagittis est dapibus venenatis vestibulum.", timestamp = "2025-09-28 14:42:01.102", messageType = "TXT", additionalData = ""),
    Message(id = "0", roomId = "hNdyfw6w0pFiWf8vAEkhe", userId = "Orci Kätter", message = "Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem.", timestamp = "2025-09-28 18:03:44.565", messageType = "TXT", additionalData = ""),
    Message(id = "0", roomId = "7", userId ="Orci Kätter", message = "Duis sagittis est dapibus venenatis vestibulum. Non egestas in.", timestamp = "2025-09-28 18:03:44.565", messageType = "TXT", additionalData = ""),
)

//Popup's type, popup's structure
enum class PopupType {
    DELETE_USER_AUTHORIZATION,
    LOGOUT_CONFIRMATION,
    DELETE_ROOM_CONFIRMATION,
    JOIN_ROOM_CONFIRMATION,
    JOIN_ROOM_APPROVAL
}
class PopupContext(
    val type: PopupType,
    val data: Any?
)
class PopupContextApprovalData(
    val user: UserData,
    val room: RoomData
)

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

    //pozwala uzywac messageUtils bez uzywania LocalContext.current w ViewModelu
//    fun initMessageUtils(context: Context) {
//        if (!::messageUtils.isInitialized) {
//            messageUtils = MessageUtils { UserRepository.getTokenFromPreferences(context) }
//        }
//    }
    //przerobienie class na object
    fun initMessageUtils(context: Context) {
        MessageUtils.init { UserRepository.getTokenFromPreferences(context) }
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
    private val deleteUserEventMutable = MutableSharedFlow<ProcessEvent<User?>>()
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
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<RoomData?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()
    private val selectedRoomEventMutable = MutableSharedFlow<ProcessEvent<RoomData>>()
    val selectedRoomEvent = selectedRoomEventMutable.asSharedFlow()

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

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()

    //Recent
    private val recentMutable = MutableStateFlow(listOf<Recent>())
    val recent = recentMutable.asStateFlow()

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

            } catch (e: Exception) {
                Log.e("LoginError", "Failed to log in", e)
                selectedUserEventMutable.emit(ProcessEvent.Error("Login failed: ${e.message}"))
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
                //status = repository.logOutUser(user.id) //M
                status = repository.logOutUser() //M - tu po prostu czyszcze token przez co juz nic nie dostanie od serwera
            }
            selectedUserMutable.value = null
            if (status == true){
                selectedUserEventMutable.emit(ProcessEvent.Success(null))
            }
            else {
                selectedUserEventMutable.emit(ProcessEvent.Error("Something went wrong while logging out."))
            }
        }
    }
    // TODO tutaj chyba jakas oblusge/pola do additionalSettings: String musisz dorobic + nie ma aktualizacji avatara
    fun updateUser(userName: String, password: String, passwordConfirmation: String, additionalSettings: String){
        viewModelScope.launch {
            if (!validateUpdate(userName, password, passwordConfirmation, additionalSettings)) {
                updateUserEventMutable.emit(ProcessEvent.Error("Failed to update account. Please try again."))
                return@launch
            }

            val currentUser = selectedUser.value
            if (currentUser == null) {
                updateUserEventMutable.emit(ProcessEvent.Error("No user logged in."))
                return@launch
            }
            //val status : Boolean = repository.updateUser(userName, password, additionalSettings)
            try {
                val userData = com.nearnet.sessionlayer.data.model.UserData(
                    id = currentUser.id,
                    login = currentUser.login,
                    name = if (userName.isNotBlank()) userName else currentUser.name,
                    avatar = currentUser.avatar,
                    publicKey = currentUser.publicKey,
                    additionalSettings = if (additionalSettings.isNotBlank()) additionalSettings else currentUser.additionalSettings
                )

                repository.updateUser(userData, password, passwordConfirmation)

                // update lokalnego usera w stanie UI
                val updatedUser = currentUser.copy(name = userData.name)
                selectedUserMutable.value = updatedUser

                updateUserEventMutable.emit(ProcessEvent.Success(Unit))
            } catch (e: Exception) {
                Log.e("UpdateUser", "Update failed", e)
                updateUserEventMutable.emit(ProcessEvent.Error("Update failed: ${e.message}"))
            }
        }
    }

    fun validateUpdate(userName: String, password: String, passwordConfirmation: String, additionalSettings: String): Boolean {
        var result = true
        val user = selectedUser.value
        if (user == null) {
            return false
        }
        val userNameChanged = userName != user.name;
        val passwordChanged = password.isNotBlank() || passwordConfirmation.isNotBlank()
        if (!userNameChanged && !passwordChanged) {
            return false
        }
        if (userNameChanged) {
            result = result && userName.isNotBlank()
        }
        if (passwordChanged) {
            result = result && (password == passwordConfirmation)
        }
        return result
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
                deleteUserEventMutable.emit(ProcessEvent.Success(null))
            } else {
                deleteUserEventMutable.emit(ProcessEvent.Error("Something went wrong while deleting account."))
            }
        }
    }
    fun resetWelcomeState(){
        welcomeStateMutable.value = false
    }
    fun loadMyRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch my rooms here.
            //roomsMutable.value = getUserRoomList(idUser)
            if (::roomRepository.isInitialized) {
                val roomsFromApi = roomRepository.getMyRooms()
                myRoomsMutable.value = roomsFromApi
            } else {
                Log.e("loadMyRooms", "RoomRepository is not initialized!")
            }
        }
    }

    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            if (::roomRepository.isInitialized) {
                val roomsFromApi = roomRepository.getAllRooms()
                discoverRoomsMutable.value = roomsFromApi
            } else {
                Log.e("loadDiscoverRooms", "RoomRepository is not initialized!")
            }
        }
    }

    fun createRoom(
        roomName: String,
        roomDescription: String,
        password: String?,
        passwordConfirmation: String?,
        isPrivate: Boolean,
        isVisible: Boolean,
        additionalSettings: String = "",
        //avatar: String //TODO Marek
    ) {
        viewModelScope.launch {
            if (!validateRoom(roomName, roomDescription, password, passwordConfirmation)) {
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                return@launch
            }

            if (::roomRepository.isInitialized) {
                try {
                    // jeśli pokój jest publiczny, hasło będzie puste
                    val createdRoomData = roomRepository.addRoom(
                        name = roomName,
                        description = roomDescription,
                        password = password ?: "",
                        isPrivate = isPrivate,
                        isVisible = isVisible,
                        additionalSettings = additionalSettings,
                        avatar = "" //TODO Marek
                    )

                    if (createdRoomData != null) {
                        // Emitujemy bezpośrednio RoomData
                        registerRoomEventMutable.emit(ProcessEvent.Success(createdRoomData))
                    } else {
                        Log.e("createRoom", "❌ Nie udało się utworzyć pokoju na serwerze")
                        registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                    }

                } catch (e: Exception) {
                    Log.e("createRoom", "❌ Błąd podczas tworzenia pokoju", e)
                    registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                }

            } else {
                Log.e("createRoom", "❌ RoomRepository nie jest zainicjalizowane!")
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
            }
        }
    }

//    fun updateRoom(name: String, description: String, password: String?, passwordConfirmation: String?, isPrivate: Boolean, isVisible: Boolean) {
//        viewModelScope.launch {
//            if (!validateRoom(name, description, password, passwordConfirmation)) {
//                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
//                return@launch
//            }
//            val selectedRoom = selectedRoom.value
//            if (selectedRoom == null) {
//                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
//                return@launch
//            }
//            // TODO Call asynchronous function to update doom data.
//            // Jeśli hasło jest pustym stringiem, to oznacza, że nie zostało zmienione, tak więc w bazie zostaje stare!
//            // val result = updateRoom(RoomData) Marek
//            val result = true
//
//            if (result) {
//                updateRoomEventMutable.emit(ProcessEvent.Success(Unit))
//            } else {
//                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
//            }
//        }
//    }

    fun updateRoom(
        name: String,
        description: String,
        password: String?,
        passwordConfirmation: String?,
        isPrivate: Boolean,
        isVisible: Boolean,
        avatar: String
    ) {
        viewModelScope.launch {

            if (!validateRoom(name, description, password, passwordConfirmation)) {
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
                password = password ?: currentRoom.password,
                isPrivate = isPrivate,
                isVisible = isVisible,
                avatar = avatar
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


    fun validateRoom(name: String, description: String, password: String?, passwordConfirmation: String?): Boolean {
        if (name.isBlank()) {
            return false
        }
        if (name.length > ROOM_NAME_MAX_LENGTH) {
            return false
        }
        if (description.length > ROOM_DESCRIPTION_MAX_LENGTH) {
            return false
        }
        if (password != null) {
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
            //val status = true

            if (status) {
                selectedRoomMutable.value = null
                deleteRoomEventMutable.emit(ProcessEvent.Success(null))
            } else {
                deleteRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while deleting the room."))
            }
        }
    }
    fun joinRoom(room: RoomData, password: String){
        viewModelScope.launch {
            //var status : Boolean = joinRoom(room.id, ””) //funkcja dla Marka -> podawane jest id pokoju gdzie dołączam i hasło lub pusty string->
            // hasło: dla publicznego pokoju pusty string podaję, dla  prywatnego podaję hasło które użytkownik wpisał lub pusty string gdy go nie zna,
            // jak jest publiczny lub użytkownik poda hasło, to klucz do rozszyfrowania wiadomości dostaje od dowolnego użytkownika, gdzie klucz jest zaszyfrowany przez RSA (+ osobno wiadomości zaszyfrowane AES, któree tym kluczem rozszyfruje sobie)
            // jak jest pokój prywatny i użytkownik nie zna hasła, to prośba o dołączenie idzie do admina i on potwierdza i wysyła mu on ten klucz szyfrowany przez RSA (+ osobno też wiadomości zaszyfrowane AES)
            //4 przypadki!!!
            //1.pokój prywatny i nie ma hasła od użytkownika (pusty string) ->prośba do admina o dołącznie
            //2.pokój prywatny i jest hasło od użytkownika -> dołącza po sprawdzeniu poprawności z hashem na serwerze lub status=false
            //3.pokój publiczny i nie ma hasła (pusty string) -> dołącza
            //4.pokój publiczny i jest hasło - PRZYPADEK NIE MA PRAWA ZAJŚĆ, w razie czego ignorujemy hasło i wpuszczamy do pokoju ->dołącza
            //hasło do pokoju trzymane w postaci hasha na serwerze, dodawane przy tworzeniu pokoju
            //var status :Boolean = true //wykomentować
            try {
                Log.d("NearNetVM", "Attempting to join room: ${room.name} with password=${if (password.isBlank()) "<empty>" else "<provided>"}")

                var joinSuccess = false

                //TODO tutaj trzeba dodacpopup z haslem, bo w rpzeciwnym wypadku dla kazdego pokoju prwatnego nawet z haselm sie te przypadek wyzej odpala
                // publiczny lub prywatny z hasłem
                val passwordToSend = if (room.isPrivate) password else "" // publiczny zawsze pusty string
                Log.d("NearNetVM", "Joining room: ${room.name} with password=${if (passwordToSend.isBlank()) "<empty>" else "<provided>"}")

                if (::roomRepository.isInitialized) {
                    joinSuccess = roomRepository.addMyselfToRoom(room.idRoom, passwordToSend)
                    Log.d("NearNetVM", "Server returned joinSuccess=$joinSuccess for room: ${room.name}")
                } else {
                    Log.e("NearNetVM", "RoomRepository is not initialized!")
                }

                if (joinSuccess) {
                    selectRoom(room)
                    Log.d("NearNetVM", "Successfully joined room: ${room.name}")
                } else {
                    joinRoomEventMutable.emit(ProcessEvent.Error("Failed to join room — incorrect password or server error."))
                    Log.e("NearNetVM", "Could not join room: ${room.name}")
                }

            } catch (e: Exception) {
                Log.e("NearNetVM", "Exception in joinRoom", e)
                joinRoomEventMutable.emit(ProcessEvent.Error("Unexpected error while joining the room."))
            }
        }
    }
    //proba do Admina o dołączenie do pokoju
    fun joinRoomRequest(room: RoomData) {
        viewModelScope.launch {
            if (!room.isPrivate) {
                joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request — the room is public."))
                return@launch
            }
            var requestSuccess : Boolean = false
            //TODO Marek funkcja wysyłająca prośbę do Admina
            if (requestSuccess) {
                joinRoomEventMutable.emit(ProcessEvent.Success(Unit))
            } else {
                joinRoomEventMutable.emit(ProcessEvent.Error("Failed to send request — please try again."))
            }
        }
    }
    //woła się, gdy admin zatwierdzi dołączenie jakiegoś usera do pokoju
    //TODO ponawianie zrobić na serwerze jak admin nieaktywny w danym momencie, by jak wejdzie to zobaczył popup, że ktoś go pyta o dołączenie
    fun joinRoomAdminApprove(user: UserData, room: RoomData){ //jaki user i do jakiego pokoju chce dołączyć
        viewModelScope.launch {
            var approveSuccess : Boolean = false
            //TODO Marek funkcja dołączająca usera do pokoju
            if (approveSuccess){
                joinRoomAdminApproveEventMutable.emit(ProcessEvent.Success(Unit))
            } else { //błąd serwera
                joinRoomAdminApproveEventMutable.emit(ProcessEvent.Error("Failed to send approve — please approve again."))
            }
        }
    }
    fun filterMyRooms(filterText: String){
        searchMyRoomsTextMutable.value = filterText
    }
    fun filterDiscoverRooms(filterText: String){
        searchDiscoverTextMutable.value = filterText
    }
    fun selectRoom(room : RoomData) {
        viewModelScope.launch {
            loadMessages(room)
            selectedRoomMutable.value = room

            if (selectedRoomMutable.value != null) {
                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
            } else {
                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
            }
        }
    }

    fun loadMessages(room: RoomData) {
        viewModelScope.launch {
//            if (!::messageUtils.isInitialized) {
//                Log.e("loadMessages", "MessageUtils nie jest zainicjalizowany")
//                return@launch
//            }

            //Log.d("loadMessages", "MessageUtils jest zainicjalizowany — startuję pobieranie wiadomości")

            //pobranie wiadomości z serwera
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
                return@launch
            }

            val messageList = response.`package`?.messageList
            if (messageList.isNullOrEmpty()) {
                Log.w("loadMessages", "Brak wiadomości w historii dla pokoju=${room.idRoom}")
            } else {
                Log.d("loadMessages", "Otrzymano ${messageList.size} wiadomości dla pokoju=${room.idRoom}")
            }

            //pobranie listy użytkowników (żeby zamienić ID → nick)
            val userResponse = try {
                Log.d("loadMessages", "Pobieram użytkowników dla pokoju=${room.idRoom}")
                MessageUtils.requestRoomUsers(room.idRoom)
            } catch (e: Exception) {
                Log.e("loadMessages", "Błąd podczas pobierania listy użytkowników dla pokoju=${room.idRoom}", e)
                null
            }

            //mapowanie ID → nazw użytkowników
            val userMap = userResponse?.userList?.rooms
                ?.associateBy({ it.id }, { it.name })
                ?: emptyMap()

            Log.d("loadMessages", "👥 Utworzono mapę użytkowników: ${userMap.size} pozycji")

            //mapowanie wiadomości i zamiana userId na nickname
            val messagesFromApi = MessageUtils.mapPayloadToMessages(
                room.idRoom,
                messageList ?: emptyList()
            ).map { msg ->
                msg.copy(
                    userId = userMap[msg.userId] ?: msg.userId // jeśli nie znaleziono, zostaje ID
                )
            }

            //aktualizacja stanu UI
            messagesMutable.value = messagesFromApi
            Log.d("loadMessages", "Załadowano ${messagesFromApi.size} wiadomości do UI")
        }
    }


    fun sendMessage(messageText : String, room : RoomData){
        viewModelScope.launch{
            //val message = Message (id = -1, userNameSender = "Orci Kätter", content = messageText)
            //messagesMutable.value += message
            // TODO Call asynchronous function to send messages
            //sendMessage(room.id, message)
//            if (!::messageUtils.isInitialized) {
//                Log.e("sendMessage", "MessageUtils nie jest zainicjalizowane!")
//                return@launch
//            }

            val userName = selectedUser.value?.name ?: "brak usera"
            val timestamp = System.currentTimeMillis().toString()

            val newMessage = com.nearnet.sessionlayer.data.model.Message(
                id = timestamp,
                roomId = room.idRoom,
                userId = userName,
                messageType = "TXT",
                message = messageText,
                additionalData = "",
                timestamp = timestamp
            )

            Log.d("sendMessage", "Wysyłam wiadomość na backend: $newMessage")

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
    fun Room.toRoomData(): RoomData {
        return RoomData(
            idRoom = this.id,
            name = this.name,
            description = this.description,
            avatar = this.avatar,
            password = "",
            isPrivate = this.isPrivate,
            isVisible = this.isVisible,
            idAdmin = this.idAdmin,
            additionalSettings = this.additionalSettings
        )
    }

    fun startRealtime(room: RoomData) {
        val userId = selectedUser.value?.id ?: return
        stopRealtimeFlag = false

        MessageUtils.receiveMessagesStream(
            room.idRoom,
            userId,
            onMessage = { newMessages ->
                viewModelScope.launch(Dispatchers.Main) {
                    messagesMutable.update { old ->
                        (old + newMessages).distinctBy { it.id }
                    }
                }
            },
            onReconnect = {
                // jesli ktos recznie zatrzymal realtime — nie rob reconnect
                if (stopRealtimeFlag) return@receiveMessagesStream

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
            //recentMutable.value = getRecentMessages(idUser) //zwraca listę trójek (Room, lastMessage,username)
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
                    //mapowanie id -> name
                    val userMap = userResponse?.userList?.rooms
                        ?.associate { user -> user.id to user.name }
                        ?: emptyMap()

                    val messages = MessageUtils.mapPayloadToMessages(
                        room.idRoom,
                        response?.`package`?.messageList ?: emptyList()
                    ).map { msg ->
                        msg.copy(
                            userId = userMap[msg.userId] ?: msg.userId
                        )
                    }


                    val latest = messages.maxByOrNull { it.timestamp } ?: continue

                    val latestMessageUI = com.nearnet.Message(
                        id = latest.id,
                        roomId = latest.roomId,
                        userId = latest.userId,
                        data = latest.message,
                        timestamp = latest.timestamp,
                        messageType = latest.messageType,
                        additionalData = latest.additionalData
                    )

                    val recentItem = Recent(
                        message = latestMessageUI,
                        room = room,
                        username = latest.userId
                    )

                    allRecents.add(recentItem)
                }

                val sorted = allRecents.sortedByDescending { it.message.timestamp }
                recentMutable.value = sorted

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

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
