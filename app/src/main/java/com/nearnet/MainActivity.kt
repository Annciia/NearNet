package com.nearnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nearnet.sessionlayer.logic.MessageUtils
import com.nearnet.sessionlayer.logic.RoomRepository
import com.nearnet.sessionlayer.logic.UserRepository
import com.nearnet.ui.component.ConversationPanel
import com.nearnet.ui.component.MessageItem
import com.nearnet.ui.component.PlainTextField
import com.nearnet.ui.component.RoomItem
import com.nearnet.ui.component.ScreenTitle
import com.nearnet.ui.component.SearchField
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.NearNetViewModel
import com.nearnet.ui.model.ProcessEvent
import com.nearnet.ui.theme.NearNetTheme
import kotlinx.coroutines.launch

data class Room(val id: String, var name: String, var description: String?, var isPrivate: Boolean)
data class Message(val id: String, val userNameSender: String, val content: String)
data class User(val id: String, val login: String, val password: String, val name: String)

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
        vm.repository = UserRepository(this)
        vm.roomRepository = RoomRepository(this)


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
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopBar(navController: NavHostController) :Unit {
        TopAppBar(
            navigationIcon = {IconButton(
                onClick = {navController.popBackStack()},
                content={Icon(
                    imageVector = Icons.TwoTone.PlayArrow,
                    contentDescription = "Go back",
                    modifier = Modifier.scale(scaleX = -1f, scaleY =1f)
                )},
                colors = IconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.primary
                )
            )},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.navigate("userProfileScreen") }, content = {
                        Image(
                            painter = painterResource((R.drawable.ic_launcher_foreground)),
                            contentDescription = "Avatar",
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                        )
                    })
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Top kitten bar.",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }

    @Composable
    fun BottomBar(navController: NavHostController) :Unit {
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
                    //verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly

                ){
                    NavigationButton(icon=R.drawable.ic_launcher_foreground, iconDescription = "recent", text = "recent", navController, screenName = "recentScreen")
                    NavigationButton(icon=R.drawable.ic_launcher_foreground, iconDescription = "rooms", text = "rooms", navController, screenName = "roomsScreen")
                    NavigationButton(icon=R.drawable.ic_launcher_foreground, iconDescription = "discover", text = "discover", navController, screenName = "discoverScreen")
                }
            }
        }
    }

    @Composable
    fun NavigationButton(icon: Int, iconDescription: String, text: String, navController: NavHostController, screenName: String) :Unit {
        Button(
            onClick = { navController.navigate(screenName)},
            modifier = Modifier.padding(0.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter= painterResource(id=icon),
                    contentDescription = iconDescription,
                    modifier = Modifier
                        .size(40.dp)
                        //.background(MaterialTheme.colorScheme.secondary)
                )
                Text(
                    text= text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

    }


    @Composable
    fun ContentArea(navController: NavHostController) :Unit {
        NavHost(navController, startDestination = "loginScreen") {
            composable("loginScreen") { LoginScreen(navController) }
            composable("registerScreen") { RegisterScreen(navController) }
            composable("recentScreen") { RecentScreen() }
            composable("roomsScreen") { RoomsScreen(navController) }
            composable("discoverScreen") { DiscoverScreen(navController) }
            composable("userProfileScreen") { UserProfileScreen() }
            composable("roomConversationScreen") { RoomConversationScreen() }
            composable("createRoomScreen") { CreateRoomScreen(navController) }
        }
    }

    @Composable
    fun LoginScreen(navController: NavController) : Unit {
        val context = LocalContext.current
        val vm = LocalViewModel.current
        val login = remember { mutableStateOf("") }
        val password = remember { mutableStateOf("") }

        ScreenTitle("Log in or create account!")
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Application logo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(200.dp).background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
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
                value = password.value,
                onValueChange = { password.value = it }
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    vm.logInUser(login.value, password.value)
                    //tu animacja czekania na logowanie w postaci kota biegającego w kółko
                },
                modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth()
            ) {
                Text(text= "Sign in")
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
                Text(text= "Create account")
            }
            Spacer(Modifier.height(30.dp))
        }
        LaunchedEffect(Unit) {
            vm.selectedUserEvent.collect { event ->
                when (event) {
                    is ProcessEvent.Success -> {
                        navController.navigate("recentScreen") {
                            popUpTo("loginScreen") { inclusive = true }
                        }
                        Toast.makeText(context, event.data.name, Toast.LENGTH_SHORT).show()
                    }
                    is ProcessEvent.Error -> {
                        Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                    }
                }
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

        ScreenTitle("Create your new account!")
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                placeholderText = "password",
                singleLine = true,
                value = password.value,
                onValueChange = { password.value = it }
            )
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                placeholderText = "confirm password",
                singleLine = true,
                value = passwordConfirmation.value,
                onValueChange = { passwordConfirmation.value = it }
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    vm.registerUser(login.value, password.value)
                    //tu animacja czekania na logowanie w postaci kota biegającego w kółko
                },
                modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth()
            ) {
                Text(text= "Let's go!")
            }
            Spacer(Modifier.height(30.dp))
        }
        LaunchedEffect(Unit) {
            launch {
                vm.registerUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            vm.logInUser(login.value, password.value)
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            launch {
                vm.selectedUserEvent.collect { event ->
                    when (event) {
                        is ProcessEvent.Success -> {
                            navController.navigate("userProfileScreen") {
                                popUpTo("registerScreen") { inclusive = true }
                            }
                            Toast.makeText(context, event.data.name, Toast.LENGTH_SHORT).show()
                        }
                        is ProcessEvent.Error -> {
                            Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                            navController.navigate("loginScreen") {
                                popUpTo("registerScreen") { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RecentScreen() : Unit {
        ScreenTitle("Recent activity")
    }

    @Composable
    fun RoomsScreen(navController: NavController) : Unit {
        LocalViewModel.current.loadMyRooms()
        val vm = LocalViewModel.current
        val rooms = vm.filteredMyRoomsList.collectAsState().value
        val searchText = vm.searchRoomText.collectAsState().value
        Column {
            ScreenTitle("My rooms")
            SearchField(placeholderText = "Search rooms...", searchText=searchText, onSearch = {
                searchText ->
                    vm.filterMyRooms(searchText)
                    //Log.e("SEARCHED ROOM", searchText)
                    //Toast.makeText(this@MainActivity, searchText, Toast.LENGTH_SHORT).show()
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
                        vm.selectRoom(room)
                        navController.navigate("roomConversationScreen")
                    })
                }
            }
        }

    }

    @Composable
    fun DiscoverScreen(navController: NavController) : Unit {
        LocalViewModel.current.loadDiscoverRooms()
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
                        //TODO Dołączanie do pokoju
                        //vm.selectRoom(room)
                        //navController.navigate("roomConversationScreen")
                    })
                }
            }
        }
    }

    @Composable
    fun CreateRoomScreen(navController: NavController){
        val vm = LocalViewModel.current
        var roomName by rememberSaveable { mutableStateOf("") }
        var roomDescription by rememberSaveable { mutableStateOf("") }
        Column {
            ScreenTitle("Create new room")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Room icon",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(100.dp).background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                PlainTextField(
                    value = roomName,
                    onValueChange = { text -> roomName = text },
                    placeholderText = "room name",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            PlainTextField(
                value = roomDescription,
                onValueChange = { text -> roomDescription = text },
                placeholderText = "description",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    vm.createRoom(roomName, roomDescription)
                    navController.navigate("roomConversationScreen")
                }) {
                    Text("Create")
                }
            }
        }

    }

    @Preview
    @Composable
    fun UserProfileScreen() : Unit {
        ScreenTitle("User profile")
    }

    @Composable
    fun RoomConversationScreen() : Unit {
        val vm = LocalViewModel.current

        val selectedRoom = vm.selectedRoom.collectAsState().value
        val messages = vm.messages.collectAsState().value
        //val selectedRoom = LocalViewModel.current.selectedRoom.collectAsState().value
        val listState = rememberLazyListState()

        //val messages = LocalViewModel.current.messages.collectAsState().value

        // pobieranie historii wiadomości przy wejściu na ekran lub zmianie pokoju
        LaunchedEffect(vm.selectedRoom.collectAsState().value) {
            val room = vm.selectedRoom.value
            if (room != null) {
                Log.d("RoomConversation", "Loading messages for room: ${room.name}")
                vm.loadMessages(room)
            }
        }

        Column {
            Text("ROOM CONVERSATION " + selectedRoom?.name)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ){
                items(messages) {message ->
                    MessageItem(message)
                }
            }
            ConversationPanel()
        }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
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
