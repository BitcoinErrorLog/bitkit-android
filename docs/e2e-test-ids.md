## RN e2e testIDs to Android testTags

Legend:

- differentName = renamed in Android
- ✅ = present in Android
- ❌ = missing in Android
- 🚫 = descoped in Android

### backup.e2e.js

| RN testID           | Android testTag |
|---------------------|-----------------|
| Activity-1          | ✅               |
| ActivitySavings     | ✅               |
| ActivityTag         | ✅               |
| ActivityTags        | ✅               |
| BlocksWidget        | ✅               |
| CurrenciesSettings  | ✅               |
| DrawerSettings      | ✅               |
| GeneralSettings     | ✅               |
| HeaderMenu          | ✅               |
| HomeScrollView      | ✅               |
| MoneyFiatSymbol     | ✅               |
| NavigationClose     | ✅               |
| NewsWidget          | ✅               |
| PriceWidget         | ✅               |
| QRCode              | ✅               |
| Receive             | ✅               |
| ReceivedTransaction | ✅               |
| Tag-${tag}          | ✅               |
| TagInput            | ✅               |
| TotalBalance        | ✅               |
| WidgetActionDelete  | ✅               |
| WidgetsEdit         | ✅               |

### boost.e2e.js

| RN testID                 | Android testTag |
|---------------------------|-----------------|
| ActivityAmount            | ✅               |
| ActivityFee               | ✅               |
| ActivityShort-1           | ✅               |
| ActivityShort-2           | ✅               |
| ActivityShort-3           | ✅               |
| ActivityTxDetails         | ✅               |
| AddressContinue           | ✅               |
| BoostButton               | ✅               |
| BoostDisabled             | ✅               |
| BoostedButton             | ✅               |
| BoostingIcon              | ✅               |
| CPFPBoost                 | ✅               |
| CPFPBoosted               | ✅               |
| Close                     | ✅               |
| ContinueAmount            | ✅               |
| CustomFeeButton           | ✅               |
| DevOptions                | ✅               |
| DevSettings               | ✅               |
| DrawerSettings            | ✅               |
| GRAB                      | ✅               |
| HeaderMenu                | ✅               |
| HomeScrollView            | ✅               |
| Minus                     | ✅               |
| MoneyText                 | ✅               |
| N0                        | ✅               |
| N000                      | ✅               |
| N1                        | ✅               |
| NavigationBack            | ✅               |
| NavigationClose           | ✅               |
| Plus                      | ✅               |
| QRCode                    | ✅               |
| RBF                       | 🚫              |
| RBFBoost                  | ✅               |
| RBFBoosted                | ✅               |
| Receive                   | ✅               |
| ReceivedTransaction       | ✅               |
| ReceivedTransactionButton | ✅               |
| RecipientInput            | ✅               |
| RecipientManual           | ✅               |
| RecomendedFeeButton       | ✅               |
| Send                      | ✅               |
| SendAmountNumberPad       | ✅               |
| SendSuccess               | ✅               |
| StatusBoosting            | ✅               |
| StatusConfirmed           | ✅               |
| TXID                      | ✅               |
| TotalBalance              | ✅               |

### lightning.e2e.js

| RN testID                 | Android testTag |
|---------------------------|-----------------|
| Activity-1                | ✅               |
| Activity-2                | ✅               |
| Activity-3                | ✅               |
| Activity-4                | ✅               |
| Activity-5                | ✅               |
| ActivityShort-1           | ✅               |
| ActivityShort-2           | ✅               |
| ActivityShort-3           | ✅               |
| ActivityShowAll           | ✅               |
| AddressContinue           | ✅               |
| AdvancedSettings          | ✅               |
| Channel                   | ✅               |
| ChannelScrollView         | ✅               |
| Channels                  | ✅               |
| Close                     | ✅               |
| CloseConnection           | ✅               |
| CloseConnectionButton     | ✅               |
| ContinueAmount            | ✅               |
| DrawerSettings            | ✅               |
| ExternalContinue          | ✅               |
| FundCustom                | ✅               |
| FundManual                | ✅               |
| GRAB                      | ✅               |
| HeaderMenu                | ✅               |
| HomeScrollView            | ✅               |
| HostInput                 | ✅               |
| InvoiceNote               | ✅               |
| IsUsableYes               | ✅               |
| LDKNodeID                 | ✅               |
| LightningNodeInfo         | ✅               |
| MoneySign                 | ✅               |
| MoneyText                 | ✅               |
| N1                        | ✅               |
| NavigationAction          | ✅               |
| NavigationBack            | ✅               |
| NavigationClose           | ✅               |
| NodeIdInput               | ✅               |
| PortInput                 | ✅               |
| QRCode                    | ✅               |
| Receive                   | ✅               |
| ReceiveLightningInvoice   | ✅               |
| ReceiveNote               | ✅               |
| ReceiveNumberPad          | ✅               |
| ReceiveNumberPadSubmit    | ✅               |
| ReceiveNumberPadTextField | ✅               |
| ReceivedTransaction       | ✅               |
| RecipientInput            | ✅               |
| RecipientManual           | ✅               |
| ReviewAmount-primary      | ✅               |
| Send                      | ✅               |
| SendAmountNumberPad       | ✅               |
| SendSuccess               | ✅               |
| ShowQrReceive             | ✅               |
| SpecifyInvoiceButton      | ✅               |
| Tab-all                   | ✅               |
| Tab-other                 | ✅               |
| Tab-received              | ✅               |
| Tab-sent                  | ✅               |
| Tag-rtag                  | ✅               |
| Tag-rtag-delete           | ✅               |
| Tag-stag                  | ✅               |
| Tag-stag-delete           | ✅               |
| TagInputReceive           | ✅               |
| TagInputSend              | ✅               |
| TagsAdd                   | ✅               |
| TagsAddSend               | ✅               |
| TagsPrompt                | ✅               |
| TotalBalance              | ✅               |
| TotalSize                 | ✅               |

### lnurl.e2e.js

| RN testID              | Android testTag |
|------------------------|-----------------|
| ActivityShort-1        | ✅               |
| AddressContinue        | ✅               |
| AdvancedSettings       | ✅               |
| Close                  | ✅               |
| CommentInput           | ✅               |
| ConnectButton          | ✅               |
| ContinueAmount         | ✅               |
| DevOptions             | ✅               |
| DialogConfirm          | ✅               |
| DrawerSettings         | ✅               |
| ExternalSuccess        | ✅               |
| ExternalSuccess-button | ✅               |
| GRAB                   | ✅               |
| HeaderMenu             | ✅               |
| HomeScrollView         | ✅               |
| InvoiceComment         | ❌               |
| LDKNodeID              | ✅               |
| LightningNodeInfo      | ✅               |
| MoneyText              | ✅               |
| N0                     | ✅               |
| N1                     | ✅               |
| N2                     | ✅               |
| N3                     | ✅               |
| NavigationClose        | ✅               |
| QRInput                | ✅               |
| ReceivedTransaction    | ✅               |
| RecipientInput         | ✅               |
| RecipientManual        | ✅               |
| ReviewAmount-primary   | ✅               |
| Scan                   | ✅               |
| ScanPrompt             | ✅               |
| Send                   | ✅               |
| SendAmountNumberPad    | ✅               |
| SendSuccess            | ✅               |
| WithdrawConfirmButton  | ✅               |

### numberpad.e2e.js

| RN testID                 | Android testTag |
|---------------------------|-----------------|
| DenominationClassic       | ✅               |
| DrawerSettings            | ✅               |
| GeneralSettings           | ✅               |
| HeaderMenu                | ✅               |
| N0                        | ✅               |
| N000                      | ✅               |
| N1                        | ✅               |
| N2                        | ✅               |
| N3                        | ✅               |
| N4                        | ✅               |
| N6                        | ✅               |
| N9                        | ✅               |
| NDecimal                  | ✅               |
| NRemove                   | ✅               |
| NavigationClose           | ✅               |
| Receive                   | ✅               |
| ReceiveNumberPad          | ✅               |
| ReceiveNumberPadTextField | ✅               |
| ReceiveNumberPadUnit      | ✅               |
| SpecifyInvoiceButton      | ✅               |
| UnitSettings              | ✅               |

### onboarding.e2e.js

| RN testID             | Android testTag |
|-----------------------|-----------------|
| Check1                | ✅               |
| Check2                | ✅               |
| Continue              | ✅               |
| CreateNewWallet       | ✅               |
| GetStarted            | ✅               |
| Passphrase            | ✅               |
| PassphraseInput       | ✅               |
| QRCode                | ✅               |
| Receive               | ✅               |
| SkipButton            | ✅               |
| Slide0                | ✅               |
| Slide1                | ✅               |
| Slide2                | ✅               |
| Slide3                | ✅               |
| WalletOnboardingClose | ✅               |

### onchain.e2e.js

| RN testID            | Android testTag |
|----------------------|-----------------|
| Activity-1           | ✅               |
| Activity-2           | ✅               |
| Activity-3           | ✅               |
| Activity-4           | ✅               |
| ActivityShort-1      | ✅               |
| ActivityShort-2      | ✅               |
| ActivityShort-3      | ✅               |
| ActivityShowAll      | ✅               |
| ActivityTxDetails    | ✅               |
| AddressContinue      | ✅               |
| AvailableAmount      | ✅               |
| CalendarApplyButton  | ✅               |
| CalendarClearButton  | ✅               |
| Close                | ✅               |
| ContinueAmount       | ✅               |
| DatePicker           | ✅               |
| Day-1                | ❌               |
| Day-28               | ❌               |
| DialogConfirm        | ✅               |
| DrawerSettings       | ✅               |
| GRAB                 | ✅               |
| HeaderMenu           | ✅               |
| HomeScrollView       | ✅               |
| MoneySign            | ✅               |
| MoneyText            | ✅               |
| N${num}              | ✅               |
| NRemove              | ✅               |
| NavigationClose      | ✅               |
| NextMonth            | ❌               |
| PrevMonth            | ❌               |
| QRCode               | ✅               |
| Receive              | ✅               |
| ReceivedTransaction  | ✅               |
| RecipientInput       | ✅               |
| RecipientManual      | ✅               |
| SecuritySettings     | ✅               |
| Send                 | ✅               |
| SendAmountNumberPad  | ✅               |
| SendAmountWarning    | ✅               |
| SendDialog1          | ✅               |
| SendDialog2          | ✅               |
| SendSuccess          | ✅               |
| ShowQrReceive        | ✅               |
| SpecifyInvoiceButton | ✅               |
| Tab-all              | ✅               |
| Tab-other            | ✅               |
| Tab-received         | ✅               |
| Tab-sent             | ✅               |
| Tag-rtag0            | ✅               |
| Tag-rtag0-delete     | ✅               |
| Tag-stag             | ✅               |
| Tag-stag-delete      | ✅               |
| TagInputReceive      | ✅               |
| TagInputSend         | ✅               |
| TagsAdd              | ✅               |
| TagsAddSend          | ✅               |
| TagsPrompt           | ✅               |
| Today                | ❌               |
| TotalBalance         | ✅               |

### receive.e2e.js

| RN testID                 | Android testTag |
|---------------------------|-----------------|
| N1                        | ✅               |
| N2                        | ✅               |
| N3                        | ✅               |
| QRCode                    | ✅               |
| Receive                   | ✅               |
| ReceiveNote               | ✅               |
| ReceiveNumberPad          | ✅               |
| ReceiveNumberPadSubmit    | ✅               |
| ReceiveNumberPadTextField | ✅               |
| ReceiveOnchainInvoice     | ✅               |
| ReceiveScreen             | ✅               |
| ReceiveSlider             | ✅               |
| ReceiveTagsSubmit         | ✅               |
| ShowQrReceive             | ✅               |
| SpecifyInvoiceButton      | ✅               |
| Tag-${tag}                | ✅               |
| Tag-${tag}-delete         | ✅               |
| TagInputReceive           | ✅               |
| TagsAdd                   | ✅               |

### security.e2e.js

| RN testID                    | Android testTag |
|------------------------------|-----------------|
| AddressContinue              | ✅               |
| AttemptsRemaining            | ✅               |
| Biometrics                   | ✅               |
| ChangePIN                    | ✅               |
| ChangePIN2                   | ✅               |
| Check1                       | ✅               |
| Close                        | ✅               |
| ContinueAmount               | ✅               |
| ContinueButton               | ✅               |
| DisablePin                   | ✅               |
| DrawerSettings               | ✅               |
| ForgotPIN                    | ✅               |
| GRAB                         | ✅               |
| HeaderMenu                   | ✅               |
| LastAttempt                  | ✅               |
| N000                         | ✅               |
| N1                           | ✅               |
| N2                           | ✅               |
| N3                           | ✅               |
| N9                           | ✅               |
| NRemove                      | ✅               |
| OK                           | ✅               |
| PINChange                    | ✅               |
| PINCode                      | ✅               |
| PinPad                       | ✅               |
| QRCode                       | ✅               |
| Receive                      | ✅               |
| ReceivedTransaction          | ✅               |
| RecipientInput               | ✅               |
| RecipientManual              | ✅               |
| SecureWallet-button-continue | ✅               |
| SecuritySettings             | ✅               |
| Send                         | ✅               |
| SendAmountNumberPad          | ✅               |
| SendSuccess                  | ✅               |
| ToggleBioForPayments         | ✅               |
| ToggleBiometrics             | ✅               |
| TotalBalance                 | ✅               |
| UseBiometryInstead           | ✅               |
| WrongPIN                     | ✅               |

### send.e2e.js

| RN testID            | Android testTag |
|----------------------|-----------------|
| AddressContinue      | ✅               |
| AdvancedSettings     | ✅               |
| AssetButton-savings  | ✅               |
| AssetButton-spending | ✅               |
| AssetButton-switch   | ✅               |
| AvailableAmount      | ✅               |
| Channel              | ✅               |
| ChannelScrollView    | ✅               |
| Channels             | ✅               |
| Close                | ✅               |
| ContinueAmount       | ✅               |
| DrawerSettings       | ✅               |
| ExternalContinue     | ✅               |
| FundCustom           | ✅               |
| FundManual           | ✅               |
| GRAB                 | ✅               |
| GeneralSettings      | ✅               |
| HeaderMenu           | ✅               |
| HostInput            | ✅               |
| IsUsableYes          | ✅               |
| LDKNodeID            | ✅               |
| LightningNodeInfo    | ✅               |
| MoneyText            | ✅               |
| N0                   | ✅               |
| N1                   | ✅               |
| N2                   | ✅               |
| NRemove              | ✅               |
| NavigationAction     | ✅               |
| NavigationBack       | ✅               |
| NavigationClose      | ✅               |
| NodeIdInput          | ✅               |
| PortInput            | ✅               |
| QRCode               | ✅               |
| QuickpayIntro-button | ✅               |
| QuickpaySettings     | ✅               |
| QuickpayToggle       | ✅               |
| Receive              | ✅               |
| ReceivedTransaction  | ✅               |
| RecipientInput       | ✅               |
| RecipientManual      | ✅               |
| ReviewAmount         | ✅               |
| ReviewAmount-primary | ✅               |
| ReviewUri            | ✅               |
| Send                 | ✅               |
| SendAmountNumberPad  | ✅               |
| SendSheet            | ✅               |
| SendSuccess          | ✅               |
| TotalBalance         | ✅               |
| TotalSize            | ✅               |

### settings.e2e.js

| RN testID                   | Android testTag |
|-----------------------------|-----------------|
| About                       | ✅               |
| AboutLogo                   | ✅               |
| Address-0                   | ✅               |
| AddressTypePreference       | 🚫              |
| AddressViewer               | ✅               |
| AdvancedSettings            | ✅               |
| AppStatus                   | ✅               |
| BackupSettings              | ✅               |
| BackupWallet                | ✅               |
| Bitcoin                     | ✅               |
| ConnectToHost               | ✅               |
| ConnectToUrl                | 🚫              |
| Connected                   | ✅               |
| ConnectedUrl                | ✅               |
| Continue                    | ✅               |
| ContinueConfirmMnemonic     | ✅               |
| ContinueShowMnemonic        | ✅               |
| CopyNodeId                  | 🚫              |
| CurrenciesSettings          | ✅               |
| CustomFee                   | ✅               |
| DenominationClassic         | ✅               |
| DevOptions                  | ✅               |
| DevSettings                 | ✅               |
| DialogConfirm               | ✅               |
| Disconnected                | ✅               |
| DrawerSettings              | ✅               |
| ElectrumConfig              | ✅               |
| ElectrumProtocol            | ✅               |
| ElectrumStatus              | ✅               |
| ErrorReport                 | 🚫              |
| GeneralSettings             | ✅               |
| HeaderMenu                  | ✅               |
| HideBalanceOnOpen           | ✅               |
| HostInput                   | ✅               |
| LDKDebug                    | 🚫              |
| LightningNodeInfo           | ✅               |
| MoneyFiatSymbol             | ✅               |
| MoneyText                   | ✅               |
| N1                          | ✅               |
| NavigationAction            | ✅               |
| NavigationBack              | ✅               |
| NavigationClose             | ✅               |
| OK                          | ✅               |
| Path                        | ✅               |
| PortInput                   | ✅               |
| QRCode                      | ✅               |
| QRInput                     | ✅               |
| RGSServer                   | ✅               |
| RGSUrl                      | ✅               |
| RebroadcastLDKTXS           | 🚫              |
| Receive                     | ✅               |
| ReceiveScreen               | ✅               |
| ReceiveTagsSubmit           | ✅               |
| RefreshLDK                  | 🚫              |
| ResetAndRestore             | ✅               |
| ResetSuggestions            | ✅               |
| ResetToDefault              | ✅               |
| RestartLDK                  | 🚫              |
| ScanPrompt                  | ✅               |
| SecuritySettings            | ✅               |
| SeedContaider               | SeedContainer   |
| ShowBalance                 | ✅               |
| SpecifyInvoiceButton        | ✅               |
| Status-backup               | ✅               |
| Status-electrum             | ✅               |
| Status-internet             | ✅               |
| Status-lightning_connection | ✅               |
| Status-lightning_node       | ✅               |
| Suggestion-lightning        | ✅               |
| SuggestionDismiss           | ✅               |
| Suggestions                 | ✅               |
| Support                     | ✅               |
| SwipeBalanceToHide          | ✅               |
| Tag-${tag}-delete           | ✅               |
| TagInputReceive             | ✅               |
| TagsAdd                     | ✅               |
| TagsSettings                | ✅               |
| TapToReveal                 | ✅               |
| TotalBalance                | ✅               |
| TransactionSpeedSettings    | ✅               |
| TriggerRenderError          | 🚫              |
| USD                         | ✅               |
| UnitSettings                | ✅               |
| UrlInput                    | 🚫              |
| Value                       | ✅               |
| WebRelay                    | 🚫              |
| WebRelayStatus              | 🚫              |
| Word-${word}                | ✅               |
| custom                      | ✅               |
| fast                        | ✅               |
| normal                      | ✅               |
| p2pkh                       | 🚫              |
| p2wpkh                      | 🚫              |

### slashtags.e2e.js

| RN testID                 | Android testTag |
|---------------------------|-----------------|
| Activity-1                | ✅               |
| ActivityAssign            | ❌               |
| ActivityDetach            | ❌               |
| ActivitySavings           | ✅               |
| AddContact                | ❌               |
| AddContactButton          | ❌               |
| BioInput                  | ❌               |
| ContactSmall              | ❌               |
| ContactURLInput           | ❌               |
| ContactURLInput-error     | ❌               |
| ContactsOnboarding-button | ❌               |
| ContactsSearchInput       | ❌               |
| CopyButton                | ❌               |
| DeleteContactButton       | ❌               |
| DeleteDialog              | ❌               |
| DialogConfirm             | ✅               |
| DrawerContacts            | ✅               |
| EditButton                | ❌               |
| EmptyProfileHeader        | ✅               |
| Header                    | ✅               |
| HeaderMenu                | ✅               |
| LinkLabelInput            | ❌               |
| LinkValueInput            | ❌               |
| NameInput                 | ❌               |
| NavigationBack            | ✅               |
| NavigationClose           | ✅               |
| OnboardingContinue        | ❌               |
| ProfileAddLink            | ❌               |
| ProfileDeleteButton       | ❌               |
| ProfileLinkSuggestions    | ❌               |
| ProfileSaveButton         | ❌               |
| ProfileSlashtag           | ❌               |
| QRCode                    | ✅               |
| Receive                   | ✅               |
| ReceivedTransaction       | ✅               |
| RemoveLinkButton          | ❌               |
| SaveContactButton         | ❌               |
| SaveLink                  | ❌               |

### transfer.e2e.js

| RN testID                     | Android testTag                 |
|-------------------------------|---------------------------------|
| ActivitySavings               | ✅                               |
| ActivityShort-1               | ✅                               |
| ActivitySpending              | ✅                               |
| AddressContinue               | ✅                               |
| AdvancedSettings              | ✅                               |
| AvailabilityContinue          | ✅                               |
| BoostButton                   | ✅                               |
| BoostingIcon                  | ✅                               |
| CPFPBoost                     | ✅                               |
| Channel                       | ✅                               |
| ChannelScrollView             | ✅                               |
| Channels                      | ✅                               |
| ChannelsClosed                | ✅                               |
| Close                         | ✅                               |
| ContinueAmount                | ✅                               |
| CurrenciesSettings            | ✅                               |
| DrawerSettings                | ✅                               |
| ExternalAmount                | ✅                               |
| ExternalAmountContinue        | ✅                               |
| ExternalContinue              | ✅                               |
| ExternalSuccess               | ✅                               |
| ExternalSuccess-button        | ✅                               |
| FeeCustomContinue             | ✅                               |
| FeeCustomNumberPad            | ✅                               |
| FundCustom                    | ✅                               |
| FundManual                    | ✅                               |
| FundTransfer                  | ✅                               |
| GRAB                          | ✅                               |
| GeneralSettings               | ✅                               |
| HeaderMenu                    | ✅                               |
| HomeScrollView                | ✅                               |
| HostInput                     | ✅                               |
| IsUsableYes                   | ✅                               |
| LDKNodeID                     | ✅                               |
| LightningNodeInfo             | ✅                               |
| LightningSettingUp            | ✅                               |
| LiquidityContinue             | ✅                               |
| MoneyText                     | ✅                               |
| N0                            | ✅                               |
| N1                            | ✅                               |
| N2                            | ✅                               |
| N5                            | ✅                               |
| NRemove                       | ✅                               |
| NavigationAction              | ✅                               |
| NavigationBack                | ✅                               |
| NavigationClose               | ✅                               |
| NodeIdInput                   | ✅                               |
| PortInput                     | ✅                               |
| QRCode                        | ✅                               |
| Receive                       | ✅                               |
| ReceivedTransaction           | ✅                               |
| RecipientInput                | ✅                               |
| RecipientManual               | ✅                               |
| SavingsIntro-button           | ✅                               |
| Send                          | ✅                               |
| SendAmountNumberPad           | ✅                               |
| SendSuccess                   | ✅                               |
| SetCustomFee                  | ✅                               |
| SpendingAdvanced              | ✅                               |
| SpendingAdvancedContinue      | ✅                               |
| SpendingAdvancedDefault       | ✅                               |
| SpendingAdvancedMax           | ✅                               |
| SpendingAdvancedMin           | ✅                               |
| SpendingAdvancedNumberField   | ✅                               |
| SpendingAmount                | ✅                               |
| SpendingAmountContinue        | ✅                               |
| SpendingAmountMax             | ✅                               |
| SpendingAmountQuarter         | ✅                               |
| SpendingConfirmAdvanced       | ✅                               |
| SpendingConfirmChannel        | ✅                               |
| SpendingConfirmDefault        | ✅                               |
| SpendingConfirmMore           | ✅                               |
| SpendingIntro-button          | ✅                               |
| StatusBoosting                | ✅                               |
| StatusTransfer                | ✅                               |
| Suggestion-lightning          | ✅                               |
| Suggestion-lightningSettingUp | Suggestion-lightning_setting_up |
| TotalBalance                  | ✅                               |
| TotalSize                     | ✅                               |
| TransferIntro-button          | ✅                               |
| TransferSuccess               | ✅                               |
| TransferSuccess-button        | ✅                               |
| TransferToSavings             | ✅                               |
| TransferToSpending            | ✅                               |

### widgets.e2e.js

| RN testID                  | Android testTag |
|----------------------------|-----------------|
| HomeScrollView             | ✅               |
| PriceWidget                | ✅               |
| PriceWidgetRow-BTC/EUR     | ✅               |
| PriceWidgetSource          | ✅               |
| WidgetActionDelete         | ✅               |
| WidgetActionEdit           | ✅               |
| WidgetEdit                 | ✅               |
| WidgetEditField-1W         | ✅               |
| WidgetEditField-BTC/EUR    | ✅               |
| WidgetEditField-showSource | ✅               |
| WidgetEditPreview          | ✅               |
| WidgetEditReset            | ✅               |
| WidgetEditScrollView       | ✅               |
| WidgetListItem-price       | ✅               |
| WidgetSave                 | ✅               |
| WidgetsAdd                 | ✅               |
| WidgetsEdit                | ✅               |
| WidgetsOnboarding-button   | ✅               |
