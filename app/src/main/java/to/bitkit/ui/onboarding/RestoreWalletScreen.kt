package to.bitkit.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.bitkit.R
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.ButtonSize
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.SecondaryButton
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.AppTopBar
import to.bitkit.ui.theme.AppTextFieldDefaults
import to.bitkit.ui.theme.AppTextStyles
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent
import to.bitkit.viewmodels.RestoreWalletViewModel

@Composable
fun RestoreWalletView(
    viewModel: RestoreWalletViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onRestoreClick: (mnemonic: String, passphrase: String?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val inputFieldPositions = remember { mutableMapOf<Int, Int>() }
    val focusRequesters = remember { List(24) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.shouldDismissKeyboard) {
        if (uiState.shouldDismissKeyboard) {
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.onKeyboardDismissed()
        }
    }

    LaunchedEffect(uiState.scrollToFieldIndex) {
        uiState.scrollToFieldIndex?.let { index ->
            inputFieldPositions[index]?.let { position ->
                scrollState.animateScrollTo(position)
            }
            viewModel.onScrollCompleted()
        }
    }

    LaunchedEffect(uiState.focusedIndex) {
        uiState.focusedIndex?.let { index ->
            focusRequesters[index].requestFocus()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                titleText = null,
                onBackClick = onBackClick,
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(scrollState)
            ) {
                Display(stringResource(R.string.onboarding__restore_header).withAccent(accentColor = Colors.Blue))
                VerticalSpacer(8.dp)
                BodyM(
                    text = stringResource(R.string.onboarding__restore_phrase),
                    color = Colors.White80,
                )
                VerticalSpacer(32.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // First column (1-6 or 1-12)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        for (index in 0 until uiState.wordsPerColumn) {
                            MnemonicInputField(
                                label = "${index + 1}.",
                                value = uiState.words[index],
                                isError = index in uiState.invalidWordIndices && uiState.focusedIndex != index,
                                onValueChanged = { viewModel.onWordChanged(index, it) },
                                onFocusChanged = { focused ->
                                    viewModel.onWordFocusChanged(index, focused)
                                },
                                onPositionChanged = { position ->
                                    inputFieldPositions[index] = position
                                },
                                onBackspaceInEmpty = {
                                    viewModel.onBackspaceInEmpty(index)
                                },
                                focusRequester = focusRequesters[index],
                                index = index,
                            )
                        }
                    }
                    // Second column (7-12 or 13-24)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        for (index in uiState.wordsPerColumn until (uiState.wordsPerColumn * 2)) {
                            MnemonicInputField(
                                label = "${index + 1}.",
                                value = uiState.words[index],
                                isError = index in uiState.invalidWordIndices && uiState.focusedIndex != index,
                                onValueChanged = { viewModel.onWordChanged(index, it) },
                                onFocusChanged = { focused ->
                                    viewModel.onWordFocusChanged(index, focused)
                                },
                                onPositionChanged = { position ->
                                    inputFieldPositions[index] = position
                                },
                                onBackspaceInEmpty = {
                                    viewModel.onBackspaceInEmpty(index)
                                },
                                focusRequester = focusRequesters[index],
                                index = index,
                            )
                        }
                    }
                }

                // Passphrase
                if (uiState.showingPassphrase) {
                    OutlinedTextField(
                        value = uiState.bip39Passphrase,
                        onValueChange = { viewModel.onPassphraseChanged(it) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.onboarding__restore_passphrase_placeholder)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AppTextFieldDefaults.semiTransparent,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.None,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("PassphraseInput")
                    )
                    VerticalSpacer(16.dp)
                    BodyS(
                        text = stringResource(R.string.onboarding__restore_passphrase_meaning),
                        color = Colors.White64,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(16.dp)
                        .weight(1f)
                )

                AnimatedVisibility(visible = uiState.invalidWordIndices.any { it != uiState.focusedIndex }) {
                    BodyS(
                        text = stringResource(
                            R.string.onboarding__restore_red_explain
                        ).withAccent(accentColor = Colors.Red),
                        color = Colors.White64,
                        modifier = Modifier.padding(top = 21.dp)
                    )
                }

                AnimatedVisibility(visible = uiState.checksumErrorVisible) {
                    BodyS(
                        text = stringResource(R.string.onboarding__restore_inv_checksum),
                        color = Colors.Red,
                        modifier = Modifier.padding(top = 21.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                ) {
                    AnimatedVisibility(visible = !uiState.showingPassphrase, modifier = Modifier.weight(1f)) {
                        SecondaryButton(
                            text = stringResource(R.string.onboarding__advanced),
                            onClick = { viewModel.onAdvancedClick() },
                            enabled = uiState.areButtonsEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("AdvancedButton")
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.onboarding__restore),
                        onClick = {
                            onRestoreClick(uiState.bip39Mnemonic, uiState.bip39Passphrase.takeIf { it.isNotEmpty() })
                        },
                        enabled = uiState.areButtonsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("RestoreButton")
                    )
                }
            }

            SuggestionsRow(
                suggestions = uiState.suggestions,
                onSelect = { viewModel.onSuggestionSelected(it) }
            )
        }
    }
}

@Composable
private fun BoxScope.SuggestionsRow(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Colors.Black)
                .padding(horizontal = 32.dp, vertical = 8.dp)
        ) {
            BodyS(
                text = stringResource(R.string.onboarding__restore_suggestions),
                color = Colors.White64,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                suggestions.forEach { suggestion ->
                    PrimaryButton(
                        text = suggestion,
                        onClick = { onSelect(suggestion) },
                        size = ButtonSize.Small,
                        fullWidth = false
                    )
                }
            }
        }
    }
}

@Composable
fun MnemonicInputField(
    label: String,
    isError: Boolean = false,
    value: String,
    onValueChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onPositionChanged: (Int) -> Unit,
    onBackspaceInEmpty: () -> Unit,
    focusRequester: FocusRequester,
    index: Int,
) {
    var isFocused by remember { mutableStateOf(false) }
    val textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { onValueChanged(it.text) },
        textStyle = if (isFocused) AppTextStyles.BodySSB else AppTextStyles.BodyS,
        prefix = {
            Text(
                text = label,
                color = if (isError) Colors.Red else Colors.White64,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 4.dp)
            )
        },
        isError = isError,
        shape = RoundedCornerShape(8.dp),
        colors = AppTextFieldDefaults.semiTransparent,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.None,
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Backspace &&
                    keyEvent.type == KeyEventType.KeyDown &&
                    value.isEmpty()
                ) {
                    onBackspaceInEmpty()
                    true
                } else {
                    false
                }
            }
            .testTag("Word-$index")
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInParent().y.toInt() * 2 // double the scroll to ensure enough space
                onPositionChanged(position)
            }
    )
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    AppThemeSurface {
        RestoreWalletView(
            onBackClick = {},
            onRestoreClick = { _, _ -> },
        )
    }
}
