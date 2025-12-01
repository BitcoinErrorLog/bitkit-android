package to.bitkit.ui.screens.wallets.receive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.NEXUS_5
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lightningdevkit.ldknode.ChannelDetails
import to.bitkit.R
import to.bitkit.ext.setClipboardText
import to.bitkit.ext.truncate
import to.bitkit.models.NodeLifecycleState
import to.bitkit.ui.components.BodyM
import to.bitkit.ui.components.BodyS
import to.bitkit.ui.components.BottomSheetPreview
import to.bitkit.ui.components.ButtonSize
import to.bitkit.ui.components.Caption13Up
import to.bitkit.ui.components.Display
import to.bitkit.ui.components.PrimaryButton
import to.bitkit.ui.components.QrCodeImage
import to.bitkit.ui.components.Tooltip
import to.bitkit.ui.components.VerticalSpacer
import to.bitkit.ui.scaffold.SheetTopBar
import to.bitkit.ui.screens.wallets.activity.components.CustomTabRowWithSpacing
import to.bitkit.ui.shared.effects.SetMaxBrightness
import to.bitkit.ui.shared.modifiers.sheetHeight
import to.bitkit.ui.shared.util.gradientBackground
import to.bitkit.ui.shared.util.shareQrCode
import to.bitkit.ui.shared.util.shareText
import to.bitkit.ui.theme.AppShapes
import to.bitkit.ui.theme.AppThemeSurface
import to.bitkit.ui.theme.Colors
import to.bitkit.ui.utils.withAccent
import to.bitkit.viewmodels.MainUiState

@Composable
fun ReceiveQrScreen(
    cjitInvoice: MutableState<String?>,
    walletState: MainUiState,
    onClickEditInvoice: () -> Unit,
    onClickReceiveCjit: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: ReceiveTab? = null,
) {
    SetMaxBrightness()

    val hasUsableChannels = walletState.channels.any { it.isUsable }

    // Tab selection state
    var selectedTab by remember {
        mutableStateOf(
            initialTab ?: if (hasUsableChannels) {
                ReceiveTab.AUTO
            } else {
                ReceiveTab.SAVINGS
            }
        )
    }

    var showDetails by remember { mutableStateOf(false) }

    val visibleTabs = remember(hasUsableChannels) {
        buildList {
            add(ReceiveTab.SAVINGS)
            if (hasUsableChannels) {
                add(ReceiveTab.AUTO)
            }
            add(ReceiveTab.SPENDING)
        }
    }

    val currentTabIndex = remember(selectedTab, visibleTabs) {
        visibleTabs.indexOf(selectedTab)
    }

    val showingCjitOnboarding = remember(selectedTab, walletState) {
        selectedTab == ReceiveTab.SPENDING && !hasUsableChannels && walletState.nodeLifecycleState.isRunning()
    }

    // Auto-correct selected tab if it becomes hidden
    LaunchedEffect(visibleTabs) {
        if (selectedTab !in visibleTabs) {
            selectedTab = visibleTabs.first()
        }
    }

    // Current invoice for display
    val currentInvoice = remember(selectedTab, walletState, cjitInvoice.value) {
        getInvoiceForTab(
            tab = selectedTab,
            bip21 = walletState.bip21,
            bolt11 = walletState.bolt11,
            cjitInvoice = cjitInvoice.value,
            isNodeRunning = walletState.nodeLifecycleState.isRunning(),
            onchainAddress = walletState.onchainAddress
        )
    }

    // QR logo based on selected tab
    val qrLogoRes = remember(selectedTab) {
        getQrLogoResource(selectedTab)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .gradientBackground()
            .navigationBarsPadding()
            .keepScreenOn()
    ) {
        SheetTopBar(stringResource(R.string.wallet__receive_bitcoin))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Tab row
            CustomTabRowWithSpacing(
                tabs = visibleTabs,
                currentTabIndex = currentTabIndex,
                selectedColor = when (selectedTab) {
                    ReceiveTab.SAVINGS -> Colors.Brand
                    ReceiveTab.AUTO -> Colors.White
                    ReceiveTab.SPENDING -> Colors.Purple
                },
                onTabChange = { tab ->
                    selectedTab = tab
                }
            )

            Spacer(Modifier.height(24.dp))

            // Content area (QR or Details)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .swipeToChangeTab(
                        currentTabIndex = currentTabIndex,
                        tabCount = visibleTabs.size,
                        onTabChange = { newIndex ->
                            selectedTab = visibleTabs[newIndex]
                        }
                    )
            ) {
                when {
                    showingCjitOnboarding -> {
                        CjitOnBoardingView(
                            modifier = Modifier.weight(1f)
                        )
                    }

                    showDetails -> {
                        ReceiveDetailsView(
                            tab = selectedTab,
                            onchainAddress = walletState.onchainAddress,
                            bolt11 = walletState.bolt11,
                            cjitInvoice = cjitInvoice.value,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        ReceiveQrView(
                            uri = currentInvoice,
                            qrLogoPainter = painterResource(qrLogoRes),
                            onClickEditInvoice = onClickEditInvoice,
                            tab = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(visible = walletState.nodeLifecycleState.isRunning()) {
                PrimaryButton(
                    text = stringResource(
                        when {
                            showingCjitOnboarding -> R.string.wallet__receive__cjit
                            showDetails -> R.string.wallet__receive_show_qr
                            else -> R.string.wallet__receive_show_details
                        }
                    ),
                    icon = {
                        if (showingCjitOnboarding) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lightning_alt),
                                tint = Colors.Purple,
                                contentDescription = null
                            )
                        }
                    },
                    onClick = {
                        if (showingCjitOnboarding) {
                            onClickReceiveCjit()
                            showDetails = false
                        } else {
                            showDetails = !showDetails
                        }
                    },
                    fullWidth = true,
                    modifier = Modifier.testTag("ReceiveToggleButton")
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveQrView(
    uri: String,
    qrLogoPainter: Painter,
    onClickEditInvoice: () -> Unit,
    modifier: Modifier = Modifier,
    tab: ReceiveTab,
) {
    val context = LocalContext.current
    val qrButtonTooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        QrCodeImage(
            content = uri,
            logoPainter = qrLogoPainter,
            tipMessage = androidx.compose.ui.res.stringResource(R.string.wallet__receive_copied),
            onBitmapGenerated = { bitmap -> qrBitmap = bitmap },
            testTag = "QRCode",
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.common__edit),
                size = ButtonSize.Small,
                onClick = onClickEditInvoice,
                fullWidth = false,
                color = Colors.White10,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_pencil_simple),
                        contentDescription = null,
                        tint = when (tab) {
                            ReceiveTab.SAVINGS -> Colors.Brand
                            ReceiveTab.AUTO -> Colors.Brand
                            ReceiveTab.SPENDING -> Colors.Purple
                        },
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("SpecifyInvoiceButton")
            )
            Tooltip(
                text = androidx.compose.ui.res.stringResource(R.string.wallet__receive_copied),
                tooltipState = qrButtonTooltipState
            ) {
                PrimaryButton(
                    text = androidx.compose.ui.res.stringResource(R.string.common__copy),
                    size = ButtonSize.Small,
                    onClick = {
                        context.setClipboardText(uri)
                        coroutineScope.launch { qrButtonTooltipState.show() }
                    },
                    fullWidth = false,
                    color = Colors.White10,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            tint = when (tab) {
                                ReceiveTab.SAVINGS -> Colors.Brand
                                ReceiveTab.AUTO -> Colors.Brand
                                ReceiveTab.SPENDING -> Colors.Purple
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("ReceiveCopyQR")
                )
            }
            PrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.common__share),
                size = ButtonSize.Small,
                onClick = {
                    qrBitmap?.let { bitmap ->
                        shareQrCode(context, bitmap, uri)
                    } ?: shareText(context, uri)
                },
                fullWidth = false,
                color = Colors.White10,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = when (tab) {
                            ReceiveTab.SAVINGS -> Colors.Brand
                            ReceiveTab.AUTO -> Colors.Brand
                            ReceiveTab.SPENDING -> Colors.Purple
                        },
                        modifier = Modifier.size(18.dp)
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CjitOnBoardingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(AppShapes.small)
            .background(color = Colors.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Display("Receive on <accent>spending balance</accent>".withAccent(accentColor = Colors.Purple))
        VerticalSpacer(8.dp)
        BodyM(
            "Enjoy instant and cheap\ntransactions with friends, family,\nand merchants.",
            color = Colors.White64,
            modifier = Modifier.fillMaxWidth()
        )
        VerticalSpacer(32.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lightning_alt),
                tint = Colors.Purple,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.TopCenter)
            )
            Icon(
                painter = painterResource(R.drawable.arrow),
                tint = Colors.Purple,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ReceiveDetailsView(
    tab: ReceiveTab,
    onchainAddress: String,
    bolt11: String,
    cjitInvoice: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Colors.Black),
        shape = AppShapes.small,
        modifier = modifier
    ) {
        Column {
            when (tab) {
                ReceiveTab.SAVINGS -> {
                    if (onchainAddress.isNotEmpty()) {
                        CopyAddressCard(
                            title = stringResource(R.string.wallet__receive_bitcoin_invoice),
                            address = onchainAddress,
                            type = CopyAddressType.ONCHAIN,
                            testTag = "ReceiveOnchainAddress",
                        )
                    }
                }

                ReceiveTab.AUTO -> {
                    // Show both onchain AND lightning if available
                    if (onchainAddress.isNotEmpty()) {
                        CopyAddressCard(
                            title = androidx.compose.ui.res.stringResource(R.string.wallet__receive_bitcoin_invoice),
                            address = onchainAddress,
                            type = CopyAddressType.ONCHAIN,
                            testTag = "ReceiveOnchainAddress",
                        )
                    }
                    if (cjitInvoice != null || bolt11.isNotEmpty()) {
                        CopyAddressCard(
                            title = androidx.compose.ui.res.stringResource(R.string.wallet__receive_lightning_invoice),
                            address = cjitInvoice ?: bolt11,
                            type = CopyAddressType.LIGHTNING,
                            testTag = "ReceiveLightningAddress",
                        )
                    }
                }

                ReceiveTab.SPENDING -> {
                    if (cjitInvoice != null || bolt11.isNotEmpty()) {
                        CopyAddressCard(
                            title = androidx.compose.ui.res.stringResource(R.string.wallet__receive_lightning_invoice),
                            address = cjitInvoice ?: bolt11,
                            type = CopyAddressType.LIGHTNING,
                            testTag = "ReceiveLightningAddress",
                        )
                    }
                }
            }
        }
    }
}

enum class CopyAddressType { ONCHAIN, LIGHTNING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyAddressCard(
    title: String,
    address: String,
    type: CopyAddressType,
    testTag: String? = null,
) {
    val context = LocalContext.current

    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row {
            Caption13Up(text = title, color = Colors.White64)

            Spacer(modifier = Modifier.width(3.dp))

            val iconRes = if (type == CopyAddressType.ONCHAIN) R.drawable.ic_bitcoin else R.drawable.ic_lightning_alt
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = Colors.White64)
        }
        Spacer(modifier = Modifier.height(16.dp))
        BodyS(
            text = address.truncate(32).uppercase(),
            modifier = testTag?.let { Modifier.testTag(it) } ?: Modifier
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Tooltip(
                text = stringResource(R.string.wallet__receive_copied),
                tooltipState = tooltipState,
            ) {
                PrimaryButton(
                    text = stringResource(R.string.common__copy),
                    size = ButtonSize.Small,
                    onClick = {
                        context.setClipboardText(address)
                        coroutineScope.launch { tooltipState.show() }
                    },
                    fullWidth = false,
                    color = Colors.White10,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            tint = if (type == CopyAddressType.ONCHAIN) Colors.Brand else Colors.Purple,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                )
            }
            PrimaryButton(
                text = stringResource(R.string.common__share),
                size = ButtonSize.Small,
                onClick = { shareText(context, address) },
                fullWidth = false,
                color = Colors.White10,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        tint = if (type == CopyAddressType.ONCHAIN) Colors.Brand else Colors.Purple,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Suppress("SpellCheckingInspection")
@Preview(showSystemUi = true, name = "Savings Mode")
@Composable
private fun PreviewSavingsMode() {
    AppThemeSurface {
        BottomSheetPreview {
            ReceiveQrScreen(
                cjitInvoice = remember { mutableStateOf(null) },
                walletState = MainUiState(
                    nodeLifecycleState = NodeLifecycleState.Running,
                    onchainAddress = "bcrt1qfserxgtuesul4m9zva56wzk849yf9l8rk4qy0l",
                    channels = emptyList()
                ),
                onClickEditInvoice = {},
                modifier = Modifier.sheetHeight(),
                initialTab = ReceiveTab.SAVINGS,
                onClickReceiveCjit = {},
            )
        }
    }
}

@Suppress("SpellCheckingInspection")
@Preview(showSystemUi = true, name = "Auto Mode")
@Composable
private fun PreviewAutoMode() {
    // Mock channel for preview (AUTO tab requires non-empty channels list)
    val mockChannel = ChannelDetails(
        channelId = "0".repeat(64),
        counterpartyNodeId = "0".repeat(66),
        fundingTxo = null,
        shortChannelId = null,
        outboundScidAlias = null,
        inboundScidAlias = null,
        channelValueSats = 1000000uL,
        unspendablePunishmentReserve = null,
        userChannelId = "0".repeat(32),
        feerateSatPer1000Weight = 1000u,
        outboundCapacityMsat = 500000000uL,
        inboundCapacityMsat = 500000000uL,
        confirmationsRequired = null,
        confirmations = null,
        isOutbound = true,
        isChannelReady = true,
        isUsable = true,
        isAnnounced = false,
        cltvExpiryDelta = null,
        counterpartyUnspendablePunishmentReserve = 0uL,
        counterpartyOutboundHtlcMinimumMsat = null,
        counterpartyOutboundHtlcMaximumMsat = null,
        counterpartyForwardingInfoFeeBaseMsat = null,
        counterpartyForwardingInfoFeeProportionalMillionths = null,
        counterpartyForwardingInfoCltvExpiryDelta = null,
        nextOutboundHtlcLimitMsat = 0uL,
        nextOutboundHtlcMinimumMsat = 0uL,
        forceCloseSpendDelay = null,
        inboundHtlcMinimumMsat = 0uL,
        inboundHtlcMaximumMsat = null,
        config = org.lightningdevkit.ldknode.ChannelConfig(
            forwardingFeeProportionalMillionths = 0u,
            forwardingFeeBaseMsat = 0u,
            cltvExpiryDelta = 0u,
            maxDustHtlcExposure = org.lightningdevkit.ldknode.MaxDustHtlcExposure.FeeRateMultiplier(0uL),
            forceCloseAvoidanceMaxFeeSatoshis = 0uL,
            acceptUnderpayingHtlcs = false
        )
    )

    AppThemeSurface {
        BottomSheetPreview {
            ReceiveQrScreen(
                cjitInvoice = remember { mutableStateOf(null) },
                walletState = MainUiState(
                    nodeLifecycleState = NodeLifecycleState.Running,
                    channels = listOf(mockChannel),
                    onchainAddress = "bcrt1qfserxgtuesul4m9zva56wzk849yf9l8rk4qy0l",
                    bolt11 = "lnbcrt500u1pn7umn7pp5x0s9lt9fwrff6rp70pz3guwnjgw97sjuv79vhx9n2ps8q6tcdehhxapqd9h8vmmfvdjjqen0wgsyqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxq",
                    bip21 = "bitcoin:bcrt1qfserxgtuesul4m9zva56wzk849yf9l8rk4qy0l?lightning=lnbcrt500u1pn7umn7pp5x0s9lt9fwrff6rp70pz3guwnjgw97sjuv79..."
                ),
                onClickEditInvoice = {},
                modifier = Modifier.sheetHeight(),
                initialTab = ReceiveTab.AUTO,
                onClickReceiveCjit = {},
            )
        }
    }
}

@Suppress("SpellCheckingInspection")
@Preview(showSystemUi = true, name = "Spending Mode")
@Composable
private fun PreviewSpendingMode() {
    val mockChannel = ChannelDetails(
        channelId = "0".repeat(64),
        counterpartyNodeId = "0".repeat(66),
        fundingTxo = null,
        shortChannelId = null,
        outboundScidAlias = null,
        inboundScidAlias = null,
        channelValueSats = 1000000uL,
        unspendablePunishmentReserve = null,
        userChannelId = "0".repeat(32),
        feerateSatPer1000Weight = 1000u,
        outboundCapacityMsat = 500000000uL,
        inboundCapacityMsat = 500000000uL,
        confirmationsRequired = null,
        confirmations = null,
        isOutbound = true,
        isChannelReady = true,
        isUsable = true,
        isAnnounced = false,
        cltvExpiryDelta = null,
        counterpartyUnspendablePunishmentReserve = 0uL,
        counterpartyOutboundHtlcMinimumMsat = null,
        counterpartyOutboundHtlcMaximumMsat = null,
        counterpartyForwardingInfoFeeBaseMsat = null,
        counterpartyForwardingInfoFeeProportionalMillionths = null,
        counterpartyForwardingInfoCltvExpiryDelta = null,
        nextOutboundHtlcLimitMsat = 0uL,
        nextOutboundHtlcMinimumMsat = 0uL,
        forceCloseSpendDelay = null,
        inboundHtlcMinimumMsat = 0uL,
        inboundHtlcMaximumMsat = null,
        config = org.lightningdevkit.ldknode.ChannelConfig(
            forwardingFeeProportionalMillionths = 0u,
            forwardingFeeBaseMsat = 0u,
            cltvExpiryDelta = 0u,
            maxDustHtlcExposure = org.lightningdevkit.ldknode.MaxDustHtlcExposure.FeeRateMultiplier(0uL),
            forceCloseAvoidanceMaxFeeSatoshis = 0uL,
            acceptUnderpayingHtlcs = false
        )
    )

    AppThemeSurface {
        BottomSheetPreview {
            ReceiveQrScreen(
                cjitInvoice = remember { mutableStateOf(null) },
                walletState = MainUiState(
                    nodeLifecycleState = NodeLifecycleState.Running,
                    channels = listOf(mockChannel),
                    bolt11 = "lnbcrt500u1pn7umn7pp5x0s9lt9fwrff6rp70pz3guwnjgw97sjuv79vhx9n2ps8q6tcdehhxapqd9h8vmmfvdjjqen0wgsyqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxqcrqvpsxq"
                ),
                onClickEditInvoice = {},
                modifier = Modifier.sheetHeight(),
                initialTab = ReceiveTab.SPENDING,
                onClickReceiveCjit = {},
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun PreviewNodeNotReady() {
    AppThemeSurface {
        BottomSheetPreview {
            ReceiveQrScreen(
                cjitInvoice = remember { mutableStateOf(null) },
                walletState = MainUiState(
                    nodeLifecycleState = NodeLifecycleState.Starting,
                ),
                onClickReceiveCjit = {},
                onClickEditInvoice = {},
                modifier = Modifier.sheetHeight(),
            )
        }
    }
}

@Preview(showSystemUi = true, device = NEXUS_5)
@Composable
private fun PreviewSmall() {
    AppThemeSurface {
        BottomSheetPreview {
            ReceiveQrScreen(
                cjitInvoice = remember { mutableStateOf(null) },
                walletState = MainUiState(
                    nodeLifecycleState = NodeLifecycleState.Running,
                ),
                onClickEditInvoice = {},
                modifier = Modifier.sheetHeight(),
                onClickReceiveCjit = {},
            )
        }
    }
}


@Suppress("SpellCheckingInspection")
@Preview(showSystemUi = true, name = "Auto Mode")
@Composable
private fun PreviewDetailsMode() {
    AppThemeSurface {
        Column(
            modifier = Modifier
                .gradientBackground()
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ReceiveDetailsView(
                tab = ReceiveTab.AUTO,
                onchainAddress = "bcrt1qfserxgtuesul4m9zva56wzk849yf9l8rk4qy0l",
                bolt11 = "lnbcrt500u1pn7umn7pp5x0s9lt9fwrff6rp70pz3guwnjgw97sjuv79...",
                cjitInvoice = null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun Modifier.swipeToChangeTab(
    currentTabIndex: Int,
    tabCount: Int,
    onTabChange: (Int) -> Unit
) = composed {
    val threshold = remember { 1500f }
    val velocityTracker = remember { VelocityTracker() }

    pointerInput(currentTabIndex) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, _ ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().x
                when {
                    velocity >= threshold && currentTabIndex > 0 ->
                        onTabChange(currentTabIndex - 1)
                    velocity <= -threshold && currentTabIndex < tabCount - 1 ->
                        onTabChange(currentTabIndex + 1)
                }
                velocityTracker.resetTracking()
            },
            onDragCancel = {
                velocityTracker.resetTracking()
            },
        )
    }
}
