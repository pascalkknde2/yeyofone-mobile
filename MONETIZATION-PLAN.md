# YeyoFone — Making money with the app

Status: draft, 2026-10-10. Prices and numbers below are **starting assumptions to test**, not market research. Check current competitor pricing and your real costs before publishing anything. This feeds decision **DEC-06** in `PRODUCTION-TODO-PLAN.md` (countries, pricing model, support contact, emergency-calling limits).

## What we have to sell

YeyoFone is a SIP softphone for desktop (macOS first), Android and iOS. Today it offers:

- Calling on your own SIP accounts, with ring tones, ringback, hold, transfer, consult transfer and **merge calls** (three-way)
- Voicemail, call history, call recording with **cloud storage** (Cloudflare R2), a statistics dashboard
- Ring groups / call forwarding / audio conference screens (preview settings, not yet saved to a phone system)
- English, French, Spanish and German
- We also run our own FreeSWITCH phone system (sysinfos.co.uk)

`MAIN-IDEA.md` scopes the product as a **client-side softphone** (comparable to Zoiper), not a PBX/telecom platform. The plan below starts inside that scope; selling a hosted phone system is listed as a later option that would widen it.

## All the ways to earn

| # | Idea | How it earns | Fits the softphone scope? |
|---|------|--------------|---------------------------|
| 1 | **Free app + paid "Pro"** | Basic calling free; Pro unlocks recording + cloud storage, transfer/merge, several accounts, history sync, statistics | Yes |
| 2 | **White label for providers** | Internet phone providers (ITSPs) and IT support companies (MSPs) license the apps, branded as theirs, per user per month | Yes |
| 3 | **Paid add-ons (usage)** | Longer recording retention and compliance export; **transcription and AI call summaries**; voicemail-to-text/email | Yes |
| 4 | **Call-centre tier** | Queues view, live wallboards (from the statistics page), supervisor listen/whisper — needs phone-system support | Partly |
| 5 | **CRM integrations** | Click-to-call and automatic call logging for HubSpot, Salesforce, Zoho as a paid feature | Yes |
| 6 | **Services** | Onboarding, custom builds/branding, support contracts | Yes |
| 7 | **Numbers and minutes** | Resell phone numbers and call minutes with a margin via a SIP trunk provider (e.g. Telnyx, Twilio) | No — makes us a telecom provider |
| 8 | **Hosted business phone service** | Phone system + numbers + apps per user per month for small businesses | No — widens the scope to a PBX/telecom service |

## Before charging anyone

- **Emergency calls:** in the UK, a service that gives customers phone numbers generally has to support emergency calls (999/112), including location information. This mainly bites ideas 7 and 8. Get this checked properly.
- **Call recording:** needs the other party's consent; recordings are personal data under GDPR (storage location, retention, deletion, access requests).
- **App store rules:** Apple and Google take a share of subscriptions sold inside their apps, and Apple has specific rules about selling outside the App Store. Decide early whether Pro is bought in-app or sold to businesses on the web.
- **Readiness:** the Team room and video are previews — don't sell them until they work. Background incoming calls on iOS wait for a paid Apple developer account (PushKit plan, `IOS-PUSH-01..06`). Calling-feature settings (forwarding, ring groups) are not yet saved to a real phone system.

---

# One-page plan

## Goal
Reach the first paying customers within about 90 days by selling the softphone itself (no telecom licence needed), then grow recurring revenue per user.

## Who we sell to first
1. **Small businesses that already have a SIP phone system** and want better desktop + mobile apps (direct, Pro tier).
2. **Small UK internet phone providers and IT support companies** that need branded apps for their customers (white label).

## The offer (pricing hypotheses — validate first)

| Plan | Who | What's included | Starting price idea |
|------|-----|-----------------|---------------------|
| **Free** | Anyone | Calling on 1 SIP account, history, voicemail | £0 |
| **Pro** | Individuals / small teams | Several accounts, recording + cloud storage (e.g. 10 GB), transfer and merge, history sync, statistics | £4–6 per user / month |
| **Business** | Teams of 5+ | Pro + admin setup, CRM click-to-call, priority support | £8–12 per user / month |
| **White label** | Providers / IT firms | Their brand, their phone system, our apps; minimum volume | £2–4 per user / month (minimum e.g. 50 users) + one-off setup fee |
| **AI add-on** | Any paid plan | Transcription, call summaries, voicemail-to-text | Per minute (e.g. £0.02–0.05) or monthly bundles |

Discount for annual billing; free trial (e.g. 14 days) for Pro and Business.

## Costs to work out per user / month
`price − (cloud storage + AI minutes + payment/app-store fees + support time + push/notification + hosting) = margin`. Fill in real figures for: Cloudflare R2 storage and requests, AI transcription/summary cost per minute, Apple/Google/Stripe fees, support hours per customer.

## 90-day launch checklist

**Weeks 1–3 — make it sellable**
- [ ] Decide DEC-06: first countries (suggest UK), plans and prices, support contact
- [ ] Licensing/entitlements in the apps (Free vs Pro vs Business), and where people pay (web checkout vs in-app)
- [ ] Billing: Stripe (web) and/or App Store / Google Play subscriptions
- [ ] Terms of service, privacy policy, recording-consent notice, data-retention settings
- [ ] Finish the in-app checks in `yeyofone-desktop/TODO.md` (item 9); fix what they find
- [ ] iOS background incoming calls (paid Apple account + PushKit plan)

**Weeks 4–8 — pilot**
- [ ] 5–10 pilot customers (existing SIP users + 1–2 providers for white label), free or discounted in exchange for feedback
- [ ] Simple website: what it does, plans, download links, contact
- [ ] Onboarding guide: add a SIP account, recording, transfer/merge
- [ ] Measure: daily active users, calls per user, crash/failed-call rate, support requests

**Weeks 9–13 — launch and first add-on**
- [ ] Turn pilots into paying customers; publish prices
- [ ] Ship the AI add-on (transcription + summaries) as the first paid extra
- [ ] White-label kit: branding config, build pipeline per partner, contract template
- [ ] Monthly review of revenue, margin per user and churn

## How much it could earn per month

These are illustrations built from the price guesses above, not a forecast. The pilot phase gives the real numbers.

**Formula:** monthly revenue = paying users × average price per user per month. At an average of about £8 per user, £1,000 a month needs about 125 paying users; £5,000 needs about 625.

| Stage | Who pays | Monthly revenue (before costs) |
|-------|----------|--------------------------------|
| **First months (pilots)** | 5 small businesses × 8 users on Business (£10) = £400; 1 white-label partner with 50 users (£3) = £150 | **≈ £550** |
| **End of year 1** | 30 businesses × 8 users at £10 = £2,400; 150 Pro users at £5 = £750; 3 partners × 150 users at £3 = £1,350; AI add-on used by 1 in 5 paying users at ≈ £9 each = £700 | **≈ £5,000** |
| **Year 2–3, if it goes well** | ≈ 1,000 direct users averaging £9 = £9,000; 3,000 white-label users at £3 = £9,000; AI add-on ≈ £2,000 | **≈ £20,000** |

**What you keep is less.** Take off payment fees (a few percent through a card processor such as Stripe, or 15–30% when people pay inside the App Store or Google Play), the AI add-on's own per-minute costs (plan on keeping roughly half of that revenue), cloud storage, hosting and support time. A margin of very roughly 60–80% before paying yourself is a reasonable starting assumption for this kind of software — confirm it with the cost formula above.

**What decides which row you reach:**
- **Getting customers** — most growth comes from selling directly to businesses and to white-label partners; free users who start paying are often only a few percent.
- **White label** — one provider with hundreds of users is worth more than dozens of individual sign-ups.
- **Churn** — keeping customers matters as much as winning them; call quality on real networks is what keeps them.
- **Readiness** — iOS background calls, the in-app checks and billing must be finished before anyone can pay.

## What to measure
Paying users · monthly recurring revenue (MRR) · margin per user · trial → paid conversion · churn · call success rate · support tickets per 100 users.

## Main risks
- **App quality on real networks** (NAT, mobile data, background calls) — keep testing with real phones before charging.
- **App store rules** for subscriptions — decide the payment path early.
- **Compliance** for recording and, if ever offering numbers, emergency calls.
- **Competition** from free or bundled softphones — compete on desktop + mobile polish, recording/AI and white label.

## Later, if we widen the scope
Hosted business phone service (idea 8) and numbers/minutes (idea 7) can bring the most revenue per user, but they make us a phone provider: emergency-call duties, number regulation, porting and 24/7 support. Revisit only after the softphone business works.
