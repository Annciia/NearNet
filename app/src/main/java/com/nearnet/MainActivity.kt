package com.nearnet

import android.graphics.Paint.Align
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nearnet.ui.component.RoomItem
import com.nearnet.ui.component.SearchField
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.NearNetViewModel
import com.nearnet.ui.theme.NearNetTheme

data class Room(val id: Int, var name: String, var description: String?, var isPrivate: Boolean)


class MainActivity : ComponentActivity() {

    private var selectedRoom: Room? = null

    @Preview
    @Composable
    fun App(){
        //var x: @Composable ()->Unit = { Text("...doc")}
        val navController = rememberNavController()
        val vm : NearNetViewModel = viewModel()
        NearNetTheme {
            CompositionLocalProvider(LocalViewModel provides vm) {
                Scaffold(
                    topBar = { TopBar(navController) },
                    bottomBar = { BottomBar(navController) },
                    content = { padding ->
                        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
                            ContentArea(navController)
                            Text("Kotek")
                            Text("Miau miau")
                            Button(onClick = {}) {
                                Text("MIAU")

                            }

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
    //Text("Bottom kitten bar.")
    //Text("Kitten bar.")

    @Composable
    fun ContentArea(navController: NavHostController) :Unit {
        NavHost(navController, startDestination = "recentScreen") {
            composable("recentScreen") { RecentScreen() }
            composable("roomsScreen") { RoomsScreen(navController) }
            composable("discoverScreen") { DiscoverScreen() }
            composable("userProfileScreen") { UserProfileScreen() }
            composable("roomConversationScreen") { RoomConversationScreen() }
        }
    }

    @Preview
    @Composable
    fun RecentScreen() : Unit {
        Text("RECENT")
    }

    private var roomNames: List<String> = listOf("Kot", "Axolotl", "Tukan", "Pomidor")
    /*private var rooms: List<Room> = listOf(
        Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
        Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
        Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
        Room(3, "Biohazard", "Be careful.", false),
        Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
        Room(5, "Fallout", null, true),
        Room(7, "My new world", "Don't join. It's private", true),
        Room(8, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
    )*/
    @Composable
    fun RoomsScreen(navController: NavHostController) : Unit {
        val rooms = LocalViewModel.current.rooms.collectAsState().value
        Column {
            Text(
                text = "My rooms",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(vertical = 20.dp)
            )
            SearchField(placeholderText = "Search rooms...", onSearch = {
                searchText -> //TODO filtrowanie po grupach
                Log.e("SEARCHED ROOM", searchText)
                Toast.makeText(this@MainActivity, searchText, Toast.LENGTH_SHORT).show()
            })
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            Text(
                text = "Found "+ rooms.size +" rooms"
            )
            Spacer(Modifier.height(8.dp).fillMaxWidth())
            LazyColumn(
                Modifier.height(400.dp).fillMaxWidth()
            ) {
                items(rooms) { room ->
                    RoomItem(room, onClick = { room ->
                        selectedRoom = room
                        navController.navigate("roomConversationScreen")
                    })
                }
                /*item {
                    Text("Bla bla bla")
                }
                item {
                    Text(text = "Bla bla bla", modifier = Modifier.padding(vertical=4.dp).background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)).padding(5.dp).fillMaxWidth()
                    )
                }
                items(20) { index ->
                    Text(
                        text = index.toString(),
                        modifier = Modifier.padding(vertical=4.dp).background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)).padding(5.dp).fillMaxWidth()
                    )
                }
                items(roomNames) { roomName ->
                    Text(
                        text = roomName,
                        modifier = Modifier.padding(vertical=4.dp).background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)).padding(5.dp).fillMaxWidth()
                    )
                }*/
            }
        }

    }

    @Preview
    @Composable
    fun DiscoverScreen() : Unit {
        Text("DISCOVER")
    }

    @Preview
    @Composable
    fun UserProfileScreen() : Unit {
        Text("USER PROFILE")
    }

    @Preview
    @Composable
    fun RoomConversationScreen() : Unit {
        Text("ROOM CONVERSATION " + selectedRoom?.name)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

}
