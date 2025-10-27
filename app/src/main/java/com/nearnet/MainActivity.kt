package com.nearnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.sessionlayer.logic.RoomRepository
import com.nearnet.sessionlayer.logic.UserRepository
import com.nearnet.ui.component.AvatarCircle
import com.nearnet.ui.component.AvatarPicker
import com.nearnet.ui.component.ConversationPanel
import com.nearnet.ui.component.LabeledSwitch
import com.nearnet.ui.component.MessageItem
import com.nearnet.ui.component.PasswordValidationResult
import com.nearnet.ui.component.PasswordValidationText
import com.nearnet.ui.component.PlainTextField
import com.nearnet.ui.component.PopupBox
import com.nearnet.ui.component.RoomItem
import com.nearnet.ui.component.ScreenTitle
import com.nearnet.ui.component.SearchField
import com.nearnet.ui.component.validatePassword
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.NearNetViewModel
import com.nearnet.ui.model.PopupType
import com.nearnet.ui.model.ProcessEvent
import com.nearnet.ui.model.ROOM_DESCRIPTION_MAX_LENGTH
import com.nearnet.ui.model.ROOM_DESCRIPTION_MAX_LINES
import com.nearnet.ui.model.ROOM_NAME_MAX_LENGTH
import com.nearnet.ui.theme.NearNetTheme
import kotlinx.coroutines.launch


//data class Room(val id: String, var name: String, var description: String, var avatar: String, var additionalSettings: String, var isPrivate: Boolean, var isVisible: Boolean, var idAdmin: String, var users: List<String>)
//data class Message(val id: String, val userId: String, val roomId: String, val data: String, val timestamp: String, val messageType: String, var additionalData: String)
//data class User(val id: String, val login: String, val name: String, var avatar: String, var additionalSettings: String, var publicKey: String)
data class Recent(val message: Message, val room: RoomData?, val user: UserData?)

class MainActivity : ComponentActivity() {


    @Preview
    @Composable
    fun App(){
        val navController = rememberNavController()
        val vm : NearNetViewModel = viewModel()
        //dla wiadomosci
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            vm.initMessageUtils(context)
        }

        // Reagowanie na Lifecycle dla start/stop SSE
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when(event) {
                    Lifecycle.Event.ON_START -> {
                        val room = vm.selectedRoom.value
                        val user = vm.selectedUser.value
                        if (room != null && user != null) {
                            vm.startRealtime(room)
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        vm.stopRealtime()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        vm.repository = UserRepository(this)
        vm.roomRepository = RoomRepository(this)

        ScreenObserver(navController, vm)

        NearNetTheme {
            CompositionLocalProvider(LocalViewModel provides vm) {
                Scaffold(
                    topBar = { TopBar(navController) },
                    bottomBar = { BottomBar(navController) },
                    content = { padding ->
                        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                            ContentArea(navController)
                            /*Button(onClick = {}) {
                                Text("MIAU")
                            }*/

                        }
                    }
                )
                PopupBox()
            }
        }
    }

    @Composable
    fun ScreenObserver (navController: NavHostController, vm: NearNetViewModel) {
        val navState = navController.currentBackStackEntryAsState().value
        val previousScreen = rememberSaveable{mutableStateOf<String?>(null)}
        val currentScreen = navState?.destination?.route
        if (previousScreen.value != currentScreen) {
            if (previousScreen.value == "userProfileScreen") {
                vm.resetWelcomeState()
            }
            previousScreen.value = currentScreen
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopBar(navController: NavHostController) :Unit {
        val navState = navController.currentBackStackEntryAsState().value
        val currentScreen = navState?.destination?.route
        if (currentScreen == null || currentScreen == "loginScreen" || currentScreen == "registerScreen") return
        TopAppBar(
            navigationIcon = {
                Icon(
                    imageVector = Icons.TwoTone.PlayArrow,
                    contentDescription = "Go back",
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                        .scale(scaleX = -1f, scaleY =1f)
                        .clip(shape = RoundedCornerShape(6.dp))
                        .clickable { navController.popBackStack() },
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            },
            title = {
                if (navState != null && (navState.destination.route == "roomConversationScreen" || navState.destination.route == "roomSettingsScreen")){
                    RoomTopBar(navController)
                }
                else {
                    UserTopBar(navController)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }

    @Composable
    fun UserTopBar(navController: NavController) {
        val vm = LocalViewModel.current
        val navState = navController.currentBackStackEntryAsState().value
        val selectedUser = vm.selectedUser.collectAsState().value
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(shape = RoundedCornerShape(6.dp)).clickable {
                    navController.navigate("userProfileScreen") {
                        launchSingleTop = true
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        navController.navigate("userProfileScreen") {
                            launchSingleTop = true
                        }
                    },
                    content = {
                        AvatarCircle(selectedUser?.avatar ?: "", R.drawable.spacecat)
                    }
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = selectedUser?.name ?: "Top kitten bar",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (navState != null && navState.destination.route == "userProfileScreen") {
                Spacer(Modifier.width(5.dp))
                StandardButton(
                    image = R.drawable.logout,
                    onClick = { vm.selectPopup(PopupType.LOGOUT_CONFIRMATION) }
                )
            }
        }
    }

    @Composable
    fun RoomTopBar(navController: NavController) {
        val vm = LocalViewModel.current
        val navState = navController.currentBackStackEntryAsState().value
        val selectedRoom = LocalViewModel.current.selectedRoom.collectAsState().value
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(shape = RoundedCornerShape(6.dp)).clickable {
                    navController.navigate("roomSettingsScreen") {
                        launchSingleTop = true
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        navController.navigate("roomSettingsScreen") {
                            launchSingleTop = true
                        }
                    },
                    content = {
                        AvatarCircle(selectedRoom?.avatar ?: "", R.drawable.image)
                    })
                Spacer(Modifier.width(5.dp))
                Text(
                    text = selectedRoom?.name ?: "Top kitten bar",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (navState != null && navState.destination.route == "roomConversationScreen") {
                StandardButton(
                    image=R.drawable.printer,
                    onClick = { /*navController.navigate("printerScreen") or simply print messages */ }
                )
            }
            if (navState != null && navState.destination.route == "roomSettingsScreen") {
                StandardButton(
                    image=R.drawable.leave_room,
                    onClick = { //leave room
                        vm.selectPopup(PopupType.LEAVE_ROOM_CONFIRMATION)
                    }
                )
            }
        }
    }

    @Composable
    fun StandardButton(image: Int, onClick: ()->Unit){
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            content = {
                Icon(
                    painter = painterResource(image),
                    contentDescription = "Print conversation",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }
        )
    }

    @Composable
    fun BottomBar(navController: NavHostController) :Unit {
        val navState = navController.currentBackStackEntryAsState().value
        val currentScreen = navState?.destination?.route
        if (currentScreen == null || currentScreen == "loginScreen" || currentScreen == "registerScreen") return
        BottomAppBar (
            containerColor = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(0.dp)
        ){
            Column(
                modifier = Modifier.fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .background(MaterialTheme.colorScheme.onPrimary)
                ){}
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly

                ){
                    NavigationButton(icon=R.drawable.recent, iconDescription = "recent", text = "recent", navController, screenName = "recentScreen")
                    NavigationButton(icon=R.drawable.rooms, iconDescription = "rooms", text = "rooms", navController, screenName = "roomsScreen")
                    NavigationButton(icon=R.drawable.discover, iconDescription = "discover", text = "discover", navController, screenName = "discoverScreen")
                }
            }
        }
    }

    @Composable
    fun NavigationButton(icon: Int, iconDescription: String, text: String, navController: NavHostController, screenName: String) :Unit {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .clickable { navController.navigate(screenName) }
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter= painterResource(id=icon),
                    contentDescription = iconDescription,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(vertical = 2.dp)
                )
                Text(
                    text= text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

    }


    @Composable
    fun ContentArea(navController: NavHostController) : Unit {
        NavHost(navController, startDestination = "loginScreen") {
            composable("loginScreen") { LoginScreen(navController) }
            composable("registerScreen") { RegisterScreen(navController) }
            composable("recentScreen") { RecentScreen(navController) }
            composable("roomsScreen") { RoomsScreen(navController) }
            composable("discoverScreen") { DiscoverScreen(navController) }
            composable("userProfileScreen") { UserProfileScreen(navController) }
            composable("roomConversationScreen") { RoomConversationScreen() }
            composable("createRoomScreen") { CreateOrUpdateRoomScreen(navController) }
            composable("roomSettingsScreen") { CreateOrUpdateRoomScreen(navController) }
        }
    }

    @Composable
    fun LoginScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val login = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val inProgress = remember { mutableStateOf(false) }

        //ScreenTitle("Log in or create account!")
        Box(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Application logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(200.dp).background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                )
                Spacer(Modifier.height(40.dp))
                PlainTextField(
                    placeholderText = "login",
                    singleLine = true,
                    value = login.value,
                    onValueChange = { login.value = it } // {x -> login.value = x }
                )
                Spacer(Modifier.height(10.dp))
                PlainTextField(
                    placeholderText = "password",
                    singleLine = true,
                    passwordField = true,
                    value = password.value,
                    onValueChange = { password.value = it }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        inProgress.value = true
                        vm.logInUser(login.value, password.value)
                        //tu animacja czekania na logowanie w postaci kota biegającego w kółko
                    },
                    enabled = !inProgress.value,
                    modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth()
                ) {
                    Text(text = "Sign in")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "or",
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        navController.navigate("registerScreen")
                    },
                    modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth()
                ) {
                    Text(text = "Create account")
                }
            }
        }
        LaunchedEffect(Unit) {
            vm.clearAppState()
            vm.selectedUserEvent.collect { event ->
                when (event) {
                    is ProcessEvent.Success -> {
                        if (event.data !== null) {
                            navController.navigate("recentScreen") {
                                popUpTo(0) { inclusive = true }
                            }
                            Toast.makeText(context, event.data.name, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to log in.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is ProcessEvent.Error -> {
                        Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                    }
                }
                inProgress.value = false
            }
        }
    }

    @Composable
    fun RegisterScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val login = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }
        val passwordConfirmation = remember { mutableStateOf("") }
        val inProgress = remember { mutableStateOf(false) }

        //ScreenTitle("Create your new account!")
        Box(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().height(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Application logo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(200.dp).background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                )
                Spacer(Modifier.height(40.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "Get your chat on",
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "sign up and start connecting!",
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                            //fontSize = 16.sp //default value
                        )
                    )
                }
                Spacer(Modifier.height(20.dp))
                PlainTextField(
                    placeholderText = "login",
                    singleLine = true,
                    value = login.value,
                    onValueChange = { login.value = it }
                )
                Spacer(Modifier.height(5.dp))
                PasswordValidationText(password.value, passwordConfirmation.value)
                Spacer(Modifier.height(5.dp))
                PlainTextField(
                    placeholderText = "password",
                    singleLine = true,
                    passwordField = true,
                    value = password.value,
                    onValueChange = { password.value = it }
                )
                Spacer(Modifier.height(10.dp))
                PlainTextField(
                    placeholderText = "confirm password",
                    singleLine = true,
                    passwordField = true,
                    value = passwordConfirmation.value,
                    onValueChange = { passwordConfirmation.value = it }
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        inProgress.value = true
                        vm.registerUser(login.value, password.value)
                        //tu animacja czekania na logowanie w postaci kota biegającego w kółko
                    },
                    enabled = !inProgress.value && login.value.isNotEmpty() && validatePassword(password.value, passwordConfirmation.value) == PasswordValidationResult.CORRECT,
                    modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth()
                ) {
                    Text(text = "Let's go!")
                }
            }
        }
        LaunchedEffect(Unit) {
            launch {
                vm.registerUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            vm.logInUser(login.value, password.value)
                            inProgress.value = false
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            inProgress.value = false
                        }
                    }
                }
            }
            launch {
                vm.selectedUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            if (event.data != null) {
                                navController.navigate("userProfileScreen") {
                                    popUpTo(0) { inclusive = true }
                                }
                                Toast.makeText(context, event.data.name, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to log in.", Toast.LENGTH_SHORT).show()
                                navController.navigate("loginScreen") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            navController.navigate("loginScreen") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RecentScreen(navController: NavController) : Unit {
        val vm = LocalViewModel.current
        val recents = vm.recents.collectAsState().value
        Column {
            ScreenTitle("Recent activity")
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
            ) {
                items(recents) { recent ->
                    MessageItem(
                        message = recent.message,
                        user = recent.user,
                        room = recent.room,
                        ellipse = true,
                        attachmentClickable = false,
                        onClick = { message, room ->
                        if (room != null) {
                            vm.selectRoom(room)
                            navController.navigate("roomConversationScreen")
                        }
                        else {
                            throw Error("MessageItem has null room.")
                        }
                    })
                }
            }
        }
        LaunchedEffect(Unit) {
            vm.loadRecentMessages()
        }
    }

    @Composable
    fun RoomsScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val rooms = vm.filteredMyRoomsList.collectAsState().value
        val searchText = vm.searchMyRoomsText.collectAsState().value
        val inProgess = remember { mutableStateOf(false) }
        Column {
            ScreenTitle("My rooms")
            SearchField(placeholderText = "Search rooms...", searchText=searchText, onSearch = {
                searchText ->
                    vm.filterMyRooms(searchText)
            })
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            Text(
                text = "Found "+ rooms.size +" rooms"
            )
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
            ) {
                items(rooms) { room ->
                    RoomItem(room, onClick = { room ->
                        val progress = inProgess.value
                        inProgess.value = true
                        if (!progress) vm.selectRoom(room)
                        //tu animacja czekania na wejście do pokoju w postaci kota biegającego w kółko
                    })
                }
            }
        }
        LaunchedEffect(Unit) {
            vm.loadMyRooms()

            vm.selectedRoomEvent.collect { event ->
                when (event) {
                    is ProcessEvent.Success -> {
                        navController.navigate("roomConversationScreen")
                    }
                    is ProcessEvent.Error -> {
                        Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                    }
                }
                inProgess.value = false
            }
        }
    }

    @Composable
    fun DiscoverScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val rooms = vm.filteredDiscoverList.collectAsState().value
        val searchText = vm.searchDiscoverText.collectAsState().value
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScreenTitle("Discover")
                Button(onClick = {
                    navController.navigate("createRoomScreen")
                }) {
                    Text("Create room")
                }
            }
            SearchField(placeholderText = "Search rooms...", searchText = searchText, onSearch = {
                searchText ->
                    vm.filterDiscoverRooms(searchText)
            })
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            Text(
                text = "Found "+ rooms.size +" rooms"
            )
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
            ) {
                items(rooms) { room ->
                    RoomItem(room, onClick = {
                        vm.selectPopup(PopupType.JOIN_ROOM_CONFIRMATION, room)
                    })
                }
            }
        }
        LaunchedEffect(Unit) {
            vm.loadDiscoverRooms()

            launch {
                vm.selectedRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            navController.navigate("roomConversationScreen")
                            Toast.makeText(context, "Welcome to the room!", Toast.LENGTH_SHORT).show()
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            launch {
                vm.joinRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            Toast.makeText(context, "Keep your fingers crossed for approval!", Toast.LENGTH_LONG).show()
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CreateOrUpdateRoomScreen(navController: NavController){
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val selectedRoom: RoomData? = if (navController.currentDestination?.route == "roomSettingsScreen") vm.selectedRoom.value else null
        val selectedUser: UserData? = vm.selectedUser.value
        var roomName by rememberSaveable { mutableStateOf(selectedRoom?.name ?: "") }
        var roomDescription by rememberSaveable { mutableStateOf(selectedRoom?.description ?: "") }
        var isCheckedPublic by rememberSaveable { mutableStateOf(if (selectedRoom != null) !selectedRoom.isPrivate else false) }
        var isCheckedVisible by rememberSaveable { mutableStateOf(if (selectedRoom != null) !selectedRoom.isVisible else false) }
        val avatar = rememberSaveable { mutableStateOf(selectedRoom?.avatar ?: "") }
        val password = remember { mutableStateOf("") }
        val passwordConfirmation = remember { mutableStateOf("") }
        val inProgress = remember { mutableStateOf(false) }
        fun getPassword(): String? {
            if (isCheckedPublic || (selectedRoom != null && password.value.isEmpty() && passwordConfirmation.value.isEmpty())) {
                return null
            } else {
                return password.value
            }
        }
        fun isAdminOrFree(): Boolean {
            return selectedUser !== null && selectedRoom !== null && (selectedUser.id == selectedRoom.idAdmin || selectedRoom.idAdmin.isEmpty())
        }
        Column {
            if (selectedRoom != null) { //roomSettingsScreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ScreenTitle("Room settings")
                    Button(onClick = {
                        vm.selectPopup(PopupType.USER_LIST_IN_ROOM)
                    }) {
                        Text("Users")
                    }
                }
            } else { //createRoomScreen
                ScreenTitle("Create new room")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarPicker(R.drawable.image, avatar.value, onAvatarChange = { base64 -> avatar.value = base64 })
                Spacer(Modifier.width(10.dp))
                PlainTextField(
                    value = roomName,
                    onValueChange = { text -> roomName = text },
                    placeholderText = "room name",
                    singleLine = true,
                    maxChars = ROOM_NAME_MAX_LENGTH,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                value = roomDescription,
                onValueChange = { text -> roomDescription = text },
                placeholderText = "description",
                singleLine = false,
                maxLines = ROOM_DESCRIPTION_MAX_LINES,
                maxChars = ROOM_DESCRIPTION_MAX_LENGTH,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedRoom == null || isAdminOrFree()) {
                Spacer(Modifier.height(10.dp))
                PlainTextField(
                    value = password.value,
                    onValueChange = { text -> password.value = text },
                    placeholderText = "password",
                    singleLine = true,
                    passwordField = true,
                    modifier = Modifier.fillMaxWidth(),
                    enable = !isCheckedPublic
                )
                Spacer(Modifier.height(10.dp))
                PlainTextField(
                    value = passwordConfirmation.value,
                    onValueChange = { text -> passwordConfirmation.value = text },
                    placeholderText = "confirm password",
                    singleLine = true,
                    passwordField = true,
                    modifier = Modifier.fillMaxWidth(),
                    enable = !isCheckedPublic
                )
                Spacer(Modifier.height(20.dp))
                //Switches
                LabeledSwitch(
                    title = "Allow for public access",
                    description = "When enabled, everyone can join the room without your approval.",
                    isChecked = isCheckedPublic,
                    onCheckedChange = { switchState -> isCheckedPublic = switchState })
                Spacer(Modifier.height(10.dp))
                LabeledSwitch(
                    title = "Visible only by name",
                    description = "When enabled, the room can only be found by entering its full name.",
                    isChecked = isCheckedVisible,
                    onCheckedChange = { switchState -> isCheckedVisible = switchState })
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    navController.popBackStack()
                }) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        inProgress.value = true
                        if (selectedRoom != null) { //roomSettingsScreen
                            vm.updateRoom(roomName, roomDescription, avatar.value, getPassword(), passwordConfirmation.value, !isCheckedPublic, !isCheckedVisible, "")
                        } else { //createRoomScreen
                            vm.createRoom(roomName, roomDescription, avatar.value, getPassword(), passwordConfirmation.value, !isCheckedPublic, !isCheckedVisible, "")
                        }
                        //tu animacja czekania na stworzenie pokoju w postaci kota biegającego w kółko
                    },
                    enabled = vm.validateRoom(roomName, roomDescription, getPassword(), passwordConfirmation.value) && !inProgress.value
                ) {
                    if (selectedRoom != null) { //roomSettingsScreen
                        Text("Accept")
                    } else { //createRoomScreen
                        Text("Create")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (selectedRoom != null && selectedUser != null && selectedUser.id == selectedRoom.idAdmin) { //Only the admin can delete their room.
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Button(onClick = {
                        vm.selectPopup(PopupType.DELETE_ROOM_CONFIRMATION)
                        //tu animacja czekania na stworzenie pokoju w postaci kota biegającego w kółko
                    }) {
                        Text("Delete room")
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            launch {
                vm.registerRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            val createdRoom = event.data
                            vm.selectRoom(createdRoom)
                            inProgress.value = false
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            inProgress.value = false
                        }
                    }
                }
            }
            launch {
                vm.selectedRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            navController.navigate("roomConversationScreen")
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            launch { //update
                vm.updateRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            Toast.makeText(context, "Room updated.", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                            inProgress.value = false
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            inProgress.value = false
                        }
                    }
                }
            }
            launch { //delete
                vm.deleteRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            Toast.makeText(context, "Room deleted.", Toast.LENGTH_SHORT).show()
                            navController.navigate("roomsScreen") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            launch { //leave
                vm.leaveRoomEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            Toast.makeText(context, "You have left the room.", Toast.LENGTH_SHORT).show()
                            navController.navigate("roomsScreen"){
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun UserProfileScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        var userName = rememberSaveable { mutableStateOf(vm.selectedUser.value?.name ?: "") }
        val currentPassword = remember { mutableStateOf("") }
        val newPassword = remember { mutableStateOf("") }
        val passwordConfirmation = remember { mutableStateOf("") }
        val avatar = remember { mutableStateOf(vm.selectedUser.value?.avatar ?: "") }

        //Wygląd ekranu
        Column {
            ScreenTitle("User profile settings")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarPicker(R.drawable.spacecat, avatar.value, onAvatarChange = { base64 -> avatar.value = base64 })
                Spacer(Modifier.width(10.dp))
                PlainTextField(
                    value = userName.value,
                    onValueChange = { text -> userName.value = text },
                    placeholderText = "user name",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                value = currentPassword.value,
                onValueChange = { text -> currentPassword.value = text },
                placeholderText = "current password",
                passwordField = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(5.dp))
            if (newPassword.value.isNotEmpty()) {
                PasswordValidationText(newPassword.value, passwordConfirmation.value)
            }
            Spacer(Modifier.height(5.dp))
            PlainTextField(
                value = newPassword.value,
                onValueChange = { text -> newPassword.value = text },
                placeholderText = "new password",
                singleLine = true,
                passwordField = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                value = passwordConfirmation.value,
                onValueChange = { text -> passwordConfirmation.value = text },
                placeholderText = "confirm new password",
                singleLine = true,
                passwordField = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if(!vm.welcomeState.value) {
                    Button(onClick = {
                        navController.popBackStack()
                    }) {
                        Text("Cancel")
                    }
                } else{
                    Button(onClick = {
                        navController.navigate("discoverScreen")
                    }) {
                        Text("Skip")
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        vm.updateUser(userName.value, currentPassword.value, newPassword.value, passwordConfirmation.value, avatar.value, "")
                        //tu animacja czekania na stworzenie pokoju w postaci kota biegającego w kółko
                    },
                    enabled = vm.validateUpdateUser(userName.value, currentPassword.value, newPassword.value, passwordConfirmation.value, avatar.value, "")
                ) {
                    Text("Accept")
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Button(onClick = {
                    vm.selectPopup(PopupType.DELETE_USER_AUTHORIZATION)
                    //tu animacja czekania na stworzenie pokoju w postaci kota biegającego w kółko
                }) {
                    Text("Delete account")
                }
            }
        }
        LaunchedEffect(Unit) {
            launch { //logout
                vm.selectedUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            navController.navigate("loginScreen") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            navController.navigate("loginScreen") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
            launch { //delete
                vm.deleteUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            navController.navigate("loginScreen") {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            launch { //update
                vm.updateUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            Toast.makeText(context, "Profile updated.", Toast.LENGTH_SHORT).show()
                            if (vm.welcomeState.value == false) {
                                navController.popBackStack()
                            } else {
                                navController.navigate("discoverScreen")
                            }
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RoomConversationScreen() {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val selectedRoom = vm.selectedRoom.collectAsState().value
        val messages = vm.messages.collectAsState().value
        val roomUsers = vm.roomUsers.collectAsState().value
        val listState = rememberLazyListState()
        val isLoaded = remember { mutableStateOf(false) }
        val isReady = remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            selectedRoom?.let { room ->
                vm.loadMessages(room)
                isLoaded.value = true
            }
        }

        LaunchedEffect(messages, isLoaded.value) {
            if (isLoaded.value) {
                if (messages.isNotEmpty()) {
                    listState.scrollToItem(messages.lastIndex)
                }
                isReady.value = true
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, selectedRoom) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        selectedRoom?.let { room ->
                            vm.startRealtime(room)
                            vm.startPendingRequestsPolling(room) // <-- tutaj start polling
                        }
                        //TODO
                        //selectedRoom?.let { vm.startRealtime(it) } // uruchamiamy SSE tylko jeśli jest wybrany pokój

                    }
                    Lifecycle.Event.ON_STOP -> {
                        vm.stopRealtime()
                        vm.stopPendingRequestsPolling()
                        //TODO
                        //vm.stopRealtime() // zatrzymujemy SSE
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Column {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().alpha(if (isReady.value) 1f else 0f),
                    reverseLayout = false
                ) {
                   items(messages, key = { it.id }) { message ->
                       // Looking for a user who is the author of the message
                       val user = roomUsers.find { user -> user.id == message.userId }
                       MessageItem(message = message, user = user)
                    }
                }
                if (!isReady.value) {
                    CircularProgressIndicator()
                    //tu animacja czekania na stworzenie pokoju w postaci kota biegającego w kółko
                }
            }
            ConversationPanel()
        }

        LaunchedEffect(Unit) {
            vm.sendMessageEvent.collect { event ->
                if (event is ProcessEvent.Error) {
                    Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

}
