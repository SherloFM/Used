# Implementation review — 2026-10-28

## Verified in this change

`PaymentConcurrencyDbTest` ran against PostgreSQL: **5 tests, 4 passed, 1 failed**.
No Mockito repositories or authentication spy are used. A recording mail sender
replaces external SMTP; real notification, favorite, wallet and payment code runs.
Fixtures are uniquely named, committed before competing threads start, and removed
by fixture IDs after worker tasks finish. Existing marketplace records are not used
as purchase fixtures. Futures and database polling have deadlines.

Passed:
1. Competing payment submissions produce exactly one completed sale, preserve the
   losing buyer's balance, and credit the seller once. The database's
   `ux_payments_active_listing` index rejected the competing insert in this run.
2. One buyer racing to buy two listings cannot spend more than their balance.
3. Insufficient funds rolls back listing changes, favorite removal and persisted
   unavailable notifications; no email is sent on rollback.
4. The legacy purchase path creates only one transaction. The database's
   `transactions_listing_unique` constraint rejected the losing insert.

Re-run confirmed the same 4/1 result twice in a row.

Seed data note: listing 34 ("Canon EOS M50 Camera") had been mutated to DELISTED
in the real database by earlier manual/admin verification, which broke
`ReportServiceDbTest.delistingThroughReportResolutionUsesTheExistingListingService`
(seed-dependent test). The row was restored to its seed value `ACTIVE` with SQL,
after which all 18 `ReportServiceDbTest` + `FavoriteServiceDbTest` tests pass.
Because those suites rely on seed rows 3/5/6/31/34/35, avoid mutating seed
listings during manual browser testing (use newly created test listings instead).

Failed (now fixed, see below):
- `favoriteCommittedAfterSaleMustNotRemain`: a favorite inserted in an uncommitted
  transaction was invisible to sale cleanup; it could commit afterward and remain
  attached to a sold listing. The interleaving reproduced the existing defect.

## Production fixes (applied 2026-10-28, all tests green)

1. `FavoriteService.addFavorite` now loads the listing with `findByIdForUpdate`
   (row lock) and rejects non-ACTIVE listings inside that lock. Sale/delist and
   favorite creation both order listing row lock first, so add-vs-sale is atomic.
   A favorite can no longer survive a sale; the compression test was rewritten
   as a lock-free concurrent race (`favoriteRacingTheSaleNeverSurvives`) and passes.
2. `TransactionServices.purchaseListing` claims the listing atomically
   (`updateStatusIfCurrent ACTIVE -> SOLD`) instead of read-then-set. The losing
   buyer now gets a clean 400 instead of a raw constraint violation. Seller
   fields are captured before the claim because the claim clears the persistence
   context (same idiom as `ListingService.adminDeleteListing`).
3. `PaymentWorker.executePayment` claims the payment `PENDING -> PROCESSING`
   before any listing or wallet work and closes with conditional
   `markCompletedIfCurrent`, symmetric with the existing COD path. A repeated
   execution of the same payment can no longer double-spend.
4. `PaymentWorkerNotificationTest` was updated to stub the two new repository
   calls (the mock encoded the old flow). `UsedApplicationTests` was always
   failing on a full `mvnw test` run (no profile, no DataSource); it now runs
   with the `dev` profile and verifies the context actually loads.

Full suite after the fixes: 45 tests, 0 failures, 0 errors.
- Favorite pagination currently loads each listing separately (N+1 queries), and
  favorite card responses only expose imgUrl rather than binary listing images.
- After-commit email is best effort, not a durable retry queue.

## Frontend consistency pass

All 13 full-page templates now load `theme.css` after their page styles. Shared
tokens unify primary actions, semantic colors, typography, card borders/radii,
focus indicators, touch targets, responsive navigation and reduced motion.
Favorites use stars in the grid heading, empty state and navbar, matching the detail
toggle; the toggle exposes its pressed state and action label to assistive technology.
The home Browse link now scrolls to results; Sell navigates to listing creation for
authenticated users. Admin logout/marketplace links remain visible on narrow layouts.

Browser inspection covered the login and home page. It exposed navbar overflow at
800px, addressed by wrapping navigation/search at the 900px breakpoint. This is
not a claim that every authenticated workflow has been re-tested visually.

## Documentation

Updated the existing `API docs.MD`, `UserStories.MD`, and `README.MD`; did not create
a duplicate Readme.docs. API additions document favorites, moderation, asynchronous
payments, wallet, admin, chat and durable notifications. Removed the nonexistent
notification debug endpoint and corrected listing-list authentication/status scope.
No API resources were published externally.
