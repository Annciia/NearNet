package com.nearnet.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
//import com.nearnet.Message
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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter



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

//event dotyczący wyniku przetwarzania jakiejś operacji asynchronicznej
sealed class ProcessEvent<out T> {
    data class Success<T>(val data: T): ProcessEvent<T>()
    data class Error(val err: String): ProcessEvent<Nothing>()
}

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel(): ViewModel() {
    lateinit var repository: UserRepository
    lateinit var roomRepository: RoomRepository
    lateinit var messageUtils: MessageUtils

    //pozwala uzywac messageUtils bez uzywania LocalContext.current w ViewModelu
    fun initMessageUtils(context: Context) {
        if (!::messageUtils.isInitialized) {
            messageUtils = MessageUtils { UserRepository.getTokenFromPreferences(context) }
        }
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
    private val myRoomsMutable = MutableStateFlow(listOf<Room>())
    val myRooms = myRoomsMutable.asStateFlow()

    //Discover rooms
    private val discoverRoomsMutable = MutableStateFlow(listOf<Room>())
    val discoverRooms = discoverRoomsMutable.asStateFlow()

    //Filtered my rooms
    private val searchMyRoomsTextMutable = MutableStateFlow("")
    val searchMyRoomsText = searchMyRoomsTextMutable.asStateFlow()
    val filteredMyRoomsList : StateFlow<List<Room>> = combine(myRooms, searchMyRoomsText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Filtered discover rooms
    private val searchDiscoverTextMutable = MutableStateFlow("")
    val searchDiscoverText = searchDiscoverTextMutable.asStateFlow()
    var filteredDiscoverList : StateFlow<List<Room>> = combine(discoverRooms, searchDiscoverText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<Room?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()
    private val selectedRoomEventMutable = MutableSharedFlow<ProcessEvent<Room>>()
    val selectedRoomEvent = selectedRoomEventMutable.asSharedFlow()

    //Register room
    private val registerRoomEventMutable = MutableSharedFlow<ProcessEvent<Room>>()
    val registerRoomEvent = registerRoomEventMutable.asSharedFlow()

    //Update room
    private val updateRoomEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val updateRoomEvent = updateRoomEventMutable.asSharedFlow()

    //Delete room
    private val deleteRoomEventMutable = MutableSharedFlow<ProcessEvent<Room?>>()
    val deleteRoomEvent = deleteRoomEventMutable.asSharedFlow()

    //Join the room
    private val joinRoomEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val joinRoomEvent = joinRoomEventMutable.asSharedFlow()

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()

    //Recent
    private val recentMutable = MutableStateFlow(listOf<Recent>())
    val recent = recentMutable.asStateFlow()

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

                repository.updateUser(userData)

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
                // konwersja RoomData -> Room
                val mappedRooms = roomsFromApi.map { rd ->
                    Room(
                        id = rd.idRoom,
                        name = rd.name,
                        description = rd.description,    // avatar jako opis, trzeba zrobic takie same klasy serw/ui
                        avatar = rd.avatar,
                        additionalSettings = "", // brak!!!!!!
                        isPrivate = rd.isPrivate,
                        isVisible = rd.isVisible,
                        idAdmin = rd.idAdmin,
                        users = emptyList()   //na serwerze tego nie ma
                    )
                }
                myRoomsMutable.value = mappedRooms
            } else {
                Log.e("loadMyRooms", "RoomRepository is not initialized!")
            }
            //roomsMutable.value = myRoomsList
        }
    }
    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            //roomsMutable.value = getRoomList()
            //roomsMutable.value = discoverRoomsList
            if (::roomRepository.isInitialized) {
                val roomsFromApi = roomRepository.getAllRooms()
                val mappedRooms = roomsFromApi.map { rd ->
                    Room(
                        id = rd.idRoom,
                        name = rd.name,
                        description = rd.description,    // avatar jako opis, trzeba zrobic takie same klasy serw/ui
                        avatar = rd.avatar,
                        additionalSettings = "", // brak!!!!!
                        isPrivate = rd.isPrivate,
                        isVisible = rd.isVisible,
                        idAdmin = rd.idAdmin,
                        users = emptyList() //lista id users należących
                    )
                }
                discoverRoomsMutable.value = mappedRooms
            } else {
                Log.e("loadDiscoverRooms", "RoomRepository is not initialized!")
            }
        }
    }
    fun createRoom(roomName : String, roomDescription : String, password: String?, passwordConfirmation: String?, isPrivate : Boolean, isVisible : Boolean, additionalSettings: String =""){
        viewModelScope.launch {
            if (!validateRoom(roomName, roomDescription, password, passwordConfirmation)) {
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                return@launch
            }
//            val createdRoom = Room(id = -1, name = roomName, description = roomDescription, isPrivate = false)
//            // TODO Call asynchronous function to create room.
//            // createdRoom.id = createRoom(createdRoom)
//            myRoomsList += createdRoom
//            discoverRoomsList += createdRoom
//            selectRoom(createdRoom)
            if (::roomRepository.isInitialized) {
                // Jeśli pokój jest publiczny to hasło jest zawsze pustym stringiem
                val createdRoomData = roomRepository.addRoom(roomName, roomDescription) //podać pozostałe argumenty: avatar, isPrivate, isVisibility, additionalSettings
                if (createdRoomData != null) {
                    val createdRoom = Room(
                        id = createdRoomData.idRoom,
                        name = createdRoomData.name,
                        description = createdRoomData.description, //tutaj dalem to co mamy jako avatar bo nie mamy opisu na serwie a mamy avatar
                        avatar = createdRoomData.avatar,
                        additionalSettings = "", // brak!!!!!!
                        isPrivate = createdRoomData.isPrivate,
                        isVisible = createdRoomData.isVisible,
                        idAdmin = createdRoomData.idAdmin,
                        users = emptyList() //lista id users należących
                    )
                    //wybranie nowego pokoju od razu przelacza do wiadomosci, po utworzeniu
                    registerRoomEventMutable.emit(ProcessEvent.Success(createdRoom))
                } else {
                    Log.e("createRoom", "❌ Nie udało się utworzyć pokoju na serwerze")
                    registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
                }
            } else {
                Log.e("createRoom", "❌ RoomRepository nie jest zainicjalizowane!")
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
            }
        }
    }
    fun updateRoom(name: String, description: String, password: String?, passwordConfirmation: String?, isPrivate: Boolean, isVisible: Boolean) {
        viewModelScope.launch {
            if (!validateRoom(name, description, password, passwordConfirmation)) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
                return@launch
            }
            val selectedRoom = selectedRoom.value
            if (selectedRoom == null) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to update room. Please try again."))
                return@launch
            }
            // TODO Call asynchronous function to update doom data.
            // Jeśli hasło jest pustym stringiem, to oznacza, że nie zostało zmienione, tak więc w bazie zostaje stare!
            // val result = updateRoom(RoomData) Marek
            val result = true

            if (result) {
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
    fun deleteRoom(room: Room) {
        viewModelScope.launch {
            val selectedRoom = selectedRoom.value
            if (selectedRoom == null) {
                updateRoomEventMutable.emit(ProcessEvent.Error("Failed to delete room. Please try again."))
                return@launch
            }
            //TODO Call asynchronous function to delete room, when user is its admin.
            //Val status = deleteRoom(idRoom) Marek
            val status = true

            if (status) {
                selectedRoomMutable.value = null
                deleteRoomEventMutable.emit(ProcessEvent.Success(null))
            } else {
                deleteRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while deleting the room."))
            }
        }
    }
    fun joinRoom(room: Room, password: String){
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

            var status :Boolean = true //wykomentować
            if (status== true){ //jeżeli publiczny lub jeżeli podam dobre hasło do prywatnego, lub jeżeli prośba zostanie wysłana do admina
                if (!room.isPrivate || (room.isPrivate && password!="")){ //hasło niewymagane lub podałam dobre hasło
                    selectRoom(room)
                }
                else{ //jeżeli prośba została wysłana do admina
                    //Popup Ania , że prośba pomyślnie wysłana do admina
                    joinRoomEventMutable.emit(ProcessEvent.Success(Unit))
                }
            }
            else{ //złe hasło podane do prywatnego lub serwer nawalił
                joinRoomEventMutable.emit(ProcessEvent.Error("Incorrect password or something went wrong. Please try joining the room again."))
            }
        }
    }
    fun filterMyRooms(filterText: String){
        searchMyRoomsTextMutable.value = filterText
    }
    fun filterDiscoverRooms(filterText: String){
        searchDiscoverTextMutable.value = filterText
    }
    fun selectRoom(room : Room) {
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
    fun loadMessages(room: Room){
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch messages here. Przefiltrowana i posortowana lista potrzebna.
            //messagesMutable.value = getMessageHistory(idRoom = room.id, offset = 0, numberOfMessages = -1)
            if (!::messageUtils.isInitialized) {
                Log.e("loadMessages", "MessageUtils nie jest zainicjalizowany")
                return@launch
            } else {
                Log.d("loadMessages", "MessageUtils jest zainicjalizowany")
            }
            //pobieranie wiadomosci
            val response = try {
                Log.d("loadMessages", "Pobieram wiadomości dla pokoju=${room.id}")
                messageUtils.requestLastMessages(room.id)
            } catch (e: Exception) {
                Log.e("loadMessages", " Błąd podczas pobierania wiadomości dla pokoju=${room.id}", e)
                null
            }

            //sprawdzenie odpowiedzi
            if (response == null) {
                Log.e("loadMessages", "Serwer zwrócił pustą odpowiedź dla pokoju=${room.id}")
                return@launch
            } else {
                Log.d("loadMessages", "Otrzymano odpowiedź z serwera dla pokoju=${room.id}: $response")
            }

            // sprawdzenie listy wiadomosci
            val messageList = response.`package`?.messageList
            if (messageList.isNullOrEmpty()) {
                Log.w("loadMessages", "Brak wiadomości w historii dla pokoju=${room.id}")
            } else {
                Log.d("loadMessages", "Serwer zwrócił ${messageList.size} wiadomości dla pokoju: ${room.id}")
            }

            // mapowanie do UI
            val messagesFromApi = messageUtils.mapPayloadToMessages(
                room.id,
                messageList ?: emptyList()
            )

//            val messagesFromApi = messageList?.map { payload ->
//                Message(
//                    id = payload.timestamp,
//                    roomId = "0", // TODO: Dane z serwera. Podany id pokoju, w którym te wiadomości wysyłane.
//                    userId = payload.userId,
//                    data = payload.data,
//                    timestamp = "2025-10-02 00:00.00.0000", // TODO: czas podany od 1970 roku
//                    messageType = "TXT",
//                    additionalData = ""
//                )
//            } ?: emptyList()

            // aktualizacja stanu
            messagesMutable.value = messagesFromApi
        }
    }
    fun sendMessage(messageText : String, room : Room){
        viewModelScope.launch{
            //val message = Message (id = -1, userNameSender = "Orci Kätter", content = messageText)
            //messagesMutable.value += message
            // TODO Call asynchronous function to send messages
            //sendMessage(room.id, message)
            if (!::messageUtils.isInitialized) {
                Log.e("sendMessage", "MessageUtils nie jest zainicjalizowane!")
                return@launch
            }

            val userName = selectedUser.value?.name ?: "brak usera"
            val timestamp = System.currentTimeMillis().toString()



            val newMessage = com.nearnet.sessionlayer.data.model.Message(
                id = timestamp,
                roomId = room.id,
                userId = userName,
                messageType = "TXT",
                message = messageText,
                additionalData = "",
                timestamp = timestamp
            )



            Log.d("sendMessage", "Wysyłam wiadomość na backend: $newMessage")

            try {
                val success = messageUtils.sendMessage(room.id, newMessage)

                if (success) {
                    Log.d("sendMessage", "Wiadomość wysłana poprawnie")
                    messagesMutable.value += newMessage
                } else {
                    Log.e("sendMessage", "Nie udało się wysłać wiadomości")
                }
            } catch (e: Exception) {
                Log.e("sendMessage", "Exception w sendMessage", e)
            }
        }
    }
    fun loadRecentMessages() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch recent messages here.
            //recentMutable.value = getRecentMessages(idUser) //zwraca listę trójek (Room, lastMessage,username)
            //funkcja: grupuje wiadomości po pokojach, dla każdej grupy uzyskuje dane pokoju, a następnie tworzy trójki
            //typu (wiadomość, pokój, nazwa użytkownika), w SQL join pokoju do wiadomości i do usera, i groupby po pokojach ,
            //a potem select na te trójki
            val backendMessages = messagesListRecent
            recentMutable.value = backendMessages // ta funkcja na teraz bierze pokój o jakimś IdRoom z Twoich na serwerze, więc jeden się wyświetla z Twoich pokoi na sztywno, reszta co się wyświetla to te o ID 0 , bo null wziął za 0. Dasz swoją funkcję to powinno działać.
                .groupBy { it.roomId } // grupowanie po pokojach
                .mapValues { (_, messagesInRoom) ->
                    val latestBackendMessage = messagesInRoom.maxByOrNull { it.timestamp }!!
                    val room = myRooms.value.find { it.id == latestBackendMessage.roomId }
                    val latestMessageUI = com.nearnet.Message(
                        id = latestBackendMessage.id,
                        roomId = latestBackendMessage.roomId,
                        userId = latestBackendMessage.userId,
                        data = latestBackendMessage.message, // backend 'message' → UI 'data'
                        timestamp = latestBackendMessage.timestamp,
                        messageType = latestBackendMessage.messageType,
                        additionalData = latestBackendMessage.additionalData
                    )
                    Recent(
                        message = latestMessageUI,
                        room = room,
                        username = latestBackendMessage.userId
                    )
                }
                .values
                .toList()
                .sortedByDescending { it.message.timestamp }
        }
    }

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
