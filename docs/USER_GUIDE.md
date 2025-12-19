# Paykit User Guide (Android)

This guide explains how to use Paykit features in Bitkit for Android.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Connecting with Pubky-ring](#connecting-with-pubky-ring)
3. [Viewing Contacts](#viewing-contacts)
4. [Receiving Payments](#receiving-payments)
5. [Sending Payments](#sending-payments)
6. [Session Management](#session-management)
7. [Troubleshooting](#troubleshooting)

---

## Getting Started

Paykit enables direct peer-to-peer payments using Pubky identity. Before using Paykit:

1. **Install Pubky-ring**: The Pubky-ring app manages your identity and sessions
2. **Create or Import Identity**: Set up your Pubky identity in Pubky-ring
3. **Connect Bitkit**: Link Bitkit to your Pubky identity

---

## Connecting with Pubky-ring

### Same Device Connection

1. Open Bitkit and navigate to **Paykit** in settings
2. Tap **Connect with Pubky-ring**
3. Pubky-ring will open and ask you to select an identity
4. Approve the connection request
5. You'll return to Bitkit with your session active

### Cross-Device Connection (QR Code)

If Pubky-ring is on a different device:

1. In Bitkit, tap **Connect via QR Code**
2. A QR code will display
3. On your other device, open Pubky-ring
4. Scan the QR code
5. Approve the connection
6. Bitkit will automatically detect the session

---

## Viewing Contacts

### Contact Discovery

Bitkit automatically discovers contacts who:
- Follow you on Pubky
- Have published payment endpoints

Navigate to **Contacts** to see your Paykit contacts.

### Contact Information

Each contact shows:
- **Display name** from their Pubky profile
- **Payment methods** they support (Lightning, On-chain)
- **Online status** if available

### Adding New Contacts

1. Get your friend's Pubky ID (z32 format)
2. Tap **Add Contact**
3. Enter their Pubky ID
4. Bitkit will fetch their profile and payment info

---

## Receiving Payments

### Setting Up Payment Endpoints

Your payment endpoints are automatically published to your Pubky homeserver:

1. Go to **Settings > Paykit > Payment Methods**
2. Enable the methods you want to receive:
   - **Lightning**: Receive via Lightning Network
   - **On-chain**: Receive to your Bitcoin address

### Sharing Your Paykit ID

Share your Pubky ID for others to pay you:

1. Go to **Receive > Paykit**
2. Tap **Copy Pubky ID** or **Show QR Code**
3. Share with the sender

### Receiving a Payment

When someone pays you via Paykit:

1. They look up your payment endpoints
2. Bitkit generates the appropriate invoice/address
3. Payment arrives as normal (Lightning or on-chain)
4. You'll see the sender's name if they're a contact

---

## Sending Payments

### Paying a Contact

1. Open the **Contacts** list
2. Select the contact you want to pay
3. Tap **Send Payment**
4. Enter the amount
5. Review and confirm
6. Payment is sent directly to their preferred method

### Paying by Pubky ID

1. Go to **Send > Paykit**
2. Enter the recipient's Pubky ID
3. Bitkit fetches their payment endpoints
4. Select amount and payment method
5. Confirm and send

### Smart Method Selection

Bitkit automatically selects the best payment method:

| Scenario | Method Selected |
|----------|-----------------|
| Small amount, recipient has Lightning | Lightning |
| Large amount, recipient has on-chain only | On-chain |
| Recipient supports both | User choice |

---

## Session Management

### Viewing Session Status

Go to **Settings > Paykit > Session** to see:
- Current session status (active/expired)
- Session expiry time
- Connected Pubky identity

### Refreshing Session

If your session expires:

1. Go to **Settings > Paykit**
2. Tap **Refresh Session**
3. Pubky-ring will open to re-authenticate
4. Return to Bitkit with fresh session

### Disconnecting

To disconnect your Paykit identity:

1. Go to **Settings > Paykit**
2. Tap **Disconnect**
3. Confirm the action
4. Session and cached data will be cleared

### Backup & Restore

To backup your Paykit data:

1. Go to **Settings > Paykit > Backup**
2. Tap **Export Backup**
3. Set a strong password
4. Save the encrypted file securely

To restore:

1. Go to **Settings > Paykit > Backup**
2. Tap **Import Backup**
3. Select your backup file
4. Enter the password
5. Sessions and keys will be restored

---

## Troubleshooting

### Connection Issues

**"Pubky-ring not found"**
- Ensure Pubky-ring is installed
- Check that it's the latest version
- Try reinstalling Pubky-ring

**"Session expired"**
- Tap Refresh Session to re-authenticate
- Check your internet connection
- Ensure homeserver is accessible

### Payment Issues

**"No payment methods available"**
- Contact hasn't published payment endpoints
- Ask them to enable Paykit in their wallet

**"Payment failed"**
- Check your balance (Lightning/on-chain)
- Ensure you have sufficient funds
- Try a smaller amount
- Check recipient's node is online

### Contact Issues

**"Contact not found"**
- Verify the Pubky ID is correct (z32 format)
- Check your internet connection
- Contact may not have published a profile

**"Cannot discover payment methods"**
- Contact's homeserver may be unreachable
- Try again later
- Ask contact to check their Paykit setup

### Sync Issues

**"Profile sync failed"**
- Check internet connection
- Verify homeserver URL in settings
- Try manual sync from Settings > Paykit > Sync

**"Contacts not updating"**
- Pull to refresh the contacts list
- Check directory service status
- Clear cache and retry

---

## Privacy & Security

### What Bitkit Shares

When using Paykit, the following is public:
- Your Pubky profile (name, bio, avatar)
- Your payment endpoints (Lightning node, addresses)
- Your follows/contacts list

### What Stays Private

- Private keys (never leave device)
- Payment history
- Wallet balances
- Session secrets

### Best Practices

1. **Use strong passwords** for backup encryption
2. **Enable biometric auth** for sensitive operations
3. **Regularly check** connected sessions
4. **Disconnect unused** identities
5. **Keep apps updated** for security patches

---

## Frequently Asked Questions

**Q: Can I use multiple Pubky identities?**
A: Currently, Bitkit supports one active Paykit session at a time.

**Q: Is Paykit compatible with other wallets?**
A: Any wallet supporting the Paykit protocol can interoperate.

**Q: What happens if my session expires while offline?**
A: Payments to you may fail. Reconnect when online to refresh.

**Q: Are Paykit payments faster than regular Lightning?**
A: They use the same underlying networks; Paykit adds contact discovery.

**Q: Can I receive payments without Pubky-ring?**
A: No, Pubky-ring is required for identity and session management.

---

## Getting Help

- **In-app help**: Tap the (?) icon in Paykit screens
- **Community**: Join our Telegram/Discord for support
- **Documentation**: See `PAYKIT_SETUP.md` for technical details
- **Bug reports**: File issues on GitHub

