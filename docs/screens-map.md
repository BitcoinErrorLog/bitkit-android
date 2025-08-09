# RN ↔ Android screens mapping

Legend: RN = React Native screen (path), Android = Compose screen (Kotlin file)

## Wallets / Send
| RN | Android |
| - | - |
| Wallets/Send/Amount.tsx | wallets/send/SendAmountScreen.kt |
| Wallets/Send/Recipient.tsx | wallets/send/SendRecipientScreen.kt |
| Wallets/Send/Address.tsx | wallets/send/SendAddressScreen.kt |
| Wallets/Send/ReviewAndSend.tsx | wallets/send/SendConfirmScreen.kt |
| Wallets/Send/FeeRate.tsx | wallets/send/SendFeeRateScreen.kt |
| Wallets/Send/FeeCustom.tsx | wallets/send/SendFeeCustomScreen.kt |
| Wallets/Send/CoinSelection.tsx | wallets/send/SendCoinSelectionScreen.kt |
| Wallets/Send/SendPinPad.tsx | wallets/send/SendPinCheckScreen.kt |
| Wallets/Send/Quickpay.tsx | wallets/send/SendQuickPayScreen.kt |
| Wallets/Send/Tags.tsx | wallets/send/AddTagScreen.kt |
| Wallets/Send/Error.tsx | wallets/send/SendErrorScreen.kt |
| Wallets/Send/Pending.tsx | - |
| src/screens/Wallets/Send/Success.tsx | — |

## Wallets / Receive
| RN | Android |
| - | - |
| Wallets/Receive/ReceiveDetails.tsx | wallets/receive/EditInvoiceScreen.kt |
| Wallets/Receive/ReceiveAmount.tsx | wallets/receive/ReceiveAmountScreen.kt |
| Wallets/Receive/ReceiveQR.tsx | wallets/receive/ReceiveQrScreen.kt |
| Wallets/Receive/ReceiveConnect.tsx | wallets/receive/ReceiveLiquidityScreen.kt / ReceiveConfirmScreen.kt |
| Wallets/Receive/ReceiveGeoBlocked.tsx | wallets/receive/LocationBlockScreen.kt |
| Wallets/Receive/Liquidity.tsx | transfer/LiquidityScreen.kt (analogue) |

## Wallets / Home & Tabs
| RN | Android |
| - | - |
| Wallets/Home.tsx | wallets/HomeScreen.kt |
| components/TabBar.tsx | components/TabBar.kt |

## Activity
| RN | Android |
| - | - |
| Activity/ActivityDetail.tsx | wallets/activity/ActivityDetailScreen.kt |
| Activity/ActivityList.tsx | wallets/activity/AllActivityScreen.kt |
| Activity/ActivityFiltered.tsx | wallets/activity/AllActivityScreen.kt |
| Activity/ListItem.tsx | wallets/activity/components/* |

## Scanner
| RN | Android |
| - | - |
| Scanner/MainScanner.tsx | scanner/QrScanningScreen.kt |

## Transfer (Unified flows)
| RN | Android |
| - | - |
| Transfer/TransferIntro.tsx | transfer/TransferIntroScreen.kt |
| Transfer/SpendingIntro.tsx | transfer/SpendingIntroScreen.kt |
| Transfer/SpendingConfirm.tsx | transfer/SpendingConfirmScreen.kt |
| Transfer/SavingsIntro.tsx | transfer/SavingsIntroScreen.kt |
| Transfer/SavingsConfirm.tsx | transfer/SavingsConfirmScreen.kt |
| Transfer/SavingsProgress.tsx | transfer/SavingsProgressScreen.kt |
| Transfer/SavingsAdvanced.tsx | transfer/SavingsAdvancedScreen.kt |
| Transfer/SpendingAmount.tsx | transfer/SpendingAmountScreen.kt |
| Transfer/Funding.tsx | transfer/FundingScreen.kt |
| Transfer/FundingAdvanced.tsx | transfer/FundingAdvancedScreen.kt |
| Transfer/SettingUp.tsx | transfer/SettingUpScreen.kt |
| Transfer/Liquidity.tsx | transfer/LiquidityScreen.kt |
| Transfer/Availability.tsx | transfer/SavingsAvailabilityScreen.kt |

## External Node / LNURL Channel
| RN | Android |
| - | - |
| Transfer/ExternalNode/Connection.tsx | transfer/external/ExternalConnectionScreen.kt |
| Transfer/ExternalNode/Amount.tsx | transfer/external/ExternalAmountScreen.kt |
| Transfer/ExternalNode/Confirm.tsx | transfer/external/ExternalConfirmScreen.kt |
| Transfer/ExternalNode/Success.tsx | transfer/external/ExternalSuccessScreen.kt |
| Transfer/LNURLChannel.tsx | transfer/external/LnurlChannelScreen.kt |

## Settings (General)
| RN | Android |
| - | - |
| Settings/index.tsx | settings/SettingsScreen.kt |
| Settings/General/index.tsx | settings/general/GeneralSettingsScreen.kt |
| Settings/Currencies/index.tsx | settings/general/LocalCurrencySettingsScreen.kt |
| Settings/Unit/index.tsx | settings/general/DefaultUnitSettingsScreen.kt |
| Settings/Tags/index.tsx | settings/general/TagsSettingsScreen.kt |
| Settings/Advanced/index.tsx | settings/AdvancedSettingsScreen.kt |
| Settings/AddressViewer/index.tsx | settings/advanced/AddressViewerScreen.kt |
| src/screens/Settings/GapLimit/index.tsx | — |
| Settings/About/index.tsx | settings/AboutScreen.kt |
| Settings/AppStatus/index.tsx | settings/appStatus/AppStatusScreen.kt |
| Settings/Widgets/index.tsx | screens/widgets/AddWidgetsScreen.kt / WidgetsIntroScreen.kt |
| Settings/WebRelay/index.tsx | settings/advanced/RgsServerScreen.kt |
| Settings/TransactionSpeed/index.tsx | settings/transactionSpeed/TransactionSpeedSettingsScreen.kt |
| Settings/TransactionSpeed/CustomFee.tsx | settings/transactionSpeed/CustomFeeSettingsScreen.kt |
| Settings/Quickpay/QuickpayIntro.tsx | settings/quickPay/QuickPayIntroScreen.kt |
| Settings/Quickpay/QuickpaySettings.tsx | settings/quickPay/QuickPaySettingsScreen.kt |
| Settings/RGSServer/index.tsx | settings/advanced/RgsServerScreen.kt |
| Settings/SupportSettings/index.tsx | settings/support/SupportScreen.kt |
| Settings/ReportIssue/index.tsx | settings/support/ReportIssueScreen.kt |
| Settings/ReportIssue/FormSuccess.tsx | settings/support/ReportIssueResultScreen.kt |
| Settings/DevSettings/index.tsx | screens/settings/DevSettingsScreen.kt |
| src/screens/Settings/DevSettings/LdkDebug.tsx | — |
| Settings/Lightning/Channels.tsx | settings/lightning/LightningConnectionsScreen.kt |
| Settings/Lightning/ChannelDetails.tsx | settings/lightning/ChannelDetailScreen.kt |
| Settings/Lightning/CloseConnection.tsx | settings/lightning/CloseConnectionScreen.kt |
| src/screens/Settings/Lightning/LightningNodeInfo.tsx | — |
| src/screens/Settings/BackupSettings/index.tsx | app/src/main/java/to/bitkit/ui/settings/BackupSettingsScreen.kt |

## Backup & Recovery
| RN | Android |
| - | - |
| Settings/Backup/Warning.tsx | settings/backups/WarningScreen.kt |
| Settings/Backup/Success.tsx | settings/backups/SuccessScreen.kt |
| Settings/Backup/ShowPassphrase.tsx | settings/backups/ShowPassphraseScreen.kt |
| Settings/Backup/ShowMnemonic.tsx | settings/backups/ShowMnemonicScreen.kt |
| Settings/Backup/MultipleDevices.tsx | settings/backups/MultipleDevicesScreen.kt |
| Settings/Backup/Metadata.tsx | settings/backups/MetadataScreen.kt |
| Settings/Backup/ConfirmPassphrase.tsx | settings/backups/ConfirmPassphraseScreen.kt |
| Settings/Backup/ConfirmMnemonic.tsx | settings/backups/ConfirmMnemonicScreen.kt |
| Settings/Backup/ResetAndRestore.tsx | settings/backups/ResetAndRestoreScreen.kt |

## Onboarding
| RN | Android |
| - | - |
| Onboarding/Welcome.tsx | onboarding/OnboardingSlidesScreen.kt / IntroScreen.kt |
| Onboarding/Slideshow.tsx | onboarding/OnboardingSlidesScreen.kt |
| Onboarding/Passphrase.tsx | onboarding/CreateWalletWithPassphraseScreen.kt |
| Onboarding/RestoreFromSeed.tsx | onboarding/RestoreWalletScreen.kt |
| Onboarding/Loading.tsx | onboarding/InitializingWalletView.kt |
| Onboarding/MultipleDevices.tsx | onboarding/WarningMultipleDevicesScreen.kt |
| Onboarding/TermsOfUse.tsx | onboarding/TermsOfUseScreen.kt |
| Onboarding/CreateWallet.tsx | onboarding/WalletRestoreSuccessView.kt / WalletRestoreErrorView.kt |

## Profile & Contacts
| RN | Android |
| - | - |
| Contacts/Contacts.tsx | — |
| Contacts/Contact.tsx | — |
| Profile/Profile.tsx | screens/profile/CreateProfileScreen.kt / ProfileIntroScreen.kt |
| Profile/ProfileEdit.tsx | screens/profile/CreateProfileScreen.kt |
| Profile/ProfileOnboarding.tsx | screens/profile/ProfileIntroScreen.kt |
| Profile/ProfileLink.tsx | screens/profile/CreateProfileScreen.kt |

## Widgets
| RN | Android |
| - | - |
| Widgets/Widget.tsx | screens/widgets/*Card.kt |
| Widgets/WidgetEdit.tsx | screens/widgets/*EditScreen.kt |
| Widgets/WidgetsOnboarding.tsx | screens/widgets/WidgetsIntroScreen.kt |
| Widgets/WidgetsSuggestions.tsx | screens/widgets/AddWidgetsScreen.kt |

## Shop
| RN | Android |
| - | - |
| Shop/ShopIntro.tsx | screens/shop/ShopIntroScreen.kt |
| Shop/ShopDiscover.tsx | screens/shop/shopDiscover/ShopDiscoverScreen.kt |
| Shop/ShopMain.tsx | screens/shop/shopWebView/ShopWebViewScreen.kt |

## App Update
| RN | Android |
| - | - |
| AppUpdate.tsx | - |

## Sheets
| RN | Android |
| ReceivedTransaction.tsx | NewTransactionSheet.kt |
