# Play Console — Data Safety declarations

Use this when filling the **Data safety** form in Play Console. Verify against your
actual configuration before submitting; Google holds you responsible for accuracy.

## Data collected / shared

| Data type | Collected | Shared | Purpose | By |
|---|---|---|---|---|
| Device or other IDs (advertising ID) | Yes | Yes | Advertising | Google AdMob |
| App activity / interactions | Yes | Yes | Advertising, analytics | Google AdMob |
| Approximate location (from IP) | Maybe | Maybe | Ads | Google AdMob |
| Purchase history | Yes | No | Manage subscription | Google Play Billing |

> Exact AdMob collection depends on personalization/consent settings. Consult
> AdMob's Data Safety guidance for the current list.

## Screen content

- Screen capture is processed **on-device** and streamed **directly** to a
  receiver the user selects on their local network.
- It is **not** sent to your servers and **not** collected. Declare accordingly
  (not collected / not shared), and disclose the screen-recording behavior in the
  listing description.

## Security

- Local streaming happens over the user's own WiFi (raw TCP). Note this in your
  description; consider adding encryption for streams that leave the LAN (future).

## Data deletion

- No account, no server-side personal data to delete. Ad/payment data is governed
  by Google's policies.
