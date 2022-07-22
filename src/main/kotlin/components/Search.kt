package components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import data.Dictionary
import data.MutableVocabulary
import data.Word
import data.deepCopy
import kotlinx.coroutines.launch
import player.*
import state.AppState
import java.awt.Rectangle
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Search(
    state: AppState,
    vocabulary: MutableVocabulary,
    onDismissRequest:() -> Unit,
    fontFamily: FontFamily,
){
    var searchResult by remember{ mutableStateOf<Word?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    val audioPlayer = LocalAudioPlayerComponent.current
    val keyEvent: (KeyEvent) -> Boolean = {
        if (it.isCtrlPressed && it.key == Key.F && it.type == KeyEventType.KeyUp) {
            onDismissRequest()
            true
        }
        if (it.isCtrlPressed && it.key == Key.J && it.type == KeyEventType.KeyUp) {
            if (!isPlayingAudio && searchResult != null) {
                if(searchResult!!.value.isNotEmpty()){
                    val audioPath = getAudioPath(
                        word = searchResult!!.value,
                        audioSet = state.audioSet,
                        addToAudioSet = {state.audioSet.add(it)},
                        pronunciation = state.typingWord.pronunciation
                    )
                    playAudio(
                        audioPath = audioPath,
                        volume = state.global.audioVolume,
                        audioPlayerComponent = audioPlayer,
                        changePlayerState = { isPlaying -> isPlayingAudio = isPlaying },
                        setIsAutoPlay = {}
                    )
                }
            }
            true
        } else if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
            onDismissRequest()
            true
        } else false
    }

    Popup(
        alignment = Alignment.Center,
        focusable = true,
        onDismissRequest = {onDismissRequest()},
        onKeyEvent = {keyEvent(it)}
    ) {
        val scope = rememberCoroutineScope()

        val focusRequester = remember { FocusRequester() }
        var input by remember { mutableStateOf("") }

        val search:(String) -> Unit = {
            scope.launch {
                input = it
                if(searchResult != null) {
                    searchResult!!.value = ""
                }

                // 先搜索词库
                for (word in vocabulary.wordList) {
                    if(word.value == input){
                        searchResult = word.deepCopy()
                        break
                    }
                }
                // 如果词库里面没有，就搜索内置词典
                if((searchResult == null) || searchResult!!.value.isEmpty()){
                    val dictWord = Dictionary.query(input)
                    if(dictWord != null){
                        searchResult = dictWord.deepCopy()
                    }
                }

            }
        }
        Surface(
            elevation = 5.dp,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier
                .width(600.dp)
                .height(500.dp)
                .background(MaterialTheme.colors.background),
        ) {
            Box(Modifier.fillMaxSize()) {
                val stateVertical = rememberScrollState(0)
                Column(Modifier.verticalScroll(stateVertical)) {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Localized description",
                            tint = if (MaterialTheme.colors.isLight) Color.DarkGray else Color.LightGray,
                            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                        )

                        BasicTextField(
                            value = input,
                            onValueChange = { search(it) },
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
                            textStyle = MaterialTheme.typography.h5.copy(
                                color = MaterialTheme.colors.onBackground,
                                fontFamily = fontFamily
                            ),
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 5.dp, bottom = 5.dp)
                                .focusRequester(focusRequester)
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }

                    }
                    Divider()
                    if (searchResult != null && searchResult!!.value.isNotEmpty()) {

                        SearchResultInfo(
                            word = searchResult!!,
                            state = state,
                        )

                        if (searchResult!!.captions.isNotEmpty()) {
                            searchResult!!.captions.forEachIndexed { index, caption ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${index + 1}. ${caption.content}",
                                        modifier = Modifier.padding(5.dp)
                                    )

                                    val playTriple =
                                        Triple(caption, vocabulary.relateVideoPath, vocabulary.subtitlesTrackId)
                                    val playerBounds by remember {
                                        mutableStateOf(
                                            Rectangle(
                                                0,
                                                0,
                                                540,
                                                303
                                            )
                                        )
                                    }
                                    var isPlaying by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .onPointerEvent(PointerEventType.Press) { pointerEvent ->
                                                val location =
                                                    pointerEvent.awtEventOrNull?.locationOnScreen
                                                if (location != null) {
                                                    if (!isPlaying) {
                                                        isPlaying = true
                                                        playerBounds.x = location.x - 270 + 24
                                                        playerBounds.y = location.y - 320
                                                        val file = File(vocabulary.relateVideoPath)
                                                        if (file.exists()) {
                                                            scope.launch {
                                                                play(
                                                                    window = state.videoPlayerWindow,
                                                                    setIsPlaying = {
                                                                        isPlaying = it
                                                                    },
                                                                    volume = state.global.videoVolume,
                                                                    playTriple = playTriple,
                                                                    videoPlayerComponent = state.videoPlayerComponent,
                                                                    bounds = playerBounds
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Localized description",
                                            tint = MaterialTheme.colors.primary
                                        )
                                    }
                                }
                            }

                        }
                        if (searchResult!!.externalCaptions.isNotEmpty()) {
                            searchResult!!.externalCaptions.forEachIndexed { index, externalCaption ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${index + 1}. ${externalCaption.content}",
                                        modifier = Modifier.padding(5.dp)
                                    )
                                    val caption =
                                        data.Caption(externalCaption.start, externalCaption.end, externalCaption.content)
                                    val playTriple =
                                        Triple(
                                            caption,
                                            externalCaption.relateVideoPath,
                                            externalCaption.subtitlesTrackId
                                        )
                                    val playerBounds by remember {
                                        mutableStateOf(
                                            Rectangle(
                                                0,
                                                0,
                                                540,
                                                303
                                            )
                                        )
                                    }
                                    var isPlaying by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier
                                            .onPointerEvent(PointerEventType.Press) { pointerEvent ->
                                                val location =
                                                    pointerEvent.awtEventOrNull?.locationOnScreen
                                                if (location != null) {
                                                    if (!isPlaying) {
                                                        isPlaying = true
                                                        playerBounds.x = location.x - 270 + 24
                                                        playerBounds.y = location.y - 320
                                                        val file = File(externalCaption.relateVideoPath)
                                                        if (file.exists()) {
                                                            scope.launch {
                                                                play(
                                                                    window = state.videoPlayerWindow,
                                                                    setIsPlaying = {
                                                                        isPlaying = it
                                                                    },
                                                                    volume = state.global.videoVolume,
                                                                    playTriple = playTriple,
                                                                    videoPlayerComponent = state.videoPlayerComponent,
                                                                    bounds = playerBounds
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Localized description",
                                            tint = MaterialTheme.colors.primary
                                        )
                                    }
                                }
                            }

                        }

                    }else if(input.isNotEmpty()){
                        Text("没有找到相关单词")
                    }
                }
                VerticalScrollbar(
                    style = LocalScrollbarStyle.current.copy(
                        shape = if (isWindows()) RectangleShape else RoundedCornerShape(
                            4.dp
                        )
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(stateVertical)
                )
            }


        }

    }

}