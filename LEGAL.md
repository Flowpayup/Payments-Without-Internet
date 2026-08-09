# Legal & disclaimer

**Read this before using, forking, or building on the code. By doing any of those, you accept these terms.**


## No warranty

This software is provided under Apache License 2.0 **"AS IS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,"** including but not limited to warranties of merchantability, fitness for a particular purpose, non-infringement, accuracy, or reliability. See Section 7 of [LICENSE](LICENSE) for the full text.

## No affiliation

Flowpay is an independent open-source project. It is **not affiliated with, endorsed by, sponsored by, certified by, or connected to** any bank, telecommunications operator, payment processor, payment scheme operator, regulator, standards body, or government entity. References to public payment shortcodes, IVR numbers, or transaction rails are made strictly for descriptive and identification purposes.

## Your transactions are between you and your bank

When the app dials `*99#` or initiates an IVR call, **you are interacting directly with your telecom operator and your bank.** Flowpay does not see, store, transmit, intermediate, or modify your transaction data or your UPI PIN. The app only:

1. Triggers the dialer with a known public shortcode.
2. Reads incoming bank-confirmation SMS **locally on your device** for the sole purpose of showing you a transaction-result screen.

Any transaction outcome — success, failure, delay, double-debit, or loss — is **between you and your bank**, governed by your bank's terms of service and the applicable payment-scheme rules. The author of Flowpay accepts no liability whatsoever for any transaction outcome.

## User responsibility & compliance

You are solely responsible for ensuring your use of this software complies with all applicable laws and regulations, including but not limited to telecommunications regulations, financial-services regulations, KYC/AML rules, data-protection laws, and your bank's and operator's terms of service. If your jurisdiction restricts USSD-based payments, automated dialler use, or SMS reading, do not use this software there.

## Telecom and bank charges

Dialling `*99#` and initiating IVR calls may incur charges from your telecom operator. UPI transactions themselves may incur charges depending on your bank's policies. Flowpay does not subsidise, refund, or have visibility into any such charges. Check with your operator and bank before using the app for real transactions.

## Permissions and data handling

Flowpay reads SMS locally to detect transaction confirmations. **SMS contents never leave your device.** No data is uploaded anywhere — the app has no backend. Because the SMS-read permission usage falls outside Google Play's restricted-permission policies, the app is not available on Google Play and is distributed as source that you build yourself. If you publish a fork, you are responsible for your own Play Store compliance review.

## Trademarks

All product names, logos, trademarks, service marks, and trade names referenced in this repository or the application are the property of their respective owners. Their use here is **purely nominative and descriptive**; no endorsement, certification, partnership, sponsorship, or affiliation is implied or should be inferred. If you are a rights holder and want a specific reference clarified or removed, open an issue.

## Independent open-source project

Flowpay is an independent open-source project, provided as-is under the Apache License 2.0. It is not a regulated financial product or payment service, and the author operates no commercial service around it. Forks and derivative works are governed by the Apache 2.0 license.

## Limitation of liability

To the maximum extent permitted by applicable law, the author and contributors shall not be liable for any direct, indirect, incidental, special, consequential, or exemplary damages — including but not limited to loss of funds, transaction failures, data loss, telecom charges, regulatory penalties, or reputational harm — arising from or related to your use of, inability to use, or reliance on this software, even if advised of the possibility of such damages.

## Severability

If any portion of this disclaimer is held unenforceable by a court of competent jurisdiction, the remainder shall remain in full force and effect.

---

*By using, building, modifying, redistributing, or otherwise interacting with this software, you acknowledge that you have read, understood, and agreed to the above.*
